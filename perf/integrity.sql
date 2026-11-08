-- Depois do estresse, todas as linhas precisam mostrar 0.
SELECT 'recebiveis_com_mais_de_uma_liquidacao' AS verificacao, count(*) AS total FROM (
    SELECT receivable_id FROM settlement GROUP BY receivable_id HAVING count(*) > 1
) duplicated
UNION ALL
SELECT 'recebiveis_settled_sem_liquidacao', count(*)
FROM receivable r
WHERE r.status = 'SETTLED' AND NOT EXISTS (SELECT 1 FROM settlement s WHERE s.receivable_id = r.id)
UNION ALL
SELECT 'liquidacoes_com_recebivel_nao_settled', count(*)
FROM settlement s JOIN receivable r ON r.id = s.receivable_id
WHERE r.status <> 'SETTLED'
UNION ALL
SELECT 'idempotency_key_duplicada', count(*) FROM (
    SELECT idempotency_key FROM settlement GROUP BY idempotency_key HAVING count(*) > 1
) duplicated
UNION ALL
SELECT 'liquidacao_usd_sem_taxa', count(*)
FROM settlement
WHERE payment_currency = 'USD' AND (fx_rate_id IS NULL OR fx_rate IS NULL)
UNION ALL
SELECT 'liquidacao_com_soma_incoerente', count(*)
FROM settlement
WHERE present_value_brl + discount <> face_value;
