import Decimal from 'decimal.js';
import type { Quote } from '../../lib/api';
import { dateTime, fxRate, money, months, percent } from '../../lib/format';

/** O borderô: a conta do desconto em coluna, do valor de face até o que será pago. */
export function QuoteSheet({ quote }: { quote: Quote }) {
  const totalRate = new Decimal(quote.baseRate).plus(quote.spread).toString();
  const isForeign = quote.paymentCurrency !== 'BRL';

  return (
    <dl className="sheet" data-testid="quote">
      <div className="sheet-row">
        <dt>Valor de face</dt>
        <dd>{money(quote.faceValue)}</dd>
      </div>
      <div className="sheet-row">
        <dt>
          Deságio
          <small>
            {months(quote.termMonths)} a {percent(totalRate)} a.m. (base {percent(quote.baseRate)} +
            spread {percent(quote.spread)})
          </small>
        </dt>
        <dd data-testid="discount">− {money(quote.discount)}</dd>
      </div>
      <div className="sheet-row sheet-total">
        <dt>{isForeign ? 'Valor presente em reais' : 'Valor presente'}</dt>
        <dd data-testid={isForeign ? 'present-value-brl' : 'present-value'}>
          {money(quote.presentValueBrl)}
        </dd>
      </div>
      {isForeign && quote.fxRate && quote.fxRateValidFrom && (
        <>
          <div className="sheet-row">
            <dt>
              Câmbio
              <small>vigente desde {dateTime(quote.fxRateValidFrom)}</small>
            </dt>
            <dd>÷ {fxRate(quote.fxRate)}</dd>
          </div>
          <div className="sheet-row sheet-total">
            <dt>A pagar em dólar</dt>
            <dd data-testid="present-value">{money(quote.presentValue, quote.paymentCurrency)}</dd>
          </div>
        </>
      )}
    </dl>
  );
}
