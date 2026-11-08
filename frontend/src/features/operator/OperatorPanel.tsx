import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useForm, useWatch } from 'react-hook-form';
import { api, type QuoteInput } from '../../lib/api';
import { money, TYPE_LABELS } from '../../lib/format';
import { useDebouncedValue } from '../../lib/useDebouncedValue';
import { QuoteSheet } from './QuoteSheet';
import { quoteSchema, settleSchema, type SettleFormInput, type SettleValues } from './schema';
import { useSettlementIntent } from './useSettlementIntent';

const EMPTY_FORM: SettleFormInput = {
  assignorId: '',
  faceValue: '',
  dueDate: '',
  type: 'DUPLICATA_MERCANTIL',
  paymentCurrency: 'BRL',
};

export function OperatorPanel() {
  const {
    register,
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<SettleFormInput, unknown, SettleValues>({
    resolver: zodResolver(settleSchema),
    mode: 'onChange',
    defaultValues: EMPTY_FORM,
  });
  const values = useWatch({ control }) as SettleFormInput;

  const parsedQuote = quoteSchema.safeParse(values);
  const parsedSettle = settleSchema.safeParse(values);
  const currentInput = parsedQuote.success ? JSON.stringify(parsedQuote.data) : null;
  const debouncedInput = useDebouncedValue(currentInput, 300);

  const quote = useQuery({
    queryKey: ['quote', debouncedInput],
    queryFn: () => api.quote(JSON.parse(debouncedInput!) as QuoteInput),
    enabled: debouncedInput !== null,
  });
  const assignors = useQuery({ queryKey: ['assignors'], queryFn: api.assignors });
  const settlement = useSettlementIntent();

  const isSimulationCurrent = currentInput !== null && currentInput === debouncedInput;
  const attempt = parsedSettle.success ? JSON.stringify(parsedSettle.data) : null;
  const attemptMatchesForm =
    settlement.variables !== undefined && JSON.stringify(settlement.variables) === attempt;
  const settledThisForm = settlement.isSuccess && attemptMatchesForm;

  // O operador só liquida o que está vendo na simulação. Depois do sucesso, o botão fica bloqueado
  // até o formulário mudar: um clique tardio criaria outro recebível, e não um replay.
  const canSettle =
    parsedSettle.success &&
    isSimulationCurrent &&
    quote.isSuccess &&
    !settlement.isPending &&
    !settledThisForm;

  const startNewOperation = () => {
    settlement.reset();
    reset(EMPTY_FORM);
  };

  return (
    <section className="desk" aria-label="Simular e liquidar um recebível">
      <form
        className="desk-form"
        noValidate
        onSubmit={handleSubmit((data) => {
          if (canSettle) settlement.mutate(data);
        })}
      >
        <h2>Recebível</h2>

        <div className="field">
          <label htmlFor="assignorId">Cedente</label>
          <select id="assignorId" {...register('assignorId')}>
            <option value="">Escolha o cedente</option>
            {assignors.data?.map((assignor) => (
              <option key={assignor.id} value={assignor.id}>
                {assignor.name}
              </option>
            ))}
          </select>
          {errors.assignorId && <p className="field-error">{errors.assignorId.message}</p>}
          {assignors.error && <p className="field-error">{assignors.error.message}</p>}
        </div>

        <div className="field">
          <label htmlFor="faceValue">Valor de face, em reais</label>
          <input
            id="faceValue"
            inputMode="decimal"
            autoComplete="off"
            placeholder="100.000,00"
            aria-invalid={errors.faceValue !== undefined}
            {...register('faceValue')}
          />
          {errors.faceValue && <p className="field-error">{errors.faceValue.message}</p>}
        </div>

        <div className="field">
          <label htmlFor="dueDate">Vencimento</label>
          <input
            id="dueDate"
            type="date"
            aria-invalid={errors.dueDate !== undefined}
            {...register('dueDate')}
          />
          {errors.dueDate && <p className="field-error">{errors.dueDate.message}</p>}
        </div>

        <div className="field">
          <label htmlFor="type">Tipo</label>
          <select id="type" {...register('type')}>
            {Object.entries(TYPE_LABELS).map(([type, label]) => (
              <option key={type} value={type}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <fieldset className="field currency">
          <legend>Moeda de pagamento</legend>
          <label>
            <input type="radio" value="BRL" {...register('paymentCurrency')} /> Real
          </label>
          <label>
            <input type="radio" value="USD" {...register('paymentCurrency')} /> Dólar
          </label>
        </fieldset>

        <button type="submit" className="settle" disabled={!canSettle}>
          {settlement.isPending ? 'Liquidando…' : 'Liquidar'}
        </button>
      </form>

      <div className="paper" aria-live="polite" aria-busy={quote.isFetching || !isSimulationCurrent}>
        {currentInput === null && (
          <p className="paper-hint">
            Preencha o valor de face e o vencimento para ver o valor presente.
          </p>
        )}
        {currentInput !== null && quote.error && isSimulationCurrent && (
          <p className="paper-error" role="alert">
            {quote.error.message}
          </p>
        )}
        {currentInput !== null && quote.data && !quote.error && (
          <div className={isSimulationCurrent ? undefined : 'stale'}>
            <QuoteSheet quote={quote.data} />
          </div>
        )}
        {settlement.isError && attemptMatchesForm && (
          <p className="paper-error" role="alert">
            {settlement.error.message} Clique em Liquidar de novo: a tentativa não cria outro
            recebível.
          </p>
        )}
        {settledThisForm && settlement.data && (
          <div className="stamp-row" role="status">
            <p className="stamp" data-testid="settled">
              Liquidada nº {settlement.data.id}
              <small>
                {money(settlement.data.presentValue, settlement.data.paymentCurrency)}
              </small>
            </p>
            <button type="button" className="link" onClick={startNewOperation}>
              Nova operação
            </button>
          </div>
        )}
      </div>
    </section>
  );
}
