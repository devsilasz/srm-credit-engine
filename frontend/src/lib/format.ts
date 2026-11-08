import Decimal from 'decimal.js';
import type { PaymentCurrency, ReceivableType } from './api';

export const TYPE_LABELS: Record<ReceivableType, string> = {
  DUPLICATA_MERCANTIL: 'Duplicata mercantil',
  CHEQUE_PRE_DATADO: 'Cheque pré-datado',
};

const TIME_ZONE = 'America/Sao_Paulo';

// Intl recebe a string decimal diretamente, então nada passa por float binário.
const asLiteral = (value: string) => value as Intl.StringNumericLiteral;

export function money(value: string, currency: PaymentCurrency = 'BRL'): string {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(asLiteral(value));
}

/** Fração para porcentagem: `0.015` vira `1,50%`. */
export function percent(fraction: string): string {
  return `${new Intl.NumberFormat('pt-BR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(asLiteral(new Decimal(fraction).times(100).toString()))}%`;
}

export function fxRate(value: string): string {
  return new Intl.NumberFormat('pt-BR', {
    minimumFractionDigits: 4,
    maximumFractionDigits: 8,
  }).format(asLiteral(value));
}

export function dateTime(instant: string): string {
  return new Intl.DateTimeFormat('pt-BR', {
    timeZone: TIME_ZONE,
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(instant));
}

export function months(term: number): string {
  return term === 1 ? '1 mês' : `${term} meses`;
}

/** Hoje no fuso do negócio, como `AAAA-MM-DD`, comparável como texto. */
export function todayInBusinessZone(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE }).format(new Date());
}

/** Aceita `100000,00`, `100.000,00` e `100000.00`. Devolve o decimal com ponto. */
export function normalizeDecimal(input: string): string {
  const trimmed = input.trim();
  return trimmed.includes(',') ? trimmed.replaceAll('.', '').replace(',', '.') : trimmed;
}
