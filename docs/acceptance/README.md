# Critérios de aceite

> **Status: RASCUNHO — revisar e assumir como seus antes da entrega.** Na defesa, qualquer linha daqui pode ser questionada ("por que 150 ms?"). Ajuste os números para algo que você consiga defender.

Formato e regras: skill `.claude/skills/acceptance-criteria`.

| Arquivo | Área | Prefixo |
|---|---|---|
| [pricing.md](pricing.md) | Motor de precificação e simulação | `AC-PRC` |
| [settlement.md](settlement.md) | Liquidação, idempotência, concorrência e auditoria | `AC-SET` |
| [fx.md](fx.md) | Câmbio e resiliência | `AC-FX` |
| [statement.md](statement.md) | Extrato e grid | `AC-STM` |
| [frontend.md](frontend.md) | Usabilidade do painel do operador | `AC-UI` |
| [non-functional.md](non-functional.md) | API e contrato, segurança, carga e estresse, cobertura e operação | `AC-API`, `AC-SEC`, `AC-PERF`, `AC-OPS`, `AC-COV` |

Coluna **Teste**: `Classe#metodo` · `MANUAL: passo` · `PENDENTE` (o agente preenche durante a implementação).
