# Especificação técnica — marcadores da captura HTML

**Estado:** aplicada no plugin 2.5.2-beta e instalada na pasta de teste; testes automatizados executados. A validação manual de captura dentro do IPED depende de executar a interface.  
**Alvo:** Chat Captura IPED para IPED 4.3.1.  
**Base de código:** plugin 2.5.1-beta; correção distribuída como 2.5.2-beta.  
**Objetivo:** impedir que uma seleção aceita pela interface falhe ao iniciar a captura HTML e garantir que uma captura válida crie uma pasta final completa, sem deixar evidência parcial.

## 1. Problema confirmado

Na instalação de teste, o JAR carregado pelo IPED é o plugin `2.5.1-beta`. O erro atualmente registrado é:

```text
Captura HTML interrompida: end_anchor_missing_or_ambiguous
```

O erro ocorre antes de a captura ser promovida. Por isso `teste_chat_extraido` recebe a atualização de `capturas-index.json`, `index.html` e `relatorio.htm`, mas nenhuma pasta `captura-chat-*` é criada.

### 1.1 Causa raiz

O menu de contexto identifica uma linha de data e apresenta o item específico **Adicionar data como início de captura**. Porém, o item genérico **Adicionar mensagem final de captura** continua ativo para essa mesma linha.

Quando esse item é usado sobre uma data, `captureEndMessageId` recebe um ID temporário como `iped-capture-date-...`. A rotina HTML depois enumera somente `div.linha[id]` com ID numérico e aceita a conversão de data apenas para o início. Para o fim, exige que o ID armazenado já apareça uma única vez na lista numérica. O ID de data não aparece nessa lista e a captura falha com `end_anchor_missing_or_ambiguous`.

O fluxo apresenta três contratos contraditórios:

| Camada | Comportamento atual | Consequência |
|---|---|---|
| Menu de contexto | Permite marcar fim sobre uma data | Salva um marcador que não representa mensagem |
| Validação inicial | Apenas verifica se o elemento DOM existe | Aceita o marcador temporário de data |
| Extrator HTML | Exige fim numérico único | Interrompe a captura depois de Start |

### 1.2 Evidências no código

- `HtmlLinkViewer` cria o menu, detecta data, mas desabilita o fim apenas quando não existe elemento selecionado: linhas 228–247.
- `setChatCaptureEndFromContext()` grava o ID recebido sem resolver ou validar seu tipo: linhas 286–293.
- `ensureCaptureRangeElementsAvailable()` aceita qualquer ID presente no DOM: linhas 2222–2235.
- `runHtmlMessageCapture()` converte data apenas com `resolveStart`; o fim precisa existir na lista numérica: linhas 2439–2442.
- A promoção de `staging` para `captureDir` ocorre somente após HTML, manifesto e anexos terem sido gravados: linhas 2598–2606.

O HTML de referência do IPED 4.3.1 confirma o contrato usado pelo plugin: há separadores `div.linha` sem ID numérico e mensagens `div.linha` com IDs como `243188`, `243189` e `243190`.

## 2. Decisão de produto e regra de seleção

O formato `html-message-v1` aceita exclusivamente **mensagens elegíveis** como limites persistentes do intervalo. Uma data é um separador visual, não uma mensagem auditável, e não pode ser usada diretamente como marcador final.

### 2.1 Regras obrigatórias

1. Uma mensagem elegível é um elemento do contrato IPED 4.3.1 com `div.linha[id]` e ID decimal único.
2. Uma data pode ser usada somente como início. Ela é resolvida para a primeira mensagem elegível posterior no DOM.
3. Uma data não pode ser usada como fim. O menu não deve oferecer ação de fim quando o alvo for data.
4. Um fim precisa apontar para uma mensagem elegível. Não há conversão implícita de data para “mensagem anterior” ou “última mensagem do dia”, pois ambas são ambíguas em separadores de data do IPED.
5. A seleção persistida em `captureStartMessageId` e `captureEndMessageId` deve conter apenas IDs numéricos antes de Start ser habilitado.
6. O intervalo é inclusivo e exige que `startIndex <= endIndex` na ordem DOM atual.
7. Duplicidade, ausência, ordem invertida ou resolução ambígua impedem Start e explicam a causa sem iniciar thread de captura.

## 3. Arquitetura da correção

### 3.1 Centralizar resolução de marcador

Criar um único resolvedor `CaptureBoundaryResolver`, usado tanto na ação do menu quanto na validação antes da captura.

```java
CaptureBoundaryResolver.resolve(rows, requestedId, role)
```

`CaptureBoundaryRole` possui os valores `START` e `END`. `CaptureBoundary` contém:

| Campo | Significado |
|---|---|
| `requestedId` | ID retornado por `messageAt` para diagnóstico local |
| `resolvedMessageId` | ID decimal único efetivamente usado; vazio em falha |
| `sourceKind` | `message` ou `date` |
| `errorCode` | código estável de falha ou vazio |
| `userMessageKey` | chave de localização para a interface |

A implementação lê uma única lista ordenada das linhas `div.linha` no WebView e passa os IDs numéricos e os marcadores de data ao resolvedor Java:

1. Enumera `div.linha[id]` em ordem documental e cria a lista de IDs decimais.
2. Conta ocorrências do ID solicitado na lista elegível.
3. Para ID decimal com uma ocorrência: retorna essa mesma ID para início ou fim.
4. Para início em `data-iped-capture-date`: encontra a linha de data e retorna a primeira mensagem decimal posterior.
5. Para fim em `data-iped-capture-date`: retorna `end_date_not_supported` sem procurar mensagem próxima.
6. Para qualquer outro tipo: retorna `marker_not_message`.
7. Para zero ou mais de uma ocorrência de uma mensagem: retorna `marker_missing` ou `marker_ambiguous`.

O JavaScript de resolução não lê anexos, texto, remetente, data/hora nem conteúdo da mensagem. Ele retorna somente IDs e classificações de marcador.

### 3.2 Corrigir o menu de contexto

Ao abrir o menu:

- Sobre mensagem: mostrar **Adicionar mensagem inicial de captura** e **Adicionar mensagem final de captura**.
- Sobre data: mostrar somente **Adicionar data como início de captura**; não adicionar o item de fim.
- Sem elemento elegível/data: desabilitar as ações de marcação.

Não criar uma opção “data como fim” nesta versão. Caso ela seja desejada no futuro, deverá ter regra explícita, texto próprio e testes independentes.

### 3.3 Gravar somente marcador resolvido

- `setChatCaptureStartFromContext()` resolve com papel `START`. Em sucesso, grava `resolvedMessageId`, não o ID temporário de data.
- `setChatCaptureStartDateFromContext()` pode reutilizar a mesma rotina `START`; o item de data existe apenas para comunicar a semântica ao usuário.
- `setChatCaptureEndFromContext()` resolve com papel `END`. Em sucesso, grava somente ID decimal.
- Em falha, preservar o marcador anterior. Não limpar silenciosamente uma seleção válida.
- O status deve identificar a falha com texto localizado. Não mostrar o ID temporário ao usuário final; o ID pode ser incluído somente no log de diagnóstico.

### 3.4 Validar antes de iniciar a thread

Executar `validateCaptureStart(File)` na EDT/WebView antes de instalar os arquivos do verificador e chamar `startCaptureBody()`:

```java
public boolean validateCaptureStart(File outputFolder)
```

Ela deve:

1. Resolver de novo os dois IDs já persistidos como `START` e `END`.
2. Exigir dois IDs decimais únicos.
3. Obter índices da mesma enumeração DOM e exigir ordem válida.
4. Atualizar os campos com os IDs resolvidos apenas após ambos terem passado.
5. Retornar mensagem localizada de erro ao painel quando inválida.

Se a validação falhar, `startCaptureBody()` não é chamado, `captureRunning` permanece `false`, a interface continua interativa e nenhuma atualização do índice de capturas é executada.

### 3.5 Manter defesa no worker

`ensureCaptureRangeElementsAvailable()` e `runHtmlMessageCapture()` continuam revalidando o intervalo no worker, porque o DOM pode mudar entre o clique em Start e a extração.

Substituir a lógica local `resolveStart` por uma rotina JavaScript compartilhada conceitualmente com `resolveCaptureBoundary`. No worker, ambos os limites já precisam ser decimais. Se um deles deixar de existir, retornar um erro específico:

- `start_marker_missing`
- `end_marker_missing`
- `start_marker_ambiguous`
- `end_marker_ambiguous`
- `marker_order_invalid`

Não usar `end_anchor_missing_or_ambiguous` para um marcador de data aceito pela interface, pois a interface deverá bloqueá-lo antes da thread.

## 4. Estados e mensagens da interface

Adicionar chaves nos dois bundles `iped-viewer-messages.properties`:

| Chave | PT-BR esperado |
|---|---|
| `ChatCapturePanel.EndDateNotAllowed` | Selecione uma mensagem como fim; uma data só pode ser usada como início. |
| `ChatCapturePanel.StartDateNoMessage` | Não há mensagem após a data selecionada. |
| `ChatCapturePanel.MarkerNotMessage` | Selecione uma mensagem válida da conversa. |
| `ChatCapturePanel.MarkerAmbiguous` | A mensagem marcada não é única na conversa carregada. Marque novamente. |
| `ChatCapturePanel.MarkerMissing` | A mensagem marcada não está mais disponível na conversa carregada. |
| `ChatCapturePanel.InvalidRangeOrder` | A mensagem inicial deve estar antes ou ser igual à mensagem final. |
| `ChatCapturePanel.RangeReady` | Intervalo pronto: início {0}, fim {1}. |

O botão Start pode permanecer visível e ativo, mas o clique deve validar e informar o erro sem disparar a captura. A alternativa aceitável é desabilitar Start enquanto não houver um intervalo válido, desde que a atualização de estado ocorra quando o usuário marca, remarca ou limpa os limites.

## 5. Persistência, logs e comportamento de falha

### 5.1 Persistência

- O manifesto novo grava `anchors.start` e `anchors.end` somente com IDs decimais resolvidos.
- Não gravar IDs `iped-capture-date-*` no manifesto, em anotações de intervalo, índice ou dados usados pelo Re-Extrair.
- Capturas anteriores não são alteradas por esta correção.

### 5.2 Logs

No nível `DEBUG`, registrar somente:

```text
Capture boundary resolve: role=END requestedKind=date result=end_date_not_supported
Capture range validation: start=243188 end=243221 valid=true
```

Não registrar texto, anexo, remetente, caminho absoluto da evidência ou conteúdo HTML.

### 5.3 Transação de saída

Comportamento aplicado:

1. Extrair e validar mensagens e anexos.
2. Gravar em diretório temporário irmão da pasta final.
3. Validar manifesto, HTML e inventário.
4. Mover o temporário irmão para `captura-chat-*` por movimento atômico, com fallback sem substituição quando o sistema de arquivos não oferecer movimento atômico.
5. Em falha anterior à promoção, limpar apenas o temporário e nunca criar uma pasta final incompleta.

Além disso, não chamar `safeRebuildCaptureRootIndex(outputRoot)` quando a validação prévia falhar. Assim, o usuário não recebe arquivos de índice/reports atualizados sem uma tentativa real de captura.

## 6. Testes obrigatórios antes da aplicação

Criar testes isolados em `teste_antes_de_aplicar/` e acrescentar testes de produção compatíveis com o ambiente Java/IPED já usado pelo projeto.

### 6.1 Fixture DOM mínima

Criar uma fixture com:

```html
<div class="linha"><span data-iped-capture-date="true" id="iped-capture-date-a">2026-11-07</span></div>
<div class="linha" id="100">Mensagem A</div>
<div class="linha" id="101">Mensagem B</div>
<div class="linha"><span data-iped-capture-date="true" id="iped-capture-date-b">2026-11-08</span></div>
<div class="linha" id="102">Mensagem C</div>
```

Também criar variantes com ID ausente, ID duplicado, data sem mensagem posterior, início posterior ao fim e elemento auxiliar sem classe `linha`.

### 6.2 Matriz de teste

| Caso | Ação | Resultado esperado |
|---|---|---|
| Mensagem → mensagem | Marcar `100` como início e `101` como fim | Intervalo válido `100..101` |
| Data → mensagem | Marcar `iped-capture-date-a` como início e `101` como fim | Início resolvido para `100`; intervalo válido |
| Data sem mensagem | Marcar data final da fixture como início | `StartDateNoMessage`; marcador anterior preservado |
| Data como fim | Tentar abrir/acionar fim sobre `iped-capture-date-b` | Ação de fim ausente; se invocada por teste interno, `EndDateNotAllowed` |
| Fim ausente | Remover `101` depois de marcar | Falha controlada antes de criar pasta |
| ID duplicado | Duplicar `101` | `MarkerAmbiguous`; não iniciar worker |
| Ordem invertida | Início `102`, fim `100` | `InvalidRangeOrder`; não iniciar worker |
| Transação | Forçar falha de extração após validação | Sem `captura-chat-*`; sem índice/reports atualizados por essa falha |
| Sucesso | Intervalo válido com HTML e anexo permitido | Uma pasta final com `whatsapp.html`, `manifest.json`, `messages.json`, inventário e anexos necessários |
| Regressão legado | Desativar `htmlMessageV1` | Fluxo PNG atual mantém seus resultados |

### 6.3 Teste no HTML real do IPED 4.3.1

Usar o arquivo de referência `IPED_testes_chat_real/Exportados/arquivos/F/A/FAF3132301A706022E1C3EAD6AE0800B.html` para confirmar:

1. O seletor reconhece todos os `div.linha` numéricos e não inclui topbar, modal, campos ocultos ou separador de data.
2. Uma marcação normal de duas mensagens numéricas produz intervalo inclusivo com as IDs esperadas.
3. O menu não permite fim sobre uma data identificada dinamicamente.
4. O manifest grava IDs numéricos e o Re-Extrair consegue relocalizá-las.

### 6.4 Critérios de aprovação prévia

- Todos os casos da matriz passam.
- Nenhum caso inicia a thread quando os limites são inválidos.
- Nenhum caso inválido cria ou altera pasta de captura, HTML, manifesto ou índice de capturas.
- Um caso válido cria uma única pasta de captura completa e é aceito pelo Verificador de Integridade 1.3.1.
- Testes existentes de `html-message-v1`, capturas legadas, anexos, CSP, sanitização SVG e Re-Extrair continuam aprovados.

## 7. Aplicação e distribuição

1. Correção aplicada no plugin Java e nos dois bundles de idioma.
2. Compilação do JAR contra as classes do IPED 4.3.1 concluída.
3. Suíte automatizada e teste de contrato sobre o HTML de referência executados.
4. Plugin atualizado para `2.5.2-beta`; o Verificador continua `1.3.1`, sem alteração de bytes.
5. `README.md`, `doc.md`, `CHANGELOG.md`, `CHECKSUMS.md` e ZIP do instalador atualizados com os hashes reais.
6. JAR instalado em `IPED_testes_chat_real/iped/lib/iped-viewers-impl-4.3.1.jar` com backup do JAR anterior; uma captura real de ponta a ponta ainda exige abrir o IPED e selecionar o trecho na interface.

## 8. Fora de escopo

- Converter uma data em fim por heurística.
- Mudar o contrato de mensagens do IPED 4.3.1.
- Alterar o formato de capturas existentes.
- Alterar JAR/EXE do Verificador de Integridade sem mudança no seu código.
- Publicar release ou enviar alterações ao repositório remoto.
