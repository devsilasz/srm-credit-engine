package br.com.srm.creditengine.domain;

public class BusinessRuleException extends RuntimeException {

  private final ErrorCode code;

  public BusinessRuleException(ErrorCode code, String detail) {
    super(detail);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
