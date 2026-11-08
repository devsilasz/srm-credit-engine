package br.com.srm.creditengine.domain.model;

/** Moeda de pagamento. O título é sempre em BRL; só o pagamento pode exigir câmbio. */
public enum PaymentCurrency {
  BRL,
  USD;

  public boolean requiresFx() {
    return this != BRL;
  }

  /** Par em que a taxa está cotada: BRL por 1 unidade desta moeda. */
  public String fxPair() {
    return name() + "-BRL";
  }
}
