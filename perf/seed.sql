-- 10.000 liquidações para medir o extrato com volume, não com a tabela vazia.
-- Espalhadas em 12 meses e entre os cedentes, com present_value_brl + discount = face_value.
BEGIN;

-- Taxa antiga só para atender a FK das liquidações em USD. Não muda a taxa vigente.
WITH seed_rate AS (
    INSERT INTO fx_rate (pair, rate, valid_from, source, created_at)
    VALUES ('USD-BRL', 5.4321, now() - interval '400 days', 'MANUAL', now() - interval '400 days')
    RETURNING id, rate
),
base AS (
    SELECT g,
           a.id AS assignor_id,
           round((1000 + random() * 200000)::numeric, 2) AS face_value,
           now() - random() * interval '365 days' AS settled_at
    FROM generate_series(1, 10000) g
    JOIN LATERAL (
        SELECT id FROM assignor ORDER BY id OFFSET (g % (SELECT count(*) FROM assignor)) LIMIT 1
    ) a ON true
),
new_receivable AS (
    INSERT INTO receivable (assignor_id, type, face_value, due_date, status, version, created_at)
    SELECT assignor_id, 'DUPLICATA_MERCANTIL', face_value, (settled_at + interval '3 months')::date,
           'SETTLED', 1, settled_at
    FROM base
    RETURNING id, assignor_id, face_value, created_at
),
priced AS (
    SELECT r.id, r.assignor_id, r.face_value, r.created_at,
           round(r.face_value / 1.076890625, 2) AS pv_brl
    FROM new_receivable r
)
INSERT INTO settlement (receivable_id, assignor_id, receivable_type, face_value, term_months,
                        base_rate_monthly, spread_monthly, present_value_brl, discount,
                        payment_currency, present_value_payment, fx_rate_id, fx_rate,
                        idempotency_key, request_hash, settled_at)
SELECT p.id, p.assignor_id, 'DUPLICATA_MERCANTIL', p.face_value, 3, 0.01, 0.015, p.pv_brl,
       p.face_value - p.pv_brl,
       CASE WHEN p.id % 4 = 0 THEN 'USD' ELSE 'BRL' END,
       CASE WHEN p.id % 4 = 0 THEN round(p.pv_brl / s.rate, 2) ELSE p.pv_brl END,
       CASE WHEN p.id % 4 = 0 THEN s.id END,
       CASE WHEN p.id % 4 = 0 THEN s.rate END,
       gen_random_uuid(), 'seed', p.created_at
FROM priced p CROSS JOIN seed_rate s;

COMMIT;
ANALYZE;
