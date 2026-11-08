---
name: git-workflow
description: Como versionar o SRM Credit Engine — branches por feature, Conventional Commits, commits atômicos e descrições de PR com o porquê. Use em todo commit, branch e merge.
---

# Git workflow

O histórico vai ser aberto na defesa ("por que esse commit?"). Ele precisa contar a história da construção.

## Estratégia: GitHub Flow

`main` sempre verde. Uma branch curta por etapa do `PROMPT.md` e um PR real com CI verde antes do merge (`git merge --no-ff` local só se o GitHub estiver indisponível). Justificativa para o README: projeto de uma pessoa, entrega contínua e sem releases paralelas. Git Flow seria cerimônia sem benefício aqui.

Antes do primeiro commit:
```bash
git init -b main
git config user.name >/dev/null || echo "⚠️ configure user.name/email do AUTOR antes de continuar"
gh repo create devsilasz/srm-credit-engine --private --source=. --remote=origin --push   # depois do commit inicial
```
Os commits devem sair com a identidade do autor. Não invente nome nem e-mail. O único remoto permitido é `devsilasz/srm-credit-engine`; `devsilasz/desafio_srm` é um projeto anterior e não pode ser alterado.

## Commits

- **Conventional Commits:** `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `ci`, `build`, com escopo: `feat(settlement): enforce idempotency key`.
- Assunto no imperativo, ≤ 72 caracteres e em inglês **ou** português, de forma consistente no repo todo (padrão: inglês).
- Corpo explica o **porquê** quando não for óbvio. Referencie critérios: `Refs: AC-SET-03`.
- **Atômico:** um commit compila, passa nos testes e faz uma coisa só. Teste e código da mesma regra podem ir juntos.
- Bons commits isolados que valem a pena ter (viram história na defesa):
  - `test(pricing): add golden cases C1–C3` (antes da implementação, vermelho na branch e verde no commit seguinte)
  - `fix(pricing): round BRL present value before FX conversion` se isso acontecer de verdade
  - `feat(db): forbid UPDATE/DELETE on settlement via trigger`
- Proibido: `wip`, `fix stuff`, `update`, commits gigantes do tipo "implement everything" e commits com testes quebrados na `main`.

## Descrição de PR (`docs/prs/NN-<branch>.md` e corpo do PR)

```markdown
## Contexto
<problema de negócio ou técnico que motivou>

## Por quê (decisões)
- <decisão> — <alternativa descartada e motivo>

## O que mudou
- <lista curta>

## Como testar
<comandos exatos e resultado esperado>

## Critérios de aceite
AC-XXX-NN, …

## Riscos / fora de escopo
<o que pode quebrar, o que ficou para depois>
```

Escreva como quem ensina: um dev júnior deve entender a decisão lendo só o PR.

## Antes de cada merge

```bash
cd backend && ./mvnw verify
cd ../frontend && npm run lint && npm run build   # se a etapa tocar o frontend
```
Se algo estiver vermelho, não faça merge.
