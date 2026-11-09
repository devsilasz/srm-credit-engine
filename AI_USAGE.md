# AI_USAGE — engenharia da colaboração com IA

> **Esqueleto para o autor revisar e reescrever com a própria voz.** As seções 1 a 3 são TODO de propósito: são escolhas e opiniões do autor, e serão questionadas na defesa. Só a seção "Log do agente" foi preenchida, e somente com fatos desta execução.

## 1. Specs e prompts estratégicos

TODO (autor): explicar como o plano foi dado à IA e por quê.

Pontos de partida no repositório: `PROMPT.md`, `CLAUDE.md`, `.claude/skills/` e `docs/acceptance/`.

## 2. Um caso concreto em que a IA errou, e como o processo detectou

TODO (autor): escolher um caso da seção "Log do agente" e contá-lo com as próprias palavras.

## 3. O que decidi não delegar à IA, e por quê

TODO (autor).

## Log do agente

Fatos registrados durante a implementação, na ordem em que aconteceram. Cada item diz o que falhou, como foi detectado e o que foi corrigido.

### Erros em código ou teste escritos pelo agente

1. **Valor esperado inventado em um teste.** Em `PricingEngineTest#largeFactorKeepsPrecision`, o agente escreveu `140171.21` de cabeça, com um comentário dizendo que o valor estava conferido, o que não era verdade. O teste falhou com `137850.54`. Um cálculo independente (Python `decimal`, prec=2000) deu `137850.5359…`: o motor estava certo e o teste, errado. O comentário foi corrigido junto.
2. **Corrida na idempotência.** `SettlementConcurrencyIT#sameKeyBothGetTheSameSettlement` falhou com `[201, 409]` em 1 de 5 repetições. A requisição perdedora lia a chave antes do commit da vencedora e o recebível depois dele, e respondia `409` sem reler a chave. Detectado pelo `@RepeatedTest(5)`; uma execução única poderia ter passado.
3. **Duplo clique no painel.** O teste com `dblclick` passava só por causa do tempo entre os eventos. O agente suspeitou disso e trocou por dois `button.click()` no mesmo tick. As duas mutações criaram dois recebíveis com a mesma chave. O backend recusou a segunda com `422`, então o dinheiro estava protegido, mas o operador via erro numa liquidação concluída e sobrava um recebível órfão (conferido no banco: recebível `68`, `OPEN`). Corrigido com uma trava síncrona em `useSettlementIntent`.
4. **`crypto.randomUUID` fora de contexto seguro.** Detectado pelo Playwright, que acessa `http://frontend`. Abrindo o painel pelo IP da máquina, a liquidação falhava com a mensagem crua do JavaScript. O agente já tinha registrado isso como risco no PR do frontend, sem tratar como defeito. Corrigido com um fallback para `crypto.getRandomValues`.
5. **Schema `Problem` ausente no OpenAPI.** O `ContractIT` falhou ao carregar o spec: todas as respostas de erro referenciavam um schema inexistente, porque o springdoc descarta os `components` do bean `OpenAPI`. Corrigido registrando o schema no `OpenApiCustomizer`.
6. **`Content-Type` diferente no `413`.** O `PayloadLimitFilter` respondia `application/problem+json;charset=UTF-8`, diferente dos outros erros. Detectado por `ApiErrorsIT#oversizedBodyIs413`.

7. **Painel inutilizável em `http://localhost:3000`, encontrado pelo autor.** Todo POST pelo Nginx recebia `403 Invalid CORS request`, porque `proxy_set_header Host $host` descarta a porta. O backend via `Host: localhost` e `Origin: http://localhost:3000` como origens diferentes. O `verify-all.sh` tinha saído com 0 e o agente tinha declarado o trabalho concluído: o E2E acessava `http://frontend` (porta 80, em que Host e Origin coincidem) e o smoke testava pelo Nginx só um GET, que o navegador envia sem `Origin`. O teste foi trocado para a porta publicada, ficou vermelho, e só então o `nginx.conf` foi corrigido para `$http_host`. O smoke ganhou um POST com `Origin`.

### Falhas de processo do agente

8. **Merge com `verify` local vermelho.** No PR #8, o agente encadeou `verify | grep ... && git commit`, e o `&&` viu o código de saída do `grep`. O `verify` local tinha falhado por causa do iCloud (item 10), e o commit e o merge seguiram. O CI passou no mesmo código. Depois disso, o `verify` passou a rodar por um script que devolve o código real do Maven.
9. **Gate de cobertura que não media nada.** Com um symlink em `target`, os goals `merge`, `report` e `check` do JaCoCo só pularam, e o build deu `BUILD SUCCESS`. O agente notou que o relatório não existia. Corrigido com `followSymlinks`, e o upload do CI passou a falhar se o relatório faltar. O gate foi provado subindo a meta para `0.99`: exit 1 com `Coverage checks have not been met`.
10. **Cópias do iCloud no repositório.** O projeto está numa pasta sincronizada. A sincronização criou cópias como `TestData 2.class`, que quebraram o `verify`, e `.git/index 2`. Um `git add -A` levou 8 cópias de arquivos do frontend para a `main` no PR #12. Elas foram removidas por um commit normal (PR #13), sem reescrever histórico. Um script de limpeza também apagou um diretório `.git 2` antes de o agente inspecioná-lo: era uma cópia não rastreada, e o repositório real seguiu íntegro (`git fsck` limpo). Mitigação local: diretórios `.nosync` e exclusões do git. **Recomendação ao autor: mover o repositório para fora de Desktop/Documentos.**
11. **Escrita fora do diretório do projeto.** A primeira execução do Testcontainers criou `~/.testcontainers.properties`, apesar do limite do `PROMPT.md`. O arquivo não existia antes e foi removido, e as execuções seguintes usam `user.home` em `.cache`. Alguns logs também foram gravados em `/tmp` por engano e apagados em seguida.
12. **Asserção nova que não testava nada.** O POST com `Origin` adicionado ao smoke no PR #18 imprimia `ok ...: 400`. No bash 3.2 do macOS, o `{a,b}` do JSON dentro de `"$(...)"` sofreu expansão de chaves antes da substituição: o curl rodou duas vezes com JSON partido, e a chamada virou `assert_eq desc 400 400 200`, com `$2` igual a `$3`. O agente notou o `400` no log depois do merge; um controle negativo confirmou que `403` também passava. Corrigido com a resposta em variável, e `assert_eq` agora recusa número errado de argumentos.

### Divergências encontradas nos documentos do kit

13. Skill `golden-cases`: a conferência manual cita `92859,94 / 5,4321 = 17094,670…`. O valor real é `17094,6668…`. O resultado arredondado (`17094,67`) não muda.
14. AC-SET-09 presume INSERT antes do UPDATE, o que contraria o `CLAUDE.md` (R3 do `SPEC.md`).
15. AC-FX-07 pede "< 50 ms", e a skill `quality-gates` proíbe limite de tempo em teste de integração (R4 do `SPEC.md`).
