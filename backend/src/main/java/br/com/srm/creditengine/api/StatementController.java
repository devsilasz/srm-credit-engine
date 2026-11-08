package br.com.srm.creditengine.api;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.infrastructure.persistence.StatementQuery;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Relatório: atalha a camada de aplicação, como o enunciado permite, porque não há regra aqui. */
@RestController
@RequestMapping("/api/settlements")
public class StatementController {

  private static final int MAX_PAGE_SIZE = 100;

  private final StatementQuery statement;

  public StatementController(StatementQuery statement) {
    this.statement = statement;
  }

  @GetMapping
  public StatementPage list(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) Long assignorId,
      @RequestParam(required = false) PaymentCurrency currency,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "settledAt,desc") String sort) {
    if (from != null && to != null && from.isAfter(to)) {
      throw invalid("`from` não pode ser posterior a `to`.");
    }
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw invalid("`page` não pode ser negativo e `size` deve ficar entre 1 e 100.");
    }
    String[] sortParts = sort.split(",", -1);
    String direction = sortParts.length > 1 ? sortParts[1] : "desc";
    boolean validDirection = direction.equals("asc") || direction.equals("desc");
    if (sortParts.length > 2 || !StatementQuery.isSortable(sortParts[0]) || !validDirection) {
      throw invalid("`sort` aceita settledAt, faceValue ou presentValue, com asc ou desc.");
    }

    var result =
        statement.find(
            new StatementQuery.Filter(
                from, to, assignorId, currency, page, size, sortParts[0], direction.equals("asc")));
    int totalPages = (int) Math.ceilDiv(result.totalElements(), (long) size);
    return new StatementPage(result.items(), page, size, result.totalElements(), totalPages);
  }

  private static BusinessRuleException invalid(String detail) {
    return new BusinessRuleException(ErrorCode.INVALID_FILTER, detail);
  }

  public record StatementPage(
      List<StatementQuery.Item> content, int page, int size, long totalElements, int totalPages) {}
}
