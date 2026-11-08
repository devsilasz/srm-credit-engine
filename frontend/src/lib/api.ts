/** Contrato com o backend. Dinheiro e taxa trafegam como string e nunca viram `number` aqui. */
export type ReceivableType = 'DUPLICATA_MERCANTIL' | 'CHEQUE_PRE_DATADO';
export type PaymentCurrency = 'BRL' | 'USD';

export interface Assignor {
  id: number;
  name: string;
}

export interface QuoteInput {
  faceValue: string;
  dueDate: string;
  type: ReceivableType;
  paymentCurrency: PaymentCurrency;
}

export interface Quote {
  faceValue: string;
  termMonths: number;
  baseRate: string;
  spread: string;
  presentValueBrl: string;
  discount: string;
  paymentCurrency: PaymentCurrency;
  presentValue: string;
  fxRate?: string;
  fxRateValidFrom?: string;
}

export interface Settlement {
  id: number;
  paymentCurrency: PaymentCurrency;
  presentValue: string;
}

export interface StatementItem {
  id: number;
  assignorName: string;
  type: ReceivableType;
  faceValue: string;
  termMonths: number;
  presentValueBrl: string;
  discount: string;
  paymentCurrency: PaymentCurrency;
  presentValue: string;
  fxRate?: string;
  settledAt: string;
}

export interface StatementPage {
  content: StatementItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Erro já pronto para o operador ler: o `detail` do ProblemDetail ou um texto equivalente. */
export class ApiError extends Error {}

const BASE_URL: string = import.meta.env.VITE_API_URL ?? '';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(BASE_URL + path, init);
  } catch {
    throw new ApiError('Sem resposta do servidor. Confira a conexão e tente de novo.');
  }
  if (response.ok) {
    return response.json() as Promise<T>;
  }
  const problem = await response.json().catch(() => null);
  throw new ApiError(
    problem?.detail ?? `O servidor recusou o pedido (status ${response.status}). Tente de novo.`,
  );
}

function postJson<T>(path: string, body: unknown, headers: HeadersInit = {}): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  });
}

export const api = {
  assignors: () => request<Assignor[]>('/api/assignors'),
  quote: (input: QuoteInput) => postJson<Quote>('/api/pricing/quote', input),
  createReceivable: (input: Omit<QuoteInput, 'paymentCurrency'> & { assignorId: number }) =>
    postJson<{ id: number }>('/api/receivables', input),
  settle: (receivableId: number, paymentCurrency: PaymentCurrency, idempotencyKey: string) =>
    postJson<Settlement>(
      '/api/settlements',
      { receivableId, paymentCurrency },
      { 'Idempotency-Key': idempotencyKey },
    ),
  statement: (query: string) => request<StatementPage>(`/api/settlements?${query}`),
};
