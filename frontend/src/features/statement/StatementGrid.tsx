import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { dateTime, fxRate, money, months, TYPE_LABELS } from '../../lib/format';
import { useStatementFilters } from './useStatementFilters';

const PAGE_SIZE = '20';

export function StatementGrid() {
  const { params, update } = useStatementFilters();
  const page = params.get('page') ?? '0';

  const query = new URLSearchParams(params);
  query.set('page', page);
  query.set('size', PAGE_SIZE);

  const statement = useQuery({
    queryKey: ['statement', query.toString()],
    queryFn: () => api.statement(query.toString()),
    placeholderData: keepPreviousData,
  });
  const assignors = useQuery({ queryKey: ['assignors'], queryFn: api.assignors });
  const data = statement.data;
  const pageNumber = data?.page ?? 0;

  return (
    <section className="statement" aria-labelledby="statement-title">
      <h2 id="statement-title">Liquidações</h2>

      <form className="filters" onSubmit={(event) => event.preventDefault()}>
        <div className="field">
          <label htmlFor="from">De</label>
          <input
            id="from"
            type="date"
            value={params.get('from') ?? ''}
            onChange={(event) => update({ from: event.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="to">Até</label>
          <input
            id="to"
            type="date"
            value={params.get('to') ?? ''}
            onChange={(event) => update({ to: event.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="filter-assignor">Cedente</label>
          <select
            id="filter-assignor"
            value={params.get('assignorId') ?? ''}
            onChange={(event) => update({ assignorId: event.target.value })}
          >
            <option value="">Todos</option>
            {assignors.data?.map((assignor) => (
              <option key={assignor.id} value={assignor.id}>
                {assignor.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="filter-currency">Moeda</label>
          <select
            id="filter-currency"
            value={params.get('currency') ?? ''}
            onChange={(event) => update({ currency: event.target.value })}
          >
            <option value="">Todas</option>
            <option value="BRL">Real</option>
            <option value="USD">Dólar</option>
          </select>
        </div>
      </form>

      {statement.error && (
        <p className="paper-error" role="alert">
          {statement.error.message}
        </p>
      )}

      {data && data.content.length === 0 && !statement.error && (
        <p className="empty">Nenhuma liquidação com estes filtros. Mude o período ou o cedente.</p>
      )}

      {data && data.content.length > 0 && !statement.error && (
        <>
          <div className="table-scroll">
            <table data-testid="statement">
              <thead>
                <tr>
                  <th scope="col">Nº</th>
                  <th scope="col">Liquidada em</th>
                  <th scope="col">Cedente</th>
                  <th scope="col">Tipo</th>
                  <th scope="col" className="num">
                    Prazo
                  </th>
                  <th scope="col" className="num">
                    Face
                  </th>
                  <th scope="col" className="num">
                    Deságio
                  </th>
                  <th scope="col" className="num">
                    Valor presente
                  </th>
                  <th scope="col" className="num">
                    Pago
                  </th>
                  <th scope="col" className="num">
                    Câmbio
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((item) => (
                  <tr key={item.id}>
                    <td>{item.id}</td>
                    <td>{dateTime(item.settledAt)}</td>
                    <td>{item.assignorName}</td>
                    <td>{TYPE_LABELS[item.type]}</td>
                    <td className="num">{months(item.termMonths)}</td>
                    <td className="num">{money(item.faceValue)}</td>
                    <td className="num">{money(item.discount)}</td>
                    <td className="num">{money(item.presentValueBrl)}</td>
                    <td className="num strong">{money(item.presentValue, item.paymentCurrency)}</td>
                    <td className="num">{item.fxRate ? fxRate(item.fxRate) : ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <nav className="pager" aria-label="Páginas do extrato">
            <button
              type="button"
              disabled={pageNumber === 0}
              onClick={() => update({ page: String(pageNumber - 1) })}
            >
              Página anterior
            </button>
            <span data-testid="page-status">
              Página {pageNumber + 1} de {data.totalPages}, {data.totalElements} liquidações
            </span>
            <button
              type="button"
              disabled={pageNumber + 1 >= data.totalPages}
              onClick={() => update({ page: String(pageNumber + 1) })}
            >
              Próxima página
            </button>
          </nav>
        </>
      )}
    </section>
  );
}
