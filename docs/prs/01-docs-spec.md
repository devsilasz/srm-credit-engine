## Contexto
O enunciado exige um `SPEC.md` antes do código e contém ambiguidades propositais (unidade do prazo, taxa base, arredondamento, qual câmbio vale). Sem decidi-las primeiro, cada uma vira um bug silencioso em dinheiro.

## Por quê (decisões)
- **Prazo em meses com `ceil`** — bate com os golden cases e é conservador para o fundo. Pró-rata por dias úteis foi descartado porque o golden case fixa meses inteiros; ficou como pergunta ao negócio.
- **Taxa vigente no instante da liquidação, lida do banco** — a taxa da data de vencimento ainda não existe nesse momento, e chamar o provedor na liquidação colocaria uma dependência externa no caminho crítico.
- **Recusar (`503`) quando a taxa venceu** — liquidar com taxa errada é pior do que não liquidar.
- **Dois critérios novos (AC-PRC-16 e AC-PRC-17)** — a revisão encontrou dois casos não previstos: vencimento muito distante deixa o `pow` caro sem limite, e um PV arredondado a zero compraria o título de graça. Os dois são recusados com `422` e ficam marcados como decisão do autor.
- **Conflito AC-SET-09 × `CLAUDE.md` registrado na seção 5 do SPEC** — o critério presume INSERT antes do UPDATE, mas nessa ordem a `UNIQUE` age primeiro e o `@Version` nunca dispararia.

## O que mudou
- `SPEC.md`: premissas, perguntas ao negócio, precisão numérica, resumo dos critérios e premissas a revisar.
- `docs/acceptance/pricing.md`: AC-PRC-16 e AC-PRC-17.

## Como testar
Só leitura: confira o SPEC contra a seção 3 do enunciado.

## Critérios de aceite
AC-PRC-16, AC-PRC-17 (definição; os testes vêm em `feat/pricing-engine`).

## Riscos / fora de escopo
A decisão R3 inverte a ordem citada em AC-SET-09. O autor precisa revisá-la antes da defesa.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
