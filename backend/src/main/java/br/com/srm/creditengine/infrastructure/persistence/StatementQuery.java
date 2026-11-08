package br.com.srm.creditengine.infrastructure.persistence;

import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Extrato em SQL nativo. O WHERE é montado só com fragmentos constantes e o ORDER BY vem de uma
 * lista branca, então nada digitado pelo usuário entra no texto do SQL. Filtros ausentes saem da
 * query, em vez de virarem {@code :x IS NULL OR ...}, que impede o planner de usar os índices.
 */
@Repository
public class StatementQuery {

  private static final Map<String, String> SORT_COLUMNS =
      Map.of(
          "settledAt", "s.settled_at",
          "faceValue", "s.face_value",
          "presentValue", "s.present_value_brl");

  private static final String ITEMS_SQL =
      """
      SELECT s.id, s.receivable_id, s.assignor_id, a.name AS assignor_name, s.receivable_type,
             s.face_value, s.term_months, s.present_value_brl, s.discount, s.payment_currency,
             s.present_value_payment, s.fx_rate, s.settled_at
      FROM settlement s
      JOIN assignor a ON a.id = s.assignor_id
      %s
      ORDER BY %s %s, s.id %s
      LIMIT :limit OFFSET :offset
      """;

  private static final String COUNT_SQL = "SELECT count(*) FROM settlement s %s";

  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;

  public StatementQuery(NamedParameterJdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public record Filter(
      LocalDate from,
      LocalDate to,
      Long assignorId,
      PaymentCurrency currency,
      int page,
      int size,
      String sortField,
      boolean ascending) {}

  public record Item(
      Long id,
      Long receivableId,
      Long assignorId,
      String assignorName,
      ReceivableType type,
      BigDecimal faceValue,
      int termMonths,
      BigDecimal presentValueBrl,
      BigDecimal discount,
      PaymentCurrency paymentCurrency,
      BigDecimal presentValue,
      BigDecimal fxRate,
      Instant settledAt) {}

  public record Result(List<Item> items, long totalElements) {}

  public static boolean isSortable(String field) {
    return SORT_COLUMNS.containsKey(field);
  }

  public Result find(Filter filter) {
    MapSqlParameterSource parameters = parameters(filter);
    List<Item> items = jdbc.query(itemsSql(filter), parameters, StatementQuery::toItem);
    Long total = jdbc.queryForObject(countSql(filter), parameters, Long.class);
    return new Result(items, total);
  }

  String itemsSql(Filter filter) {
    String direction = filter.ascending() ? "ASC" : "DESC";
    return ITEMS_SQL.formatted(
        where(filter), SORT_COLUMNS.get(filter.sortField()), direction, direction);
  }

  String countSql(Filter filter) {
    return COUNT_SQL.formatted(where(filter));
  }

  private static String where(Filter filter) {
    List<String> conditions = new ArrayList<>();
    if (filter.from() != null) {
      conditions.add("s.settled_at >= :from");
    }
    if (filter.to() != null) {
      conditions.add("s.settled_at < :toExclusive");
    }
    if (filter.assignorId() != null) {
      conditions.add("s.assignor_id = :assignorId");
    }
    if (filter.currency() != null) {
      conditions.add("s.payment_currency = :currency");
    }
    return conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
  }

  /** As datas são dias inteiros no fuso do negócio; o banco guarda instantes em UTC. */
  MapSqlParameterSource parameters(Filter filter) {
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("limit", filter.size())
            .addValue("offset", (long) filter.page() * filter.size());
    if (filter.from() != null) {
      parameters.addValue("from", startOfDay(filter.from()));
    }
    if (filter.to() != null) {
      parameters.addValue("toExclusive", startOfDay(filter.to().plusDays(1)));
    }
    if (filter.assignorId() != null) {
      parameters.addValue("assignorId", filter.assignorId());
    }
    if (filter.currency() != null) {
      parameters.addValue("currency", filter.currency().name());
    }
    return parameters;
  }

  private OffsetDateTime startOfDay(LocalDate date) {
    return date.atStartOfDay(clock.getZone()).toOffsetDateTime();
  }

  private static Item toItem(ResultSet row, int rowNumber) throws SQLException {
    return new Item(
        row.getLong("id"),
        row.getLong("receivable_id"),
        row.getLong("assignor_id"),
        row.getString("assignor_name"),
        ReceivableType.valueOf(row.getString("receivable_type")),
        row.getBigDecimal("face_value"),
        row.getInt("term_months"),
        row.getBigDecimal("present_value_brl"),
        row.getBigDecimal("discount"),
        PaymentCurrency.valueOf(row.getString("payment_currency")),
        row.getBigDecimal("present_value_payment"),
        row.getBigDecimal("fx_rate"),
        row.getObject("settled_at", OffsetDateTime.class).toInstant());
  }
}
