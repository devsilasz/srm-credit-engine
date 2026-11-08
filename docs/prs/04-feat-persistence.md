## Contexto
A imutabilidade e a integridade não podem depender só da disciplina do código Java: um `UPDATE` manual em produção também precisa falhar. Esta etapa coloca as regras no próprio banco.

## Por quê (decisões)
- **Trigger que bloqueia UPDATE, DELETE e TRUNCATE em `settlement` e `fx_rate`** — `@Immutable` só protege quem passa pelo Hibernate. O histórico de câmbio segue a mesma regra porque a auditoria de uma liquidação aponta para a linha de taxa.
- **CHECKs de coerência (`present_value_brl + discount = face_value` e câmbio obrigatório fora de BRL)** — são a última linha de defesa contra um bug de aplicação gravando registro incoerente.
- **Constraints `UNIQUE` com nome fixo** — o serviço de liquidação vai distinguir qual delas foi violada para decidir entre replay e `409`; o nome gerado pelo Postgres não é um contrato.
- **Dois índices em `settlement` e nenhum para moeda** — `(settled_at)` e `(assignor_id, settled_at)` atendem o extrato. Moeda tem 2 valores e seria pouco seletivo.
- **Entidades sem associações JPA (`assignorId`, não `Assignor`)** — evita lazy loading e N+1. O extrato usa SQL nativo, então associações não trariam ganho.
- **`assignor_id` repetido em `settlement`** — o índice do extrato não pode depender de um JOIN com `receivable`.

## O que mudou
- `db/migration`: V1 (schema e índices), V2 (trigger), V3 (seed de cedentes).
- `infrastructure/persistence`: entidades e repositórios.
- `TestData` e `SettlementTableIT`.

## Como testar
```bash
cd backend && ./mvnw verify
```
`ddl-auto: validate` falha na subida se entidade e migration divergirem.

## Critérios de aceite
AC-SET-10.

## Riscos / fora de escopo
Testes não limpam o banco, porque nem eles podem apagar liquidações: cada teste cria o próprio cedente e filtra por ele.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
