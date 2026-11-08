## Contexto
A mesa precisa de um extrato por período, cedente e moeda, com volume. O enunciado aceita atalhar a camada de aplicação em relatórios e valoriza SQL nativo otimizado, então este é o único caminho de leitura fora do JPA.

## Por quê (decisões)
- **SQL nativo com `NamedParameterJdbcTemplate`, sem ORM** — o relatório precisa de JOIN e paginação controlados. Os valores entram só como parâmetros.
- **WHERE montado por fragmentos constantes** — com `:x IS NULL OR coluna = :x` o texto é um só, mas o planner perde o índice e os parâmetros nulos ficam sem tipo. Aqui, filtro ausente some da query. Nada digitado pelo usuário entra no texto do SQL.
- **`ORDER BY` por lista branca, com `s.id` como desempate** — parâmetro de bind não funciona em `ORDER BY`, então a lista branca é o que impede injeção. O desempate evita que uma linha apareça em duas páginas quando há `settled_at` iguais.
- **`from`/`to` são dias inteiros em `America/Sao_Paulo`, com `to` exclusivo no dia seguinte** — é o dia do operador. Um `BETWEEN` com `23:59:59` perderia o último segundo.
- **`COUNT` sem JOIN** — `assignor_id` é `NOT NULL` com FK, então o JOIN não muda o total, só custa.
- **Teste de `EXPLAIN` com 10 mil linhas** — tabela pequena faz o planner preferir Seq Scan, então só com volume e `ANALYZE` o teste diz algo sobre os índices.

## O que mudou
- `StatementQuery` e `StatementController`.
- `StatementApiIT` e `StatementQueryPlanIT`.

## Como testar
```bash
cd backend && ./mvnw verify
curl 'http://localhost:8080/api/settlements?currency=USD&page=0&size=5&sort=settledAt,desc'
```

## Critérios de aceite
AC-STM-01 a AC-STM-06, AC-STM-08 e AC-STM-09; AC-STM-07 por revisão de código.

## Riscos / fora de escopo
A latência com volume é medida em `test/perf`. `OFFSET` fica caro em páginas muito profundas; paginação por cursor seria o próximo passo, mas o grid do operador não navega até lá.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
