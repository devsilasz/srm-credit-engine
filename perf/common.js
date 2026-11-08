import crypto from 'k6/crypto';
import http from 'k6/http';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const JSON_HEADERS = { 'Content-Type': 'application/json' };

/** UUID v4 sem jslib remota: o teste não depende de baixar nada em tempo de execução. */
export function uuid() {
  const bytes = new Uint8Array(crypto.randomBytes(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Data daqui a N dias, longe o bastante para o vencimento não ser recusado durante o teste. */
export function isoDatePlusDays(days) {
  return new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);
}

export function quoteBody(iteration) {
  return JSON.stringify({
    faceValue: '100000.00',
    dueDate: isoDatePlusDays(92),
    type: 'DUPLICATA_MERCANTIL',
    paymentCurrency: iteration % 2 === 0 ? 'BRL' : 'USD',
  });
}

export function createReceivables(count) {
  const assignors = JSON.parse(http.get(`${BASE_URL}/api/assignors`).body);
  const ids = [];
  for (let i = 0; i < count; i++) {
    const response = http.post(
      `${BASE_URL}/api/receivables`,
      JSON.stringify({
        assignorId: assignors[i % assignors.length].id,
        type: 'DUPLICATA_MERCANTIL',
        faceValue: '100000.00',
        dueDate: isoDatePlusDays(92),
      }),
      { headers: JSON_HEADERS, tags: { name: 'setup' } },
    );
    if (response.status !== 201) {
      throw new Error(`setup: recebível não criado (status ${response.status})`);
    }
    ids.push(JSON.parse(response.body).id);
  }
  return { ids, assignors: assignors.map((a) => a.id) };
}
