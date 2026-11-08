import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { api, type Settlement } from '../../lib/api';
import type { SettleValues } from './schema';

interface Intent {
  fingerprint: string;
  idempotencyKey: string;
  receivableId?: number;
}

/** `randomUUID` só existe em HTTPS ou localhost; `getRandomValues` existe em qualquer origem. */
function newIdempotencyKey(): string {
  if (crypto.randomUUID) {
    return crypto.randomUUID();
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // versão 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variante RFC 4122
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20),
  ].join('-');
}

/**
 * Liquidar são duas chamadas: criar o recebível e liquidá-lo. A intenção guarda o id e a chave, para
 * que um novo clique depois de uma falha não crie outro recebível nem outra liquidação. Ela só é
 * descartada no sucesso ou quando o formulário muda (a impressão digital deixa de bater).
 */
export function useSettlementIntent() {
  const intent = useRef<Intent | null>(null);
  const inFlight = useRef(false);
  const queryClient = useQueryClient();

  const mutation = useMutation<Settlement, Error, SettleValues>({
    mutationFn: async (values) => {
      const fingerprint = JSON.stringify(values);
      if (intent.current?.fingerprint !== fingerprint) {
        intent.current = { fingerprint, idempotencyKey: newIdempotencyKey() };
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
    onSettled: () => {
      inFlight.current = false;
    },
  });

  // O `disabled` do botão só vale depois do re-render. Dois cliques antes disso criariam dois
  // recebíveis com a mesma chave: esta trava é síncrona.
  const settle = (values: SettleValues) => {
    if (!inFlight.current) {
      inFlight.current = true;
      mutation.mutate(values);
    }
  };

  return { ...mutation, settle };
}
