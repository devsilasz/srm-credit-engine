// Estresse: prova que o sistema falha limpo sob pressão e registra até onde ele aguentou.
// Não há threshold de latência, só de integridade.
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';
import http from 'k6/http';
import { BASE_URL, createReceivables, JSON_HEADERS, quoteBody, uuid } from './common.js';

const HOSTILE_RATE = 5;
const STEP = '55s';
const RAMP = '5s';
const HOSTILE_DURATION = '5m';
const RECEIVABLES = 1600;

const statusCounters = {
  200: new Counter('hostile_200'),
  201: new Counter('hostile_201'),
  409: new Counter('hostile_409'),
  500: new Counter('hostile_500'),
  503: new Counter('hostile_503'),
};
const unexpectedStatus = new Counter('hostile_unexpected_status');
const doubleSuccess = new Counter('hostile_double_success');

export const options = {
  setupTimeout: '300s',
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    quote: {
      executor: 'ramping-arrival-rate',
      exec: 'quote',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
      // Degraus de 1 min: 5 s para subir e 55 s no patamar.
      stages: [
        { target: 50, duration: '1m' },
        { target: 100, duration: RAMP },
        { target: 100, duration: STEP },
        { target: 200, duration: RAMP },
        { target: 200, duration: STEP },
        { target: 400, duration: RAMP },
        { target: 400, duration: STEP },
        { target: 800, duration: RAMP },
        { target: 800, duration: STEP },
      ],
    },
    hostile: {
      executor: 'constant-arrival-rate',
      exec: 'hostile',
      rate: HOSTILE_RATE,
      timeUnit: '1s',
      duration: HOSTILE_DURATION,
      preAllocatedVUs: 20,
      maxVUs: 200,
    },
  },
  thresholds: {
    // AC-PERF-05: o único jeito de reprovar o estresse é um status indevido ou um pagamento duplo.
    hostile_unexpected_status: ['count==0'],
    hostile_double_success: ['count==0'],
  },
};

export function setup() {
  return createReceivables(RECEIVABLES);
}

export function quote() {
  http.post(`${BASE_URL}/api/pricing/quote`, quoteBody(exec.scenario.iterationInTest), {
    headers: JSON_HEADERS,
    tags: { name: 'quote' },
  });
}

/** Por recebível: 3 requisições com a mesma chave e 1 com outra chave, todas ao mesmo tempo. */
export function hostile(data) {
  const id = data.ids[exec.scenario.iterationInTest];
  if (id === undefined) {
    return;
  }
  const sameKey = uuid();
  const body = JSON.stringify({ receivableId: id, paymentCurrency: 'BRL' });
  const request = (key) => ({
    method: 'POST',
    url: `${BASE_URL}/api/settlements`,
    body,
    params: {
      headers: { ...JSON_HEADERS, 'Idempotency-Key': key },
      tags: { name: 'hostile' },
      responseCallback: http.expectedStatuses(200, 201, 409, 500, 503),
    },
  });

  const responses = http.batch([
    request(sameKey),
    request(sameKey),
    request(sameKey),
    request(uuid()),
  ]);

  responses.forEach((response) => {
    const counter = statusCounters[response.status];
    if (counter) {
      counter.add(1);
    } else {
      unexpectedStatus.add(1);
    }
  });

  // Mais de um 201, ou as duas chaves com sucesso, seria o recebível pago duas vezes.
  const created = responses.filter((r) => r.status === 201).length;
  const sameKeySucceeded = responses.slice(0, 3).some((r) => r.status === 200 || r.status === 201);
  const otherKeySucceeded = responses[3].status === 200 || responses[3].status === 201;
  if (created > 1 || (sameKeySucceeded && otherKeySucceeded)) {
    doubleSuccess.add(1);
  }
  check(responses, { 'no máximo uma liquidação criada': () => created <= 1 });
}
