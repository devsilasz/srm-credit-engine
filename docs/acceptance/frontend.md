# Frontend / usabilidade — `AC-UI`

| ID | Dado | Quando | Então | Tipo | Teste |
|---|---|---|---|---|---|
| AC-UI-01 | painel aberto | o operador preenche valor, vencimento, tipo e moeda válidos | o valor líquido aparece em ≤ 1 s após parar de digitar (debounce de 300 ms) | Usabilidade | PENDENTE |
| AC-UI-02 | simulação exibida | — | mostra PV, deságio, taxa base + spread, prazo em meses e, se USD, a taxa usada com a hora de vigência | Usabilidade | PENDENTE |
| AC-UI-03 | valor `100000,00`, duplicata, +3 meses, BRL | simulação | exibe `R$ 92.859,94` (formato pt-BR) | Funcional | PENDENTE |
| AC-UI-04 | campo inválido (vazio, ≤ 0, data passada) | — | mensagem inline no campo, sem chamada à API e botão Liquidar desabilitado | Usabilidade | PENDENTE |
| AC-UI-05 | API retorna `ProblemDetail` | simulação ou liquidação | exibe o `detail` legível, nunca JSON cru nem "Erro desconhecido" | Usabilidade | PENDENTE |
| AC-UI-06 | o operador clica Liquidar 2x rápido | — | uma única requisição sai (botão desabilitado durante o envio); se houver retry, ele reusa a mesma `Idempotency-Key` | Funcional | PENDENTE |
| AC-UI-07 | liquidação concluída | — | confirmação com o id da liquidação; o grid é atualizado (invalidação da query) | Usabilidade | PENDENTE |
| AC-UI-08 | grid com > 20 registros | troca de página ou de filtro | a requisição leva `page`/`size`/filtros; os filtros ficam na URL (recarregar mantém o estado) | Funcional | PENDENTE |
| AC-UI-09 | — | navegação só por teclado | todos os campos e botões são alcançáveis por Tab; labels associados (`<label for>`) | Usabilidade | MANUAL: navegar com Tab |
| AC-UI-10 | — | revisão de código | nenhuma conta monetária com `number` em `frontend/src` (só exibição de strings da API / `decimal.js`) | Segurança | MANUAL: grep da skill monetary-precision |
| AC-UI-11 | recebível criado e `POST /api/settlements` falhou (rede ou 5xx) | o operador clica Liquidar de novo | a nova tentativa reusa o mesmo `receivableId` e a mesma `Idempotency-Key`; nenhum segundo recebível é criado | Funcional | PENDENTE |
