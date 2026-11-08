---
name: acceptance-criteria
description: Estrutura, valida e rastreia critérios de aceite do SRM Credit Engine em docs/acceptance/. Use ao criar ou revisar critérios, ao iniciar qualquer feature (para saber o que provar) e ao final (para checar cobertura). Não decide regras de negócio — só garante rigor e rastreabilidade.
---

# Critérios de aceite

## Princípio

Os critérios pertencem ao **autor**: o enunciado pede critérios "que você mesmo definiu". Esta skill **não inventa regra de negócio**. Ela:
1. força um formato verificável;
2. rejeita critérios vagos;
3. liga cada critério a um teste ou a um passo manual;
4. aponta lacunas.

Se um critério exigir uma decisão de negócio que não está no `CLAUDE.md`, marque `⚠️ DECISÃO DO AUTOR` em vez de decidir.

## Formato obrigatório

Cada arquivo em `docs/acceptance/` é uma área (`pricing.md`, `settlement.md`, …). Cada critério é uma linha de tabela:

| ID | Dado | Quando | Então | Tipo | Teste |
|---|---|---|---|---|---|
| AC-SET-03 | recebível `OPEN` e chave `K` já usada com o mesmo payload | `POST /api/settlements` com `Idempotency-Key: K` | `200`, mesmo `settlementId`, `count(settlement)` inalterado | Funcional | `SettlementIdempotencyIT#sameKeySamePayload` |

- **ID:** `AC-<ÁREA>-NN`, onde ÁREA ∈ `PRC`, `SET`, `FX`, `STM`, `API`, `UI`, `SEC`, `PERF`, `OPS`, `COV`. IDs nunca são reaproveitados; um critério removido vira ~~riscado~~ com motivo.
- **Tipo:** Funcional, Segurança, Desempenho, Usabilidade ou Operação.
- **Teste:** `Classe#metodo`, ou `MANUAL: <passo reproduzível>`, ou `PENDENTE`.

## Regras de qualidade (rejeite o critério se violar)

- **Mensurável:** proibido "rápido", "seguro", "amigável", "corretamente" ou "adequado". Troque por número, status HTTP, contagem de linhas ou texto exato.
  - ❌ "A simulação deve ser rápida" → ✅ "p95 de `POST /quote` < 150 ms com 20 req/s por 60 s (k6, local)"
  - ❌ "Erros devem ser tratados" → ✅ "Recebível inexistente → `404`, `Content-Type: application/problem+json`, `type` = `/errors/receivable-not-found`"
- **Um comportamento por linha.** "E" no Então só vale para efeitos do mesmo evento.
- **Observável de fora:** status, corpo, banco, métrica, log ou tela. Nada de "o service chama o repository".
- **Inclua o caminho infeliz:** cada critério funcional feliz deve ter ao menos um irmão de erro ou de borda.

## Como usar durante o desenvolvimento

1. **Antes de codar uma feature:** liste os IDs da área e escreva os testes com o ID no nome ou no `@DisplayName("AC-SET-03 …")`.
2. **Ao terminar:** atualize a coluna `Teste`.
3. **Checagem de cobertura** (rodar antes de cada merge):
   ```bash
   # IDs definidos
   grep -rhoE "AC-[A-Z]+-[0-9]+" docs/acceptance | sort -u > /tmp/ac_def
   # IDs referenciados em testes
   grep -rhoE "AC-[A-Z]+-[0-9]+" backend/src/test frontend/src frontend/e2e 2>/dev/null | sort -u > /tmp/ac_test
   comm -23 /tmp/ac_def /tmp/ac_test   # critérios sem teste automatizado
   ```
   Todo ID listado precisa estar marcado como `MANUAL` (com passo) ou `PENDENTE` (com motivo em DECISIONS.md).
4. **No SPEC.md:** só um resumo por área com link para o arquivo; o detalhe vive em `docs/acceptance/`.

## Ao revisar critérios existentes

Para cada linha, responda: (a) é mensurável? (b) um teste consegue falhar por causa dela? (c) existe o caso de erro irmão? Corrija o formato livremente; **conteúdo de regra de negócio** só com base no `CLAUDE.md` ou com a marca `⚠️ DECISÃO DO AUTOR`.
