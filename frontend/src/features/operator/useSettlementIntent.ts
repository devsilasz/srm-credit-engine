import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { api, type Settlement } from '../../lib/api';
import type { SettleValues } from './schema';

interface Intent {
  fingerprint: string;
  idempotencyKey: string;
  receivableId?: number;
}

/**
 * Liquidar são duas chamadas: criar o recebível e liquidá-lo. A intenção guarda o id e a chave, para
 * que um novo clique depois de uma falha não crie outro recebível nem outra liquidação. Ela só é
 * descartada no sucesso ou quando o formulário muda (a impressão digital deixa de bater).
 */
export function useSettlementIntent() {
  const intent = useRef<Intent | null>(null);
  const queryClient = useQueryClient();

  return useMutation<Settlement, Error, SettleValues>({
    mutationFn: async (values) => {
      const fingerprint = JSON.stringify(values);
      if (intent.current?.fingerprint !== fingerprint) {
        intent.current = { fingerprint, idempotencyKey: crypto.randomUUID() };
      }
      const current = intent.current;
      if (current.receivableId === undefined) {
        const created = await api.createReceivable({
          assignorId: Number(values.assignorId),
          type: values.type,
          faceValue: values.faceValue,
          dueDate: values.dueDate,
        });
        current.receivableId = created.id;
      }
      const settlement = await api.settle(
        current.receivableId,
        values.paymentCurrency,
        current.idempotencyKey,
      );
      intent.current = null;
      return settlement;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['statement'] }),
  });
}
