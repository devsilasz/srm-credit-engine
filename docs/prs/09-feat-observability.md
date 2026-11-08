## Contexto
O eixo sênior do enunciado é operação. No plantão, a pergunta é "quantas liquidações foram recusadas agora, e por quê?". O Anexo B só existe porque o código do Anexo A não tem log nem métrica.

## Por quê (decisões)
- **Um contador com `{currency, type, outcome}` em vez de um por situação** — a mesma série responde "liquidações por minuto" e "taxa de recusa por motivo". O `outcome` sai do `ErrorCode`, então um erro novo aparece na métrica sem mudança extra.
- **Toda tentativa conta, inclusive replay e recusa** — o duplo clique do Anexo B aparece como `outcome="replay"`. Sem isso, o incidente seria invisível.
- **`type="UNKNOWN"` quando o recebível nem foi lido** — `receivable_not_found` e chave reutilizada não têm tipo. Fazer outra consulta só para preencher a métrica custaria mais do que vale.
- **Timer só em volta do motor** — medir com banco e rede misturaria duas perguntas. A latência de ponta a ponta já vem do k6 e do `http_server_requests`.
- **`publishPercentiles` em vez de histograma** — o p99 sai direto em `/actuator/prometheus`, que é o que AC-PERF-02 lê. Histograma daria percentil agregável entre instâncias, mas aqui só há uma. Esses valores são probabilidades, não dinheiro, então o `double` no parâmetro é legítimo.
- **Logs ECS nativos do Spring Boot 3.4+** — JSON no console, sem `logback.xml` nem dependência extra. Ids vão no MDC, então aparecem em toda linha daquela liquidação.

## O que mudou
- `SettlementService`: métrica e linha de log em `finally`, para toda tentativa.
- `QuoteService`: timer `credit_engine_pricing_duration`.
- `application.yml`: `logging.structured.format.console=ecs`.
- `ObservabilityIT`.

## Como testar
```bash
cd backend && ./mvnw verify
curl -s localhost:8080/actuator/prometheus | grep credit_engine_
```

## Critérios de aceite
AC-OPS-02, AC-OPS-03, AC-OPS-04.

## Riscos / fora de escopo
A linha do stack trace de um `500` sai depois do `finally`, então não carrega os ids do MDC. A linha "Liquidação processada" com `outcome="error"` carrega, e as duas se correlacionam pelo horário e pela thread.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
