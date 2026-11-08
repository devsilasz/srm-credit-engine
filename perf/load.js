// Carga: prova que o uso esperado da mesa de operações cabe com folga.
import { check } from 'k6';
import exec from 'k6/execution';
import http from 'k6/http';
import { BASE_URL, createReceivables, isoDatePlusDays, JSON_HEADERS, quoteBody, uuid } from './common.js';

const WARMUP = '30s';
const DURATION = '2m';
// 5 req/s por 30 s de aquecimento + 2 min de medição, com folga.
const RECEIVABLES = 900;
const WARMUP_SETTLEMENTS = 200;

function scenario(exec, rate, measured) {
  return {
    executor: 'constant-arrival-rate',
    exec,
    rate,
    timeUnit: '1s',
    duration: measured ? DURATION : WARMUP,
    startTime: measured ? WARMUP : '0s',
    preAllocatedVUs: 30,
    maxVUs: 100,
  };
}

export const options = {
  setupTimeout: '180s',
  // Sem esta lista, o summary não traz p(50) e p(99).
  summaryTrendStats: ['avg', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    warmup_quote: scenario('quote', 20, false),
    warmup_statement: scenario('statement', 10, false),
    warmup_settle: scenario('settleWarmup', 5, false),
    quote: scenario('quote', 20, true),
    statement: scenario('statement', 10, true),
    settle: scenario('settle', 5, true),
  },
  thresholds: {
    'http_req_duration{scenario:quote}': ['p(95)<150', 'p(99)<300'],
    'http_req_duration{scenario:statement}': ['p(95)<100'],
    'http_req_duration{scenario:settle}': ['p(95)<300'],
    'http_req_failed{scenario:quote}': ['rate==0'],
    'http_req_failed{scenario:statement}': ['rate==0'],
    'http_req_failed{scenario:settle}': ['rate==0'],
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

http.setResponseCallback(http.expectedStatuses(200, 201));

export function setup() {
  return createReceivables(RECEIVABLES);
}

export function quote() {
  const response = http.post(
    `${BASE_URL}/api/pricing/quote`,
    quoteBody(exec.scenario.iterationInTest),
    { headers: JSON_HEADERS, tags: { name: 'quote' } },
  );
  check(response, {
    'quote 200': (r) => r.status === 200,
    'quote com valor em string': (r) => r.status === 200 && typeof r.json('presentValue') === 'string',
  });
}

export function statement(data) {
  const iteration = exec.scenario.iterationInTest;
  const assignorId = data.assignors[iteration % data.assignors.length];
  const currency = iteration % 2 === 0 ? 'BRL' : 'USD';
  const from = isoDatePlusDays(-30);
  const to = isoDatePlusDays(0);
  const response = http.get(
    `${BASE_URL}/api/settlements?assignorId=${assignorId}&currency=${currency}&from=${from}&to=${to}&page=0&size=20`,
    { tags: { name: 'statement' } },
  );
  check(response, { 'extrato 200': (r) => r.status === 200 });
}

function settleReceivable(id) {
  const response = http.post(
    `${BASE_URL}/api/settlements`,
    JSON.stringify({ receivableId: id, paymentCurrency: id % 2 === 0 ? 'BRL' : 'USD' }),
    { headers: { ...JSON_HEADERS, 'Idempotency-Key': uuid() }, tags: { name: 'settle' } },
  );
  check(response, { 'liquidação 201': (r) => r.status === 201 });
}

export function settleWarmup(data) {
  settleReceivable(data.ids[exec.scenario.iterationInTest]);
}

export function settle(data) {
  settleReceivable(data.ids[WARMUP_SETTLEMENTS + exec.scenario.iterationInTest]);
}
