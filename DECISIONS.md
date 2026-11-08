# DECISIONS — cortes e simplificações

O que foi deliberadamente cortado ou simplificado, e por quê.

## Fora do escopo

| Item | Por que ficou de fora | O que seria |
|---|---|---|
| **Autenticação e autorização** | O enunciado não pede, e implementar pela metade daria falsa segurança. | OAuth2/JWT, com papéis OPERADOR (liquida) e AUDITOR (só lê). |
| **Mensageria / EDA** | Um único serviço com uma transação local resolve a atomicidade. Evento traria consistência eventual sem nenhum consumidor real. | Outbox para publicar `SettlementCreated`. |
| **ADRs extensos** | O alvo é Pleno. As decisões estão aqui, no `SPEC.md` e nas descrições dos PRs. | Um ADR por decisão difícil. |
| **Design para 1M tx/min** | Item do nível Staff, fora do alvo. | Particionar `settlement`, chave de idempotência com TTL. |
| **Multi-tenant** | Só existe um fundo no enunciado. | — |
| **Moedas além de BRL e USD** | O enunciado cita só as duas. O código deixa o caminho aberto: `PaymentCurrency.fxPair()` monta o par, e o que falta é o valor no enum e os `CHECK`. | — |

## Simplificações

| Decisão | Motivo | Quando rever |
|---|---|---|
| **Liquidação unitária, sem lote atômico** | O "lote" é atendido pelo operador liquidando N itens, cada um independente e idempotente. Tudo-ou-nada é uma decisão de negócio ainda em aberto (`SPEC.md`). | Se o negócio exigir lote tudo-ou-nada. |
| **Um único `BusinessRuleException` com `ErrorCode`** | Uma classe por erro seria volume sem ganho. O `switch` do handler é exaustivo: um código novo sem status não compila. | Se algum erro precisar carregar dados próprios. |
| **Entidades JPA como modelo; sem mapear para objetos de domínio** | A regra de precificação já é Java puro. Duplicar `Settlement` em duas camadas não protegeria nada. | Se a persistência começar a vazar para a regra. |
| **Controller lendo direto o repositório em `/api/assignors` e `/api/settlements` (GET)** | Sem regra de negócio, um serviço só repassaria a chamada. O enunciado permite atalhar em relatórios. | Se surgir regra nessas leituras. |
| **Grid sem ordenação por coluna** | A API aceita `sort`, mas o enunciado pede só paginação e filtros. | Se a mesa pedir. |
| **Paginação por `OFFSET`** | Simples, e o grid não navega para páginas profundas. | Paginação por cursor, se isso mudar. |
| **Prazo limitado a 360 meses e recusa de PV zero** | Premissas conservadoras (R1 e R2 do `SPEC.md`). | Quando o negócio definir os limites reais. |

## Stack

| Decisão | Motivo |
|---|---|
| **Spring Boot 3.5.x, fora do suporte open source** | A linha 3.x já saiu do suporte open source, e em um sistema real a migração para 4.x entraria no planejamento. Neste case, migrar traria risco de compatibilidade (springdoc, Hibernate, Jackson) sem ganho funcional, a poucos dias de uma defesa ao vivo. |
| **Testcontainers 1.21.x gerenciado pelo Boot** | O CI passou na primeira execução, sem o erro `client version ... is too old`, então não foi preciso subir para a 2.x. |
| **Sem Redux nem estado global no frontend** | Estado do servidor no TanStack Query, do formulário no React Hook Form e os filtros na URL. Não sobra nada para um estado global. |
| **Filtros na URL sem React Router** | Só existe uma tela. |
| **Sem framework de teste unitário no frontend** | O cliente não precifica: a regra está no backend. Um único fluxo Playwright cobre o que o painel pode quebrar. |

## Testes

| Decisão | Motivo |
|---|---|
| **Build Docker com `-DskipTests`** | Testcontainers dentro do `docker build` exigiria Docker-in-Docker. Os testes rodam no CI e no `./mvnw verify`. |
| **Carga e estresse fora do CI** | Runner compartilhado gera números sem significado. Rodam localmente, e o relatório fica versionado em `docs/perf/`. |
| **Playwright fora do CI, em `scripts/verify-all.sh`** | O fluxo precisa do compose inteiro no ar. O CI já cobre backend e frontend, e um fluxo E2E nele custaria minutos em todo push. |
| **Exclusões de cobertura: `**/CreditEngineApplication*` e `**/config/**`** | Só bootstrap e configuração. Nenhum service nem controller foi excluído para bater a meta. |
| **Sem limite de tempo nos testes de integração** | Fica instável com a JVM fria. Latência se mede na carga. Consequência: os "< 50 ms" de AC-FX-07 não têm verificação automática (R4 do `SPEC.md`). |

## Segurança mínima

| Decisão | Motivo |
|---|---|
| **Swagger e métricas abertos** | Sem autenticação no escopo, e o projeto só roda localmente. Em produção ficariam atrás de autenticação ou de uma rede interna. |
| **Credenciais do banco com padrão no compose** | Para subir sem `.env`. Os valores são só de desenvolvimento, e o compose lê de variáveis de ambiente. |
