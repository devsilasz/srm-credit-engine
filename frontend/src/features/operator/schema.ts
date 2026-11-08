import Decimal from 'decimal.js';
import { z } from 'zod';
import { normalizeDecimal, todayInBusinessZone } from '../../lib/format';

const faceValue = z
  .string()
  .transform(normalizeDecimal)
  .superRefine((value, context) => {
    const refuse = (message: string) =>
      context.addIssue({ code: z.ZodIssueCode.custom, message });
    if (value === '') {
      refuse('Informe o valor de face.');
    } else if (!/^\d{1,17}(\.\d{1,2})?$/.test(value)) {
      refuse('Use um valor com até 2 casas decimais.');
    } else if (new Decimal(value).isZero()) {
      refuse('O valor precisa ser maior que zero.');
    }
  });

const dueDate = z
  .string()
  .min(1, 'Informe o vencimento.')
  .refine((value) => value > todayInBusinessZone(), 'O vencimento precisa ser depois de hoje.');

export const quoteSchema = z.object({
  faceValue,
  dueDate,
  type: z.enum(['DUPLICATA_MERCANTIL', 'CHEQUE_PRE_DATADO']),
  paymentCurrency: z.enum(['BRL', 'USD']),
});

export const settleSchema = quoteSchema.extend({
  assignorId: z.string().min(1, 'Escolha o cedente.'),
});

export type SettleFormInput = z.input<typeof settleSchema>;
export type SettleValues = z.output<typeof settleSchema>;
