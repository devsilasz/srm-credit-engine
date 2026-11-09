package br.com.srm.creditengine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class StatementQueryTest {

  private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final StatementQuery query =
      new StatementQuery(jdbc, Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), SAO_PAULO));

  private static StatementQuery.Filter noFilters() {
    return new StatementQuery.Filter(null, null, null, null, 0, 20, "settledAt", false);
  }

  private static StatementQuery.Filter allFilters() {
    return new StatementQuery.Filter(
        LocalDate.parse("2026-03-10"),
        LocalDate.parse("2026-03-11"),
        5L,
        PaymentCurrency.USD,
        2,
        10,
        "faceValue",
        true);
  }

  @Test
  @DisplayName("Filtro ausente sai da query: `:x IS NULL OR` impediria o uso dos índices")
  void absentFiltersLeaveTheQuery() {
    assertThat(query.itemsSql(noFilters())).doesNotContain("WHERE").doesNotContain("IS NULL");
    assertThat(query.countSql(noFilters())).doesNotContain("WHERE");
    assertThat(query.parameters(noFilters()).getParameterNames())
        .containsExactlyInAnyOrder("limit", "offset");
  }

  @Test
  @DisplayName("Valores do usuário entram só como parâmetro, nunca no texto do SQL")
  void userValuesNeverReachTheSqlText() {
    String sql = query.itemsSql(allFilters());

    assertThat(sql)
        .contains(":from", ":toExclusive", ":assignorId", ":currency")
        .doesNotContain("USD")
        .doesNotContain("2026-03");
    assertThat(query.countSql(allFilters())).contains(":assignorId").doesNotContain("USD");
  }

  @Test
  @DisplayName("O período cobre os dois dias inteiros em São Paulo, com o fim exclusivo")
  void periodUsesBusinessTimezoneAndExclusiveEnd() {
    MapSqlParameterSource parameters = query.parameters(allFilters());

    assertThat(parameters.getValue("from"))
        .isEqualTo(LocalDate.parse("2026-03-10").atStartOfDay(SAO_PAULO).toOffsetDateTime());
    assertThat(parameters.getValue("toExclusive"))
        .isEqualTo(LocalDate.parse("2026-03-12").atStartOfDay(SAO_PAULO).toOffsetDateTime());
    assertThat(parameters.getValue("assignorId")).isEqualTo(5L);
    assertThat(parameters.getValue("currency")).isEqualTo("USD");
    assertThat(parameters.getValue("limit")).isEqualTo(10);
    assertThat(parameters.getValue("offset")).isEqualTo(20L);
  }

  @Test
  void orderComesFromTheWhitelistWithIdAsTieBreaker() {
    assertThat(query.itemsSql(noFilters())).contains("ORDER BY s.settled_at DESC, s.id DESC");
    assertThat(query.itemsSql(allFilters())).contains("ORDER BY s.face_value ASC, s.id ASC");
    assertThat(StatementQuery.isSortable("presentValue")).isTrue();
    assertThat(StatementQuery.isSortable("id")).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void mapsRowsAndTotal() throws Exception {
    ResultSet row = mock(ResultSet.class);
    when(row.getLong("id")).thenReturn(1L);
    when(row.getLong("receivable_id")).thenReturn(2L);
    when(row.getLong("assignor_id")).thenReturn(3L);
    when(row.getString("assignor_name")).thenReturn("Cedente");
    when(row.getString("receivable_type")).thenReturn("CHEQUE_PRE_DATADO");
    when(row.getBigDecimal("face_value")).thenReturn(new BigDecimal("25000.00"));
    when(row.getInt("term_months")).thenReturn(2);
    when(row.getBigDecimal("present_value_brl")).thenReturn(new BigDecimal("23337.77"));
    when(row.getBigDecimal("discount")).thenReturn(new BigDecimal("1662.23"));
    when(row.getString("payment_currency")).thenReturn("USD");
    when(row.getBigDecimal("present_value_payment")).thenReturn(new BigDecimal("4296.27"));
    when(row.getBigDecimal("fx_rate")).thenReturn(new BigDecimal("5.43210000"));
    when(row.getObject("settled_at", OffsetDateTime.class))
        .thenReturn(OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC));
    when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
        .thenAnswer(call -> List.of(call.getArgument(2, RowMapper.class).mapRow(row, 0)));
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(30L);

    StatementQuery.Result result = query.find(noFilters());

    assertThat(result.totalElements()).isEqualTo(30);
    StatementQuery.Item item = result.items().get(0);
    assertThat(item.type()).isEqualTo(ReceivableType.CHEQUE_PRE_DATADO);
    assertThat(item.paymentCurrency()).isEqualTo(PaymentCurrency.USD);
    assertThat(item.presentValue()).isEqualByComparingTo("4296.27");
    assertThat(item.settledAt()).isEqualTo(Instant.parse("2026-01-15T12:00:00Z"));
  }
}
