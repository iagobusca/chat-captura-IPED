# Especificacao Tecnica: Notas e Links por Mensagem (Chat Captura IPED)

Versao do documento: **1.0**
Versao alvo do plugin: **2.0.0**
Base analisada: `src/iped/viewers/HtmlLinkViewer.java` (3646 linhas) e `src/iped/viewers/ChatCapturePanel.java` (232 linhas)
Alvo: **IPED 4.3.1 / Java 11**

---

## 1. Objetivo

Adicionar ao plugin Chat Captura a capacidade de o analista **anotar** mensagens do chat e **vincular** cada mensagem a contatos especificos, com saida persistida e visualizavel no HTML gerado pela captura.

Duas novas acoes no menu de contexto (botao direito) sobre a mensagem:

- **Notas** - texto livre do analista sobre a mensagem.
- **Link** - lista de contatos aos quais aquele trecho de mensagem esta vinculado.

Complementos obrigatorios:

- regra de elegibilidade dos dois itens;
- dialogo de confirmacao (pre-flight) no `Start`, com contagens e exclusao do que esta fora do intervalo;
- `toggle` no painel para ligar/desligar o recurso nesta captura;
- compatibilidade total com capturas antigas (sem o recurso);
- no HTML gerado, clique na mensagem mostra a nota e/ou a lista de contatos, e **com 1 unico contato mostra direto o trecho capturado**.

## 2. Escopo

### 2.1 Principio: o recurso e opcional em todos os niveis

**Notas e Links nunca sao obrigatorios.** O recurso e off por padrao na leitura e so entra na
saida quando o analista o usa explicitamente. Nenhuma captura precisa ter anotacao.

| Nivel | Situacao | Comportamento |
|---|---|---|
| Uso | analista nunca clicou em Notas nem em Link | `anotacoes.json` pode nem ser criado; saida identica a da versao 1 |
| Uso | toggle do painel desligado | `pendingAnnotations = null`; nenhuma anotacao e consultada nem exportada |
| Uso | toggle ligado mas **zero** anotacoes | pre-flight nao aparece e `pendingAnnotations = null` (nada a exportar) |
| Arquivo | `anotacoes.json` ausente | recurso desligado; sem erro; nada e criado por leitura |
| Arquivo | `anotacoes.json` corrompido | vira `.bak`, loga warning, viewer segue funcionando |
| Saida | bloco `annotations` ausente em qualquer arquivo | leitor novo trata como **desligado** |
| Saida | bloco `annotations` ausente em arquivo gerado pela v2 | houve toggle OFF ou zero anotacoes; idem |
| Legado | captura `version < 2` | toggle `Ignorar` fixo e bloqueado (§13.1) |

Invariante que garante a opcionalidade:

```java
// unica fonte da verdade do writer
if (pendingAnnotations == null) {
    // nao escreve bloco annotations, nao escreve secao no texto.txt,
    // nao injeta badge, nao cria anotacoes.json
}
```

Consequencia pratica: uma captura feita sem usar o recurso e **byte-a-byte igual** a que a
versao 1 do plugin geraria, exceto por `version: 2` e `pluginVersion` no `manifest.json`
(§5.1). Esse e o criterio de regressao R1 (§19.1).

### Incluido

- 2 itens novos no `ContextMenu` JavaFX de `HtmlLinkViewer`.
- Popup de Nota (Swing `JDialog`).
- Popup de Link (Swing `JDialog`) com lista de contatos multi-selecao.
- Acao por contato: abrir o chat do contato e capturar o trecho.
- Pre-flight (contagens + toggle) antes do `Start`.
- `anotacoes.json` na raiz da pasta de saida.
- Bloco `annotations` opcional em `manifest.json` e `whatsapp-coordinates.json`.
- Painel de anotacoes no `whatsapp.html` gerado.
- Contadores no painel e no `relatorio.htm` / `index.html` de raiz.

### Fora do escopo

- Edicao de notas ja gravadas em captura concluida (apenas re-captura).
- Busca de contatos no indice Lucene do caso (decisao §3.1).
- Sincronizacao das anotacoes com relatorio indexado do IPED.
- Notas colaborativas / multiplos analistas.

## 3. Decisoes Travadas

| # | Decisao | Escolha |
|---|---|---|
| 3.1 | Origem da lista de contatos | **Participantes do chat atual + entrada manual.** Sem consulta Lucene no caminho padrao. |
| 3.2 | Persistencia das anotacoes | **`<outputRoot>/anotacoes.json`**, schema proprio, `version: 2`. |
| 3.3 | Capturas antigas (v1) | Toggle **Ignorar** fixo e **nao editavel**. Saida identica a da versao anterior. |
| 3.4 | Item de menu inelegivel | **Visivel e desabilitado**, com tooltip explicando o motivo (nao some do menu). |
| 3.5 | Validacao | **Duas camadas**: leniente ao adicionar, estrita no `Start`. |
| 3.6 | Default do toggle | **Ligado** em capturas novas. |
| 3.7 | Link para captura de outro chat | **Referencia relativa** entre pastas da mesma raiz. Sem copiar PNG. |

---

## 4. Viabilidade - Verificado no Ambiente

Verificacoes executadas antes de definir a implementacao.

### 4.1 Rotulos vem do JAR, nao da pasta `localization/` - CONFIRMADO

`Messages.getString` usa `iped.localization.Messages.getExternalBundle()`, que cria um `URLClassLoader` sobre `iped-4.3.1/localization`. Porem `URLClassLoader` **nao sobrescreve `getResource`** com prioridade propria: a delegaçao parent-first do `ClassLoader` faz o JAR ganhar.

Probe executado com o JRE do proprio IPED (Java 11):

```text
localization dir   = C:\...\iped-4.3.1\localization
localization exist = true
loader.findResource = file:/C:/.../iped-4.3.1/localization/iped-viewer-messages_pt_BR.properties
loader.getResource  = jar:file:/C:/.../lib/iped-viewers-impl-4.3.1.jar!/iped-viewer-messages_pt_BR.properties
OK   ChatCapturePanel.Start = Start
OK   MenuClass.ChatCaptureSetStart = Adicionar mensagem inicial de captura
FAIL KEY_QUE_NAO_EXISTE -> MissingResourceException
```

Consequencias obrigatorias:

1. As labels novas vão **dentro do JAR**, na raiz do jar:
   `iped-viewer-messages.properties` e `iped-viewer-messages_pt_BR.properties`
   (fonte: `_src_IPED_4.3.1/iped-viewers/iped-viewers-impl/src/main/resources/`).
2. A copia em `iped-4.3.1/localization/` **nao precisa** ser alterada e nao tem efeito.
3. `Messages.getString` **lanca** `MissingResourceException` se a chave faltar. Toda chave nova
   precisa existir nos **dois** arquivos do jar (base e pt_BR), senao o painel quebra em
   runtime nos outros locales. **Nao usar chave nova sem cadastrar.**

### 4.2 `Messages.getString` nao e segura para texto de usuario - CONFIRMADO

Texto digitado pelo analista (nota, nome de contato) **nao** pode passar por `Messages.getString`.
Use `Messages.getString("...")` so para rotulos fixos da UI.

### 4.3 Avatar de contato disponivel em runtime - CONFIRMADO

O relatorio do chat nao tem foto por participante. Existe avatar generico em:

```text
iped/parsers/whatsapp/img/avatar.png   (909 bytes, dentro de lib/iped-parsers-impl-4.3.1.jar)
```

Carregavel por classloader (mesmo caminho que `Util.getImageResourceAsEmbedded("img/avatar.png")` usa):

```java
HtmlLinkViewer.class.getClassLoader().getResourceAsStream("iped/parsers/whatsapp/img/avatar.png")
```

### 4.4 Sem agenda de contatos no HTML do chat - CONFIRMADO

`wachat-html-template.txt` so tem `#topbar` + `#conversation`. Nao existe `#chatlist`
(esse fica no `index.html` do parser, nao no preview). Os unicos nomes disponiveis sao:

- `#topbar .left` - titulo do chat;
- `<span class="name">` - remetente em mensagem (`ReportGenerator.java:798`);
- `<span class="name_call">` - remetente em chamada (`ReportGenerator.java:385` e seguintes).

Isso sustenta a decisao 3.1.

### 4.5 ID de mensagem estavel - CONFIRMADO

`ReportGenerator.java:275`: `<div class="linha" id="{message.getUniqueId()}">`.
Usado como chave das anotacoes. Estavel entre recargas e entre o Re-Extrair do mesmo chat.

### 4.6 Thread JavaFX x Swing - RISCO REAL

`htmlViewer.setOnContextMenuRequested(...)` roda na **JavaFX Application Thread**.
Um `JDialog.setVisible(true)` modal **nessa** thread congela o WebView e impede
`executeScriptAndWait` de outras threads.

Mitigacao obligatoria: o handler do `MenuItem` apenas agenda o dialogo via
`SwingUtilities.invokeLater(...)` e retorna. Coleta de dados do DOM **antes** de abrir o dialogo.

### 4.7 Divergencia de copia do fonte - VERIFICADA

| Arquivo | `plugins/chat-captura IPED/src` | `_src_IPED_4.3.1/.../src/main/java` | SHA-256 |
|---|---|---|---|
| `HtmlLinkViewer.java` | identico | identico | iguais |
| `ChatCapturePanel.java` | identico (CRLF) | identico (LF) | so quebras de linha |

Conteudo equivalente. O `_compile_chat_capture.args` compila os caminhos do `_src_IPED_4.3.1`,
entao **a sincronizacao repo -> `_src` e passo obrigatorio do build** (§17).

### 4.8 Toolchain disponivel

- `iped-4.3.1/jre/bin/java.exe` - **Java 11** (class file ate 55.0).
- Nao existe `javac` dentro do workspace.
- Compilador disponivel: `C:\Program Files\Java\jdk-25\bin\javac.exe`.
- Obrigatorio compilar com **`--release 11`** (o padrao gera class 69.0 e o IPED recusa).
- `-source 11 -target 11` do `_compile_chat_capture.args` nao basta sem `--release`;
  usar `--release 11` no lugar.

### 4.9 Estado atual instalado

```text
lib/iped-viewers-impl-4.3.1.jar  SHA-256 = 727783D85FABB34AEFF65AEAC4902A58977D05209710DFFE842DF81D3F730EAC
```

Coincide com `CHECKSUMS.md`. Alterar esse valor a cada novo build.

---

## 5. Estrategia de Versao

### 5.1 Versoes do artefato

| Artefato | Versao atual | Nova versao | Politica |
|---|---|---|---|
| `manifest.json` | `"version": 1` | `"version": 2` | campos v1 preservados byte a byte; `annotations` e opcional |
| `whatsapp-coordinates.json` | `"version": 1` | `"version": 2` | idem |
| `capturas-index.json` | `"version": 1` | `"version": 1` | apenas campos opcionais por entrada |
| `anotacoes.json` | inexistente | `"version": 2` | schema novo, independente |

`capturas-index.json` **nao** sobe de versao: nenhum campo existente muda, so ha acrescimos.
Isso evita invalidar leitores externos do indice.

### 5.2 Regra do plugin

```java
public static final String CHAT_CAPTURE_PLUGIN_VERSION = "2.0.0";
```

Gravado em todo `manifest.json` novo:

```json
"pluginVersion": "2.0.0"
```

Serve para rastreabilidade e para detectar, ao reabrir uma pasta, com qual versao cada captura
foi produzida.

### 5.3 Regras de leitura (obrigatorias em todo leitor novo)

1. Aceitar `version >= 1`; nunca falhar por `version` desconhecida.
2. Campo ausente = valor padrao. `annotations` ausente = recurso **desligado**.
3. Campo desconhecido = ignorar.
4. `version < 2` **ou** `anotacoes.json` ausente = **captura legada** (§12).

### 5.4 Criterio de "captura legada"

```java
boolean legacyAnnotations(manifestContent, outputRoot) {
    long v = jsonLongField(manifestContent, "version", 1L);
    return v < 2L || !Files.isRegularFile(outputRoot.resolve("anotacoes.json"));
}
```

---

## 6. Modelo de Dados

### 6.1 Classes Java

```java
package iped.viewers;

class ChatAnnotation {
    String messageId = "";      // IPED uniqueId da .linha
    String noteText = "";       // vazio = sem nota
    String noteAuthor = "";
    String noteCreatedAt = "";
    String noteUpdatedAt = "";
    List<ChatContactRef> links = new ArrayList<>();
    boolean isEmpty() { return (noteText == null || noteText.isBlank()) && links.isEmpty(); }
}

class ChatContactRef {
    String contactId = "";      // id estavel do contato
    String displayName = "";
    String phone = "";          // opcional
    String origin = "chat";     // "chat" | "manual"
    String chatKey = "";        // chat do contato, para "Abrir chat deste contato"
    String chatTitle = "";
    String captureFolder = "";  // resolvido na exportacao
}

class ChatAnnotationSet {
    List<ChatAnnotation> items = new ArrayList<>();
    int notesCount() { /* ... */ }
    int linksCount() { /* ... */ }
    int contactsCount() { /* soma de links.size() */ }
}
```

### 6.2 `anotacoes.json`

Gravado em `<outputRoot>/anotacoes.json`, escrito de forma atomica
(`*.tmp` + `Files.move(..., REPLACE_EXISTING)`) para nunca corromper em queda de energia.

```json
{
  "version": 2,
  "pluginVersion": "2.0.0",
  "updatedAt": "2026-06-14T10:22:31Z",
  "chats": {
    "a1b2c3d4e5f6...": {
      "sourceHash": "a1b2c3d4e5f6...",
      "sourceName": "Conversa_WhatsApp.db",
      "sourcePath": " evidences/.../Conversa_WhatsApp.db",
      "chatTitle": "João Silva",
      "items": [
        {
          "messageId": "1234",
          "note": {
            "text": "Coordenadas conferidas com a Nota Fiscal.",
            "author": "analista",
            "createdAt": "2026-06-14T10:20:00Z",
            "updatedAt": "2026-06-14T10:20:00Z"
          },
          "links": [
            {
              "contactId": "c-8f2a",
              "displayName": "Maria Souza",
              "phone": "+5511999990000",
              "origin": "chat",
              "chatKey": "9e8d7c6b5a4f...",
              "chatTitle": "Maria Souza",
              "captureFolder": ""
            }
          ]
        }
      ]
    }
  }
}
```

`chatKey` = `sourceHash` do item-fonte do chat. E o que impede anotacao de "vazar" para outro
chat quando o analista troca de item no IPED.

---

## 7. Regra de Elegibilidade no Menu

### 7.1 JS necessario (adicionar em `installChatCaptureSupport()`, hoje em `:3166`)

```java
+ "getMessageIndex:function(id){var items=this.getOrderedMessages();for(var i=0;i<items.length;i++){if(items[i].id===id)return i;}return -1;}," //$NON-NLS-1$
+ "getMessageCount:function(){return this.getOrderedMessages().length;}," //$NON-NLS-1$
+ "getChatParticipants:function(){var out=[],seen={};var push=function(name,src){var n=this.norm(name);if(!n||seen[n]||n.length<2)return;seen[n]=1;out.push(encodeURIComponent(String(name).replace(/\\s+/g,' ').trim())+'|'+src);};var h=document.getElementById('topbar');if(h){var t=h.querySelector('.left');if(t)push.call(this,t.innerText||t.textContent||'','chat');}document.querySelectorAll(this.messageSelector).forEach(function(el){Array.prototype.forEach.call(el.querySelectorAll('.name,.name_call'),function(n){push.call(self,n.innerText||n.textContent||'','message');});},this);return out.join(';');}," //$NON-NLS-1$
```

`getChatParticipants` retorna `nomeCodificado|origem;...`. `push` e chamada com `.call(this, ...)`
porque dentro de `forEach` o `this` e o elemento.

### 7.2 Regra

```text
elegivel(M) =
    M != null
 AND !isDateCaptureElement(M)                     // "iped-capture-date-*"
 AND (captureStartMessageId != null || captureEndMessageId != null)
 AND (captureStartMessageId == null || idx(M) >  idx(start))
 AND (captureEndMessageId   == null || idx(M) <  idx(end))
```

`idx(x)` vem de `getMessageIndex(x)`; se retornar `-1`, o elemento nao esta no DOM e o item
e desabilitado.

### 7.3 Tabela de resultado

| Marcas | M acima do start | M entre start e end | M abaixo do end |
|---|---|---|---|
| so `start` | bloqueado | **liberado** | liberado |
| so `end` | liberado | **liberado** | bloqueado |
| `start` + `end` | bloqueado | **liberado** | bloqueado |

### 7.4 Tooltips

| Situacao | Chave |
|---|---|
| nao ha marca nenhuma | `MenuClass.ChatCaptureNotesDisabledNoMark` |
| mensagem acima do inicio | `MenuClass.ChatCaptureNotesDisabledAboveStart` |
| mensagem abaixo do fim | `MenuClass.ChatCaptureNotesDisabledBelowEnd` |
| elemento de data | `MenuClass.ChatCaptureNotesDisabledDate` |

### 7.5 Alteracao no `ContextMenu` (hoje em `:183-203`)

```java
MenuItem notes = new MenuItem(Messages.getString("MenuClass.ChatCaptureNotes"));
MenuItem link = new MenuItem(Messages.getString("MenuClass.ChatCaptureLink"));
AnnotationEligibility eligibility = evaluateAnnotationEligibility(captureContextMessageId);
notes.setDisable(!eligibility.eligible);
link.setDisable(!eligibility.eligible);
if (!eligibility.eligible) {
    notes.setTooltipText(Messages.getString(eligibility.reasonKey));
    link.setTooltipText(Messages.getString(eligibility.reasonKey));
}
notes.setOnAction(e -> openNoteDialogAsync(captureContextMessageId));
link.setOnAction(e -> openLinkDialogAsync(captureContextMessageId));
menu.getItems().add(isDate ? dateStart : start);
menu.getItems().addAll(end, clear);
menu.getItems().add(new SeparatorMenuItem());
menu.getItems().addAll(notes, link);
```

`SeparatorMenuItem` precisa de `import javafx.scene.control.SeparatorMenuItem;`.

---

## 8. Popup de Nota

Classe nova `ChatNoteDialog extends JDialog`.

```java
public final class ChatNoteDialog extends JDialog {
    private final JTextArea noteArea = new JTextArea(8, 46);
    private final JTextField authorField = new JTextField();
    private boolean confirmed;
    private String noteText = "";
    private String noteAuthor = "";

    public ChatNoteDialog(Window owner, String initialNote, String initialAuthor, String messagePreview) { /* ... */ }
    public boolean isConfirmed() { return confirmed; }
    public String getNoteText() { return noteText; }
    public String getNoteAuthor() { return noteAuthor; }
}
```

Requisitos:

- `JTextArea` com `lineWrap` e `wrapStyleWord`;
- campo de autor com default `System.getProperty("user.name", "")`;
- rotulo com **preview** da mensagem alvo (`blockText` do bloco), para o analista conferir contexto;
- botoes `Salvar` / `Cancelar` / `Remover nota`;
- `Remover nota` so habilitado quando ja existe nota;
- `setModal(true)` e `setLocationRelativeTo(owner)`.

Abertura segura (fora da thread JavaFX):

```java
private void openNoteDialogAsync(String messageId) {
    if (messageId == null) {
        return;
    }
    ChatAnnotation existing = annotationStore.get(messageId);
    String preview = getMessagePreviewText(messageId);
    SwingUtilities.invokeLater(() -> {
        ChatNoteDialog dialog = new ChatNoteDialog(SwingUtilities.getWindowAncestor(this), existing == null ? ""
                : existing.noteText, defaultAuthor(), preview);
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) {
            return;
        }
        String text = dialog.getNoteText().trim();
        String author = dialog.getNoteAuthor().trim();
        if (text.isEmpty()) {
            annotationStore.removeNote(messageId);
        } else {
            annotationStore.setNote(messageId, text, author);
        }
        refreshAnnotationsUi();
    });
}
```

`annotationStore.removeNote` **nao** apaga a lista de links da mesma mensagem.

---

## 9. Popup de Link (Contatos)

Classe nova `ChatContactPickerDialog extends JDialog`.

### 9.1 Layout

```text
+----------------------------------------------------------+
| Contactos desta mensagem:     [ busca... ]  [+ Adicionar]|
+---------------------------+------------------------------+
| Lista (checkbox, multi)   | Escolhidos para esta mensagem |
|  [img] Maria Souza        |  [img] Maria Souza  [x]      |
|  [img] João Lima          |  [img] João Lima    [x]      |
|  [img] +55 11 9999-0000   |                              |
+---------------------------+------------------------------+
| Origem: participantes do chat + entrada manual             |
|            [ Adicionar a mensagem ]   [ Cancelar ]       |
+----------------------------------------------------------+
```

### 9.2 Fontes de contatos

| Fonte | Origem | Marca na UI |
|---|---|---|
| Participantes do chat | `getChatParticipants()` | rotulo `ChatCaptureLink.FromChat` |
| Manual | campo de texto + `Adicionar` | rotulo `ChatCaptureLink.FromManual` |

`contactId` de contato do chat: `"c-" + sha1(nomeNormalizado).substring(0,8)`, estavel entre sessoes.
`contactId` manual: `"m-" + sha1(nomeNormalizado+"|"+telefone).substring(0,8)`.
Nome vazio **nao** entra na lista (`n.length<2` no JS ja filtra).

### 9.3 Contato ja vinculado a esta mensagem

Aparece na coluna direita e vem pre-marcado. Permite remover sem precisar desmarcar na lista.

### 9.4 Acao por contato: "Abrir chat deste contato"

Quando o contato veio do chat atual (`origin == "chat"`, ou possui `chatKey` resolvido):

```text
Clicar no contato  ->  botao "Abrir chat e capturar trecho"
  1. guarda pendingLink = { contactId, origemChatKey, origemMessageId, origemOutputRoot }
  2. attachSearcher.openItem(query, null, nomeExato)   // padrao de openRecaptureChat():580
     queries de fallback: "WhatsApp:Chat:\"<nome>\"", "WhatsApp:Party:\"<nome>\"", "name:\"<nome>\""
  3. aguarda waitUntilCaptureSourceMatches():801  (timeout 15s)
  4. falha em todas -> message dialog com a lista de queries tentadas e botao
     "Escolher item manualmente" (JFileChooser de .db / ItemSearchDialog)
  5. sucesso -> resetRecaptureMarksAndPreviewState():256
     captureStartMessageId = null; captureEndMessageId = null;
     chatCapturePanel.setStatus("ChatCapturePanel.MarkMessages")
  6. o usuario marca inicio e fim pelo menu normal (continua funcionando)
     e "Limpar pontos marcados" tambem
  7. no Start, o pre-flight mostra a linha extra:
     "Vínculo pendente: <contato> (origem: msg <id> do chat <titulo>)"
  8. ao concluir, writeManifest() grava linkedFrom e
     captureFolder do contato passa a existir
```

Sem `pendingLink` nao ha captura encadeada: o `Link` e apenas uma anotacao nominal.
O botao so aparece quando o contato tem `chatKey` resolvido.

### 9.5 Abertura segura

Mesma regra de §4.6: dados do DOM colhidos antes, dialogo aberto via
`SwingUtilities.invokeLater`, sem bloquear a thread JavaFX.

---

## 10. Pre-flight do Start

### 10.1 Regra de classificacao (camada estrita)

Para cada anotacao do chat atual, com `idxStart`, `idxEnd`, `idxMsg` do DOM:

| Condicao | Classificacao |
|---|---|
| `idxMsg == -1` (mensagem nao esta no DOM) | `orphan` |
| `idxStart <= idxMsg <= idxEnd` | `inRange` |
| demais | `outOfRange` |

`orphan` **nao** bloqueia: e apenas informativo (chat recarregado, filtro aplicado).

### 10.2 Fluxo em `startChatCapture(...)` (hoje em `:286`)

```java
public void startChatCapture(File outputFolder, String customFolderName, File sensitiveImagesFolder,
        int sensitiveSimilarityPercent, int sensitiveBlurPercent) {
    if (captureRunning) { return; }
    if (captureStartMessageId == null || captureEndMessageId == null) {
        chatCapturePanel.setStatus("ChatCapturePanel.MissingMarks");
        return;
    }
    if (outputFolder == null || outputFolder.getPath().isBlank()) {
        chatCapturePanel.setStatus("ChatCapturePanel.MissingFolder");
        return;
    }
    // Recurso opcional (§2.1): so entra no output se o toggle estiver ligado
    // E houver pelo menos UMA anotacao. Caso contrario pendingAnnotations = null
    // e a saida fica byte-a-byte igual a da versao anterior.
    if (!chatCapturePanel.isAnnotationsEnabled()) {
        pendingAnnotations = null;                 // toggle OFF
        return startCaptureBody(outputFolder, customFolderName, sensitiveImagesFolder, sensitiveSimilarityPercent, sensitiveBlurPercent);
    }
    AnnotationPreflight preflight = buildAnnotationPreflight();
    if (preflight.total() == 0) {
        pendingAnnotations = null;                 // toggle ON, mas nada anotado
        return startCaptureBody(outputFolder, customFolderName, sensitiveImagesFolder, sensitiveSimilarityPercent, sensitiveBlurPercent);
    }
    if (!chatCapturePanel.isConfirmPreflight(preflight)) {
        chatCapturePanel.setStatus("ChatCapturePanel.StartCancelled");
        return;                                     // Cancelar: nada e descartado
    }
    pendingAnnotations = preflight.toAnnotationSet();
    startCaptureBody(outputFolder, customFolderName, sensitiveImagesFolder, sensitiveSimilarityPercent, sensitiveBlurPercent);
}

private void startCaptureBody(File outputFolder, String customFolderName, File sensitiveImagesFolder,
        int sensitiveSimilarityPercent, int sensitiveBlurPercent) {
    // ... remainder atual de startChatCapture, identico ao de hoje
}
```

Os tres `return` evitam duplicar o corpo e garantem que `pendingAnnotations` valha `null`
sempre que nao houver anotacao a exportar — inclusive no caso **toggle ligado e zero
anotacoes**, que sem esse cuidado gravaria um bloco `annotations` com contagens zeradas e
quebraria a equivalencia de §13.2.

`buildAnnotationPreflight()` deve rodar fora da EDT. Como `startChatCapture` e chamado do botao
Swing (EDT) e precisa de `executeScriptAndWait`, envolver em
`SwingUtilities.invokeLater(() -> new Thread(...).start())` e usar `CountDownLatch`,
igual a `captureImage()` (`:2411`) faz.

### 10.3 Layout do dialogo

```text
+--------------------------------------------------------------+
| Anotações e Links desta captura                               |
|                                                               |
| Notas nesta captura .................................... 5    |
| Mensagens com link ..................................... 3    |
| Total de contatos vinculados ........................ 8    |
| ------------------------------------------------------------ |
| Dentro do intervalo (msg 4 -> msg 12) ................ 6     |
| Fora do intervalo - serão ignoradas .................. 2     |
| Sem correspondência no chat atual .................... 0     |
| [ lista rolavel com as 2 fora, mostrando msg e resumo ]      |
| ------------------------------------------------------------ |
| [x] Incluir Notas e Links nesta captura                      |
|                                                               |
|        Iniciar captura                 Cancelar               |
+--------------------------------------------------------------+
```

Regras:

- **Sempre** mostrar as contagens, mesmo com toggle OFF (o analista audita o que existe).
- Houver `outOfRange > 0` e toggle ON: texto do titulo em vermelho
  (`ChatCapturePreflight.WarnOutOfRange`), botao `Iniciar captura` continua sendo o de confirmar.
- `Iniciar captura` -> descarta as fora e segue (requisito explicito).
- `Cancelar` -> nada muda, nenhuma anotacao e descartada.
- Sem anotacao nenhuma e toggle ON -> **nao mostra o dialogo** e `pendingAnnotations` fica `null`.
- `pendingLink` (§9.4) acrescenta uma linha informativa no dialogo.

### 10.4 Toggle no painel

Em `ChatCapturePanel`:

```java
private final JCheckBox annotationsToggle = new JCheckBox(Messages.getString("ChatCapturePanel.IncludeAnnotations"), true);
private final JLabel notesCounter = new JLabel("0");
private final JLabel linksCounter = new JLabel("0");
private final JLabel outOfRangeCounter = new JLabel("0");

public boolean isAnnotationsEnabled() { return annotationsToggle.isSelected(); }
public void updateAnnotationCounters(int notes, int links, int outOfRange) { /* ... */ }
```

Layout: mesma `GridLayout(4,1,...)` de `options` passa para `GridLayout(5,1,...)`
(§18 lista o redesenho). `annotationsToggle` fica **acima** do botao `Start`.

O toggle e persistido na sessao do viewer (nao em arquivo). Default `true`.
Durante captura e Re-Extrair em andamento, `annotationsToggle.setEnabled(false)`.

---

## 11. Exportacao

### 11.1 Estado transiente

```java
private final ChatAnnotationStore annotationStore = new ChatAnnotationStore();
private volatile ChatAnnotationSet pendingAnnotations;   // preenchido no pre-flight
```

`pendingAnnotations` e o unico que chega nos writers. `annotationStore` e a fonte da verdade
das anotacoes e nao vaza para a saida sem passar pelo pre-flight.

### 11.2 `whatsapp-coordinates.json`

Adicionar antes de `"frames"` (linha `2290`):

```json
"annotations": {
  "version": 2,
  "enabled": true,
  "notOriginalEvidence": true,
  "authoredBy": "analista",
  "generatedAt": "2026-06-14T10:25:00Z",
  "toggleState": "included",
  "counts": { "notes": 5, "annotatedMessages": 8, "contacts": 8, "inRange": 6, "ignoredOutOfRange": 2, "orphans": 0 },
  "items": [
    {
      "messageId": "1234",
      "date": "2026-05-25 14:30:00",
      "text": "texto da mensagem para contexto",
      "note": { "text": "...", "author": "analista", "createdAt": "...", "updatedAt": "..." },
      "links": [
        {
          "contactId": "c-8f2a1b3c",
          "displayName": "Maria Souza",
          "phone": "+5511999990000",
          "chatKey": "9e8d7c6b...",
          "chatTitle": "Maria Souza",
          "captureFolder": "captura-chat-20260614-101500",
          "hasCapture": true,
          "frames": ["screenshots/frame_0001.png", "screenshots/frame_0002.png"],
          "excerptFile": "texto.txt",
          "linkedFrom": { "chatKey": "a1b2c3...", "messageId": "1200", "contactId": "c-8f2a1b3c" }
        }
      ]
    }
  ]
}
```

`captureFolder` e `frames` sao resolvidos **na exportacao**, varrendo `capturas-index.json`
da raiz por `chatKey`/`chatTitle`. Como as capturas de um contato sao gravadas em outra pasta
da mesma raiz, a referencia e `../<captureFolder>/screenshots/frame_0001.png` - relativo,
portanto funciona em `file://` e nao duplica bytes.

Se nao houver captura para o contato:

```json
"hasCapture": false
```

### 11.3 `manifest.json`

```java
out.println("  \"version\": 2,");
out.println("  \"pluginVersion\": " + json(CHAT_CAPTURE_PLUGIN_VERSION) + ",");
```

e antes de `"frames"` (linha `2093`):

```json
"annotations": { ... igual ao bloco 11.2 ... },
"linkedFrom": { "chatKey": "...", "messageId": "1200", "contactId": "c-8f2a1b3c" }
```

`linkedFrom` so quando `pendingLink != null` (§9.4).

### 11.4 `texto.txt`

Acrescentar secao, escrita por `buildCaptureText()` (`:2235`), somente quando
`pendingAnnotations != null`:

```text
ANOTACOES DO ANALISTA
Nota: 2 | Links: 3 (4 contatos) | Fora do intervalo ignoradas: 1

- [msg 1234 | 2026-05-25 14:30:00] NOTA (analista): Coordenadas conferidas...
    LINK: Maria Souza -> captura-chat-20260614-101500
    LINK: Joao Lima -> sem captura vinculada
```

### 11.5 `whatsapp.html` - painel de anotacoes

O `writeWhatsAppHtml()` (`:2400`) ja tem `esc()` e abre `side`/`modal`.
Adicionar ao `<style>`:

```css
#side .an{font-size:13px;line-height:1.5;max-width:100%;overflow-wrap:anywhere}
#side .an-note{background:#fff8e1;border-left:4px solid #f9a825;padding:8px 10px;margin:8px 0;white-space:pre-wrap}
#side .an-link{display:flex;align-items:center;gap:10px;padding:8px 4px;border-top:1px solid #eee;cursor:pointer}
#side .an-link:hover{background:#f2f7f6}
#side .an-link img{width:36px;height:36px;border-radius:50%;object-fit:cover}
#side .an-excerpt{margin-top:10px;border-top:1px solid #ddd;padding-top:10px}
#side .an-excerpt img{width:100%;border:1px solid #ddd;margin-bottom:8px;cursor:zoom-in}
#side .an-empty{color:#777;font-size:13px;margin:8px 0}
.hotspot.has-note{box-shadow:inset 0 0 0 2px #f9a825}
.hotspot.has-link{box-shadow:inset 0 0 0 2px #25d366}
```

Ao criar cada hotspot, aplicar a classe conforme `annotationsByMessage`:

```javascript
var ANN = (DATA.annotations && DATA.annotations.enabled) ? DATA.annotations : null;
var ANN_BY_MSG = {};
if (ANN) { (ANN.items||[]).forEach(function(a){ ANN_BY_MSG[a.messageId] = a; }); }
```

### 11.6 `relatorio.htm` e `index.html` de raiz

Mostrar contagens **somente quando `> 0`**, para capturas antigas ficarem identicas:

```java
if (entry.notesCount > 0 || entry.linksCount > 0) {
    // bloco de metadados
}
```

Campos novos em `CaptureRootEntry` (`:3362`):

```java
int notesCount;
int linksCount;
int contactsCount;
boolean annotationsEnabled;
boolean legacyAnnotations;
```

Popular em `readCaptureRootEntry()` (`:1091`) a partir do `manifest.json` da pasta.

---

## 12. Clique no HTML Gerado

### 12.1 Roteamento no handler de clique do hotspot

```
clique no hotspot
  |
  +-- ANN nao existe ou enabled === false
  |     -> comportamento atual (showText / showFile / openModal)
  |
  +-- ANN existe
        -> marca visual: .has-note e/ou .has-link
        -> openSide( renderAnnotation(ANN_BY_MSG[blockId]) )
```

### 12.2 `renderAnnotation`

```javascript
function renderAnnotation(a){
  var h='<h2>Anotação do analista</h2>';
  if(a.date){ h+='<p class="muted">Mensagem em '+esc(a.date)+'</p>'; }
  if(a.note){ h+='<div class="an-note"><b>Nota</b> ('+esc(a.note.author||'analista')+'):<br>'+esc(a.note.text)+'</div>'; }
  var links=a.links||[];
  if(!links.length){ if(!a.note){ return h+'<div class="an-empty">Sem nota e sem link.</div>'; } return h+'</div>'; }
  h+='<div style="margin-top:12px"><b>'+(links.length===1?'Contato vinculado':'Contatos vinculados')+'</b></div>';
  if(links.length===1){
    return h+renderLinkRow(links[0])+renderExcerpt(links[0]);
  }
  links.forEach(function(l){ h+=renderLinkRow(l); });
  return h+'<div id="anExcerpt"></div>';
}
```

### 12.3 Regra do contato unico (requisito explicito)

`links.length === 1` -> **abre direto o trecho capturado**, sem lista, sem clique intermediario.

### 12.4 Trecho capturado

```javascript
function renderExcerpt(l){
  if(!l.hasCapture){ return '<div class="an-empty">Sem captura vinculada para este contato.</div>'; }
  var base='../'+l.captureFolder+'/';
  var s='<div class="an-excerpt"><b>Trecho capturado em '+esc(l.chatTitle||'')+'</b>';
  (l.frames||[]).forEach(function(f){ s+='<img src="'+esc(base+f)+'" onclick="openModal(\'<img src=&quot;'+esc(base+f)+'&quot;&gt;\')">'; });
  s+='</div>';
  return s;
}
```

Se `l.frames` vier vazio mas `hasCapture` for true, carregar `base + l.excerptFile` e
exibir o texto em `<pre class="an">` (fallback).

### 12.5 Notas e links visiveis no viewer ao vivo

Badges pequenos dentro da mensagem no IPED, para o analista reencontrar o que anotou:

```java
+ "setAnnotationBadges:function(list){var self=this;var enc=encodeURIComponent;document.querySelectorAll('.iped-annotation-badge').forEach(function(b){b.parentNode.removeChild(b);});(list||[]).forEach(function(row){var p=row.split('|');if(p.length<3)return;var el=document.getElementById(decodeURIComponent(p[0]));if(!el)return;var b=document.createElement('span');b.className='iped-annotation-badge';b.textContent=(p[1]==='note'?'📝 ':'')+(p[2]==='link'?'🔗':'');b.style.cssText='float:right;font-size:11px;opacity:.85;cursor:pointer';el.appendChild(b);});}," //$NON-NLS-1$
+ "clearAnnotationBadges:function(){document.querySelectorAll('.iped-annotation-badge').forEach(function(b){b.parentNode.removeChild(b);});}," //$NON-NLS-1$
```

Chamado por `refreshAnnotationsUi()` apos cada anotacao, apenas para as anotacoes do chat atual.
Os badges sao `display:none` durante a captura (mesma regra de `.iped-capture-hide-marks`).

---

## 13. Compatibilidade com Capturas Antigas

| Cenario | Deteccao | Comportamento |
|---|---|---|
| Captura v1 em disco | `manifest.version < 2` | `legacyAnnotations = true`; toggle `Ignorar`; nenhum bloco `annotations` gravado |
| Captura v2 sem `anotacoes.json` | arquivo ausente | `legacyAnnotations = true` (captura feita com toggle OFF) |
| Pasta nova, toggle OFF | `anotacoes.json` sem entrada do chat | saida byte-a-byte igual a de hoje |
| `capturas-index.json` v1 | campos ausentes | `notesCount=0`, `linksCount=0`, `legacyAnnotations=true` |
| `whatsapp.html` v1 | `DATA.annotations` ausente | JS cai no fluxo atual |
| Leitor v1 do nosso `manifest.json` v2 | campos a mais | ignora campos desconhecidos; `version` so aparece em log |

### 13.1 Toggle no Re-Extrair

Em `showRecaptureDialog()` (`:322`), acrescentar ao painel do dialogo:

```java
JCheckBox annToggle = new JCheckBox(Messages.getString("ChatCapturePanel.IncludeAnnotations"));
boolean anyLegacy = jobs.stream().anyMatch(j -> j.legacyAnnotations);
boolean allLegacy = jobs.stream().allMatch(j -> j.legacyAnnotations);
if (allLegacy) {
    annToggle.setSelected(false);
    annToggle.setEnabled(false);
    annToggle.setToolTipText(Messages.getString("ChatCapturePanel.LegacyAnnotationsLocked"));
}
```

- Todos legados -> fixo em "Ignorar", **bloqueado** (decisao 3.3).
- Mistura -> habilitado, default ligado, e a lista de checkboxes mostra `sem anotacoes` nas legadas.
- Selecionar apenas legadas -> toggle e forcado a OFF no momento do `startRecaptureBatch`.

`RecaptureJob` ganha `boolean legacyAnnotations;`, populado em `loadRecaptureJobs()` (`:383`)
com `jsonLongField(content, "version", 1L) < 2L`.

### 13.2 Garantia de equivalencia com toggle OFF

Com `pendingAnnotations == null`:
- nenhum bloco `annotations` e escrito;
- nenhum badge e injetado;
- `texto.txt` nao ganha a secao;
- `whatsapp.html` recebe apenas `var ANN = null`;
- `capturas-index.json` recebe contagens zeradas.

Nenhuma alteracao de linha de codigo no caminho de captura e necessaria. Esse e o criterio de
regressao numero 1 (§19).

---

## 14. Novas Rotulos

**Arquivos dentro do JAR** (raiz), fonte:
`_src_IPED_4.3.1/iped-viewers/iped-viewers-impl/src/main/resources/`

### `iped-viewer-messages.properties`

```properties
MenuClass.ChatCaptureNotes=Add note to this message
MenuClass.ChatCaptureLink=Link contacts to this message
MenuClass.ChatCaptureNotesDisabledNoMark=Mark the start or end point first.
MenuClass.ChatCaptureNotesDisabledAboveStart=Message is above the marked start point.
MenuClass.ChatCaptureNotesDisabledBelowEnd=Message is below the marked end point.
MenuClass.ChatCaptureNotesDisabledDate=Date separators do not accept notes or links.
ChatCapturePanel.IncludeAnnotations=Include Notes and Links in this capture
ChatCapturePanel.Notes=Notes:
ChatCapturePanel.Links=Links:
ChatCapturePanel.OutOfRange=Out of range:
ChatCapturePanel.LegacyAnnotationsLocked=This capture has no notes/links feature (legacy v1). Locked to Ignore.
ChatCapturePanel.StartCancelled=Start cancelled.
ChatCapturePanel.NotesMarked=Note saved for this message.
ChatCapturePanel.NoteRemoved=Note removed from this message.
ChatCapturePanel.LinksMarked=Contacts linked to this message.
ChatCapturePanel.LinksRemoved=Links removed from this message.
ChatNoteDialog.Title=Note
ChatNoteDialog.Message=Note text
ChatNoteDialog.Author=Author
ChatNoteDialog.Preview=Message
ChatNoteDialog.Save=Save
ChatNoteDialog.Cancel=Cancel
ChatNoteDialog.RemoveNote=Remove note
ChatCaptureLink.Title=Link contacts
ChatCaptureLink.Available=Available contacts
ChatCaptureLink.Search=Search contact
ChatCaptureLink.Add=Add
ChatCaptureLink.Selected=Linked to this message
ChatCaptureLink.ClearAll=Remove all
ChatCaptureLink.FromChat=From this chat
ChatCaptureLink.FromManual=Added manually
ChatCaptureLink.AddToMessage=Link to message
ChatCaptureLink.OpenChat=Open chat and capture excerpt
ChatCaptureLink.NoContacts=No contacts found in this chat. Type a name below to add one.
ChatCaptureLink.OpenFailed=Could not open the chat. Try another contact or pick the file manually.
ChatCaptureLink.ChooseManually=Choose file manually
ChatCaptureLink.PendingLink=Pending link:
ChatCapturePreflight.Title=Notes and Links in this capture
ChatCapturePreflight.NotesCount=Notes in this capture
ChatCapturePreflight.LinksCount=Messages with link
ChatCapturePreflight.ContactsCount=Total linked contacts
ChatCapturePreflight.InRange=Inside the interval
ChatCapturePreflight.OutOfRange=Outside the interval - will be ignored
ChatCapturePreflight.Orphans=No match in the current chat
ChatCapturePreflight.WarnOutOfRange=Some notes/links are outside the interval. Confirming Start ignores them.
ChatCapturePreflight.Start=Start capture
ChatCapturePreflight.Cancel=Cancel
```

### `iped-viewer-messages_pt_BR.properties`

```properties
MenuClass.ChatCaptureNotes=Adicionar nota nesta mensagem
MenuClass.ChatCaptureLink=Vincular contatos a esta mensagem
MenuClass.ChatCaptureNotesDisabledNoMark=Marque primeiro o início ou o fim da captura.
MenuClass.ChatCaptureNotesDisabledAboveStart=A mensagem está acima do início marcado.
MenuClass.ChatCaptureNotesDisabledBelowEnd=A mensagem está abaixo do fim marcado.
MenuClass.ChatCaptureNotesDisabledDate=Separadores de data não aceitam nota nem link.
ChatCapturePanel.IncludeAnnotations=Incluir Notas e Links nesta captura
ChatCapturePanel.Notes=Notas:
ChatCapturePanel.Links=Links:
ChatCapturePanel.OutOfRange=Fora:
ChatCapturePanel.LegacyAnnotationsLocked=Esta captura é da versão anterior (sem notas/links). Fixado em Ignorar.
ChatCapturePanel.StartCancelled=Início cancelado.
ChatCapturePanel.NotesMarked=Nota salva nesta mensagem.
ChatCapturePanel.NoteRemoved=Nota removida desta mensagem.
ChatCapturePanel.LinksMarked=Contatos vinculados a esta mensagem.
ChatCapturePanel.LinksRemoved=Vínculos removidos desta mensagem.
ChatNoteDialog.Title=Nota
ChatNoteDialog.Message=Texto da nota
ChatNoteDialog.Author=Autor
ChatNoteDialog.Preview=Mensagem
ChatNoteDialog.Save=Salvar
ChatNoteDialog.Cancel=Cancelar
ChatNoteDialog.RemoveNote=Remover nota
ChatCaptureLink.Title=Vincular contatos
ChatCaptureLink.Available=Contatos disponíveis
ChatCaptureLink.Search=Buscar contato
ChatCaptureLink.Add=Adicionar
ChatCaptureLink.Selected=Vinculados a esta mensagem
ChatCaptureLink.ClearAll=Remover todos
ChatCaptureLink.FromChat=Deste chat
ChatCaptureLink.FromManual=Adicionado manualmente
ChatCaptureLink.AddToMessage=Vincular a mensagem
ChatCaptureLink.OpenChat=Abrir chat e capturar trecho
ChatCaptureLink.NoContacts=Nenhum contato encontrado neste chat. Digite um nome abaixo para adicionar.
ChatCaptureLink.OpenFailed=Não foi possível abrir o chat. Tente outro contato ou escolha o arquivo manualmente.
ChatCaptureLink.ChooseManually=Escolher arquivo manualmente
ChatCaptureLink.PendingLink=Vínculo pendente:
ChatCapturePreflight.Title=Notas e Links desta captura
ChatCapturePreflight.NotesCount=Notas nesta captura
ChatCapturePreflight.LinksCount=Mensagens com link
ChatCapturePreflight.ContactsCount=Total de contatos vinculados
ChatCapturePreflight.InRange=Dentro do intervalo
ChatCapturePreflight.OutOfRange=Fora do intervalo - serão ignoradas
ChatCapturePreflight.Orphans=Sem correspondência no chat atual
ChatCapturePreflight.WarnOutOfRange=Há notas/links fora do intervalo. Confirmar o Start vai ignorá-los.
ChatCapturePreflight.Start=Iniciar captura
ChatCapturePreflight.Cancel=Cancelar
```

**Obrigatorio**: as duas listas precisam ter exatamente as mesmas chaves. Validar antes de empacotar:

```powershell
$base = Get-Content 'iped-viewer-messages.properties' | Where-Object { $_ -match '^\w' } | ForEach-Object { ($_ -split '=')[0] }
$pt   = Get-Content 'iped-viewer-messages_pt_BR.properties' | Where-Object { $_ -match '^\w' } | ForEach-Object { ($_ -split '=')[0] }
Compare-Object $base $pt
```

Saida vazia = OK.

---

## 15. Arquivos

### 15.1 Novos

| Arquivo | Responsabilidade |
|---|---|
| `src/iped/viewers/ChatAnnotation.java` | modelo de anotacao |
| `src/iped/viewers/ChatContactRef.java` | modelo de contato vinculado |
| `src/iped/viewers/ChatAnnotationSet.java` | colecao + contadores |
| `src/iped/viewers/ChatAnnotationStore.java` | estado em memoria + `anotacoes.json` + `chatKey` |
| `src/iped/viewers/AnnotationEligibility.java` | resultado da regra §7.2 |
| `src/iped/viewers/AnnotationPreflight.java` | resultado da classificacao §10.1 |
| `src/iped/viewers/ChatNoteDialog.java` | popup de nota |
| `src/iped/viewers/ChatContactPickerDialog.java` | popup de link |
| `src/iped/viewers/ChatPreflightDialog.java` | popup de confirmacao do Start |

### 15.2 Alterados

| Arquivo | Pontos de edicao (linhas do arquivo de 3646) |
|---|---|
| `HtmlLinkViewer.java` | campo `CHAT_CAPTURE_PLUGIN_VERSION` (~`:110`); `annotationStore` (~`:132`); `ContextMenu` (`:183-203`); `startChatCapture` (`:286-308`); `showRecaptureDialog` (`:322-381`); `loadRecaptureJobs` (`:383`); `readCaptureRootEntry` (`:1091`); `runChatCaptureInternal` (`:1016-1026`); `buildCaptureText` (`:2235`); `buildWhatsAppCoordinatesJson` (`:2278`); `writeWhatsAppHtml` (`:2400`); `writeManifest` (`:2082`); `writeCaptureRootIndexJson` (`:1638`); `writeCaptureRootIndexHtml` (`:1678`); `writeCaptureRootReport` (`:1704`); `writeInteractiveCaptureReport` (`:1919`); `installChatCaptureSupport` (`:3166`); `CaptureRootEntry` (`:3362`); `RecaptureJob` (`:3453`); imports |
| `ChatCapturePanel.java` | `annotationsToggle` + 3 contadores (`:27-42`); `options` `GridLayout` 4 -> 5 (`:79`); metodos `isAnnotationsEnabled`, `isConfirmPreflight`, `updateAnnotationCounters`; `setCaptureRunning` (`:176`) desabilita o toggle |
| `iped-viewer-messages.properties` | +46 chaves |
| `iped-viewer-messages_pt_BR.properties` | +46 chaves |

---

## 16. Ordem de Implementacao

| # | Passo | Entrega | Verificacao |
|---|---|---|---|
| 1 | `getMessageIndex`, `getMessageCount`, `getChatParticipants` no JS | `HtmlLinkViewer` | console do WebView devolve indice correto |
| 2 | `ChatAnnotation`, `ChatContactRef`, `ChatAnnotationSet` | modelos | compila |
| 3 | `ChatAnnotationStore` com `anotacoes.json` | persistencia | grava, relê, arquivo atomico |
| 4 | `AnnotationEligibility` + `ContextMenu` com 2 itens desabilitados | menu | item existe e tooltip varia |
| 5 | `ChatNoteDialog` + `openNoteDialogAsync` | nota | nota salva, badge aparece, FX nao congela |
| 6 | `ChatContactPickerDialog` + `openLinkDialogAsync` | link | 1 e N contatos, remover, manual |
| 5 | `AnnotationPreflight` + `ChatPreflightDialog` + gate do `Start` | pre-flight | contagens batem com o store; zero anotacao -> sem popup e `pendingAnnotations == null` |
| 8 | toggle e contadores no painel | UI | toggle OFF nao chama pre-flight |
| 9 | badges no viewer ao vivo | feedback | `refreshAnnotationsUi()` |
| 10 | export `annotations` em coordinates + manifest | dados | JSON valido, `version: 2` |
| 11 | `renderAnnotation` no `whatsapp.html` | visual | 1 contato abre direto; 2+ listam |
| 12 | `linkedFrom` + `captureFolder` resolvido | captura encadeada | link clica e mostra os frames |
| 13 | `legacyAnnotations` + lock no Re-Extrair | legado | pasta v1 nao muda |
| 14 | contadores no `relatorio.htm` e `index.html` | relatorio | pasta antiga identica |
| 15 | `texto.txt` com a secao | texto | so quando `pendingAnnotations != null` |

---

## 17. Build e Instalacao

### 17.1 Sincronizar fonte

```powershell
$repo  = 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\plugins\chat-captura IPED\src\iped\viewers'
$build = 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\iped-4.3.1\_src_IPED_4.3.1\iped-viewers\iped-viewers-impl\src\main\java\iped\viewers'
Copy-Item "$repo\*.java" $build -Force
```

Obrigatorio: o `_compile_chat_capture.args` compila os caminhos do `_src_IPED_4.3.1` (§4.7).

### 17.2 Compilar

```powershell
$iped = 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\iped-4.3.1'
$javac = 'C:\Program Files\Java\jdk-25\bin\javac.exe'
$args = Get-Content "$iped\_compile_chat_capture.args"
# trocar -source 11 / -target 11 por --release 11
```

Aplicar a alteracao em `_compile_chat_capture.args`:

```text
--release
11
```

no lugar do bloco:

```text
-source
11
-target
11
```

Razão: o JRE do IPED é Java 11 e não aceita class file 69.0 (§4.8).

### 17.3 Empacotar

Copiar os `.class` novos para `target\classes` e reconstruir o jar:

```powershell
$src = "$iped\_src_IPED_4.3.1\iped-viewers\iped-viewers-impl\target\classes"
$orig = 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\plugins\instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar'
$out  = 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\plugins\chat-captura IPED\instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar'
Copy-Item "$orig" "$out" -Force
& "$iped\jre\bin\jar.exe" uf "$out" -C $src iped/viewers/ChatAnnotation.class ... 
```

Ordem real sugerida:

1. `javac --release 11` -> `target\classes` (§17.2);
2. copiar os properties de `src\main\resources` para `target\classes`;
3. `jar uf <jar> -C target\classes iped/viewers -C target\classes iped-viewer-messages.properties -C target\classes iped-viewer-messages_pt_BR.properties`;
4. conferir que as classes novas estao no jar;
5. `FEche o IPED` antes de instalar.

### 17.4 Checar o jar

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z=[System.IO.Compression.ZipFile]::OpenRead($out)
$z.Entries | Where-Object { $_.FullName -match 'Chat(Annotation|Contact|Note|Preflight)' } | Select-Object FullName,Length
$z.Dispose()
```

Esperado: `ChatAnnotationStore`, `ChatContactRef`, `ChatNoteDialog`,
`ChatContactPickerDialog`, `ChatPreflightDialog`, `AnnotationPreflight`, `AnnotationEligibility`.

### 17.5 Atualizar CHECKSUMS.md

```powershell
(Get-FileHash -LiteralPath $out -Algorithm SHA256).Hash
```

### 17.6 Instalar

```powershell
& 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\plugins\instalar_captura_chat_zap\instalar_captura_chat_zap.bat'
```

Backup automatico em `lib\iped-viewers-impl-4.3.1.jar.bak-YYYYMMDD-HHMMSS`.

### 17.7 README

Atualizar a lista "O que este plugin altera" e "Como usar no IPED".

---

## 18. Wireframe do Painel

```
+-- Captura de Chat ---------------------------------+---+
| Pasta de destino                        [ Selecionar] |
| [x] Nome custom                                       |
|       [ organizacao_nome            ]                 |
| Pasta imagens blur                        [ ... ]     |
|       [                                        ]      |
| Similaridade blur            [ 90% v ]                 |
| Blur censura                 [ 90% v ]                 |
| [x] Incluir Notas e Links nesta captura   <- NOVO      |
|                                                      |
| Imagens: 0     Arquivos: 0   Notas: 0  Links: 0 <-NOVO|
|                                                      |
| [  Start  ] [ Re-Extrair ]                            |
| Marque a mensagem inicial e final.                    |
+------------------------------------------------------+
```

`preferences`/`counters` precisam de altura extra: `setPreferredSize` de
`(260, 250)` (`:48`) passa para `(280, 300)`.

---

## 19. Testes

### 19.1 Regressao (prioridade maxima - o toggle OFF tem de ser invisivel)

| # | Teste | Esperado |
|---|---|---|
| R1 | Captura completa com toggle OFF | `manifest.json` **sem** bloco `annotations`; `version: 2`; `pluginVersion: 2.0.0`; `capturas-index.json` com contagens 0; `whatsapp.html` sem painel |
| R1b | Captura completa com toggle ON e nenhuma anotacao usada | identico a R1; nenhum `anotacoes.json` criado |
| R2 | Preview de imagem / PDF / texto | sem regressao (o `ContextMenu` so muda no `HtmlLinkViewer`) |
| R3 | Chat aberto sem usar captura | menu igual ao atual + 2 itens desabilitados |
| R4 | Screenshot atual do viewer | sem regressao |
| R5 | Exportacao normal do IPED | sem regressao |

### 19.2 Regra de elegibilidade

| # | Cenario | Esperado |
|---|---|---|
| E1 | Sem marca nenhuma, clique na msg 4 | Notas e Link desabilitados, tooltip "Marque primeiro..." |
| E2 | `start` na msg 4, clique na msg 2 | desabilitado, "acima do inicio" |
| E3 | `start` na msg 4, clique na msg 9 | **habilitado** |
| E4 | `start` 4 e `end` 12, clique na msg 20 | desabilitado, "abaixo do fim" |
| E5 | `end` na msg 12, clique na msg 2 | habilitado |
| E6 | clique em separador de data | desabilitado |
| E7 | clique fora de qualquer `.linha` | desabilitado |

### 19.3 Pre-flight (caso do enunciado)

| # | Cenario | Esperado |
|---|---|---|
| P1 | `start` na 4, nota na 9, `end` na 3, Start | popup: Dentro 0 / Fora 1; confirmar -> captura sem a nota; `counts.ignoredOutOfRange: 1` |
| P2 | mesmo, mas Cancelar | nada muda, anotacao continua no `anotacoes.json` |
| P3 | 3 notas dentro + 2 fora | popup: Dentro 3 / Fora 2; confirmar -> `annotations.items` com 3 |
| P4 | anotacao em msg que sumiu do DOM | contada em `orphans`, nao bloqueia |
| P5 | nenhuma anotacao, toggle ON | Start direto, **sem popup**, `pendingAnnotations == null`, nenhum bloco `annotations` escrito |
| P6 | 3 notas, toggle OFF | popup **nao** aparece, contagem ignorada |
| P7 | anotao + `pendingLink` | linha extra "Vinculo pendente" |
| P8 | toggle ON, 3 notas, todas `orphan` (chat recarregado) | popup mostra `orphans: 3`; confirmar -> `pendingAnnotations` nao nulo, `items` vazio, bloco escrito com `counts` zerado mas `ignoredOutOfRange: 0` |

### 19.4 Nota

| # | Cenario | Esperado |
|---|---|---|
| N1 | Salvar nota | badge no viewer, contador +1, persiste |
| N2 | Editar nota | substitui, `updatedAt` muda, `createdAt` mantem |
| N3 | "Remover nota" | some a nota, **links permanecem** |
| N4 | Texto com `<script>` e aspas | aparece literal no dialogo e no HTML |
| N5 | Texto com 5000 caracteres | nao quebra o dialogo nem o JSON |

### 19.5 Link

| # | Cenario | Esperado |
|---|---|---|
| L1 | Grupo com 4 remetentes | dialogo lista 4 + titulo do chat, deduplicado |
| L2 | Chat 1:1 | lista ao menos o titulo |
| L3 | Selecionar 3 | 3 na coluna direita, contador `Links: 3`, `contacts: 3` |
| L4 | Adicionar manual "Carlos" | aparece com `origin: manual` |
| L5 | Remover 1 da lista e readicionar | nao duplica |
| L6 | Nome duplicado com acento diferente | `contactId` estavel pelo nome normalizado |

### 19.6 Captura encadeada

| # | Cenario | Esperado |
|---|---|---|
| C1 | Link em "Maria" -> "Abrir chat" -> marca 2 pontos -> Start | pasta nova criada; `manifest.json` da origem ganha `captureFolder` |
| C2 | Captura da origem aberta depois | 1 contato -> trecho direto, sem lista |
| C3 | 2+ contatos | lista; clique no contato mostra os frames dele |
| C4 | Contato sem captura | "Sem captura vinculada para este contato" |
| C5 | `openItem` falha em todas as queries | dialogo de erro + "Escolher arquivo manualmente" |
| C6 | "Limpar pontos marcados" durante o fluxo encadeado | marks vao para null, `pendingLink` mantido |

### 19.7 Legado

| # | Cenario | Esperado |
|---|---|---|
| G1 | `capturas-index.json` de pasta v1 + `Re-Extrair` | toggle fixo em Ignorar e bloqueado |
| G2 | Re-Extrair de pasta v2 | toggle habilitado, default ligado |
| G3 | Re-Extrair com mistura (v1 + v2) | habilitado, listas marcadas |
| G4 | Selecionar so legadas | `pendingAnnotations = null` no batch |
| G5 | `whatsapp.html` v1 aberto no navegador | funciona, sem painel de anotacao |
| G6 | `manifest.json` v2 lido por leitor que ignora campos novos | continua valido |

### 19.8 Robustez

| # | Cenario | Esperado |
|---|---|---|
| T1 | Trocar de item durante captura encadeada | `pendingLink` invalidado, aviso |
| T2 | Chat muito longo com 5000 mensagens | `getMessageIndex` e `getChatParticipants` sem travar |
| T3 | Janela pequena / tela cheia | painel e dialogs cabem |
| T4 | `anotacoes.json` corrompido | nao quebra o viewer; renomeia para `.bak` e loga |
| T5 | `anotacoes.json` de outro chat no `anotacoes.json` atual | preservado ao gravar |

---

## 20. Riscos

| Risco | Impacto | Mitigacao |
|---|---|---|
| Congelar a thread JavaFX com `JDialog` modal | viewer travado | `SwingUtilities.invokeLater` (§4.6), dados colhidos antes |
| Chave de rotulo faltando | `MissingResourceException` no painel | validar §14 antes de empacotar; adicionar nos **dois** arquivos |
| Texto do analista no HTML | injecao de script | `esc()` obrigatorio em **todo** campo; ja existe no `whatsapp.html` |
| `openItem` sem resultado para contato | link sem trecho | fallback de 3 queries + escolha manual (§9.4) |
| Trocar de chat losing anotacoes | perda de trabalho | `chatKey`; `anotacoes.json` persistido a cada edicao |
| Anotacao contada como evidencia | problema forense | `notOriginalEvidence: true`, `authoredBy`, secao isolada no `texto.txt` |
| `pendingAnnotations` vazando entre capturas | anotacao de uma captura em outra | `finally { pendingAnnotations = null; }` em `runChatCaptureInternal` |
| Duplicar PNG na captura encadeada | explosao de disco | referencia relativa, sem copia (§3.7) |
| Breaking em leitor externo | perda de compatibilidade | `annotations` sempre opcional; `capturas-index.json` mantem `version: 1` (§5.1) |

---

## 21. Criterios de Aceite

1. `Notas` e `Link` aparecem no menu de contexto ao lado de `Limpar pontos marcados...`.
2. Ambos ficam desabilitados com tooltip enquanto nao houver marca, ou se a mensagem estiver
   fora da posicao permitida.
3. `Notas` salva texto do analista e o `Link` salva 1..N contatos por mensagem.
4. Ao clicar `Start` com anotacoes, aparece o popup com as contagens
   (total, dentro, fora, orfas).
5. Confirmar o `Start` ignora as fora e a captura contem apenas as dentro.
6. Com o toggle OFF, nenhuma anotacao e usada e a saida e identica a da versao anterior.
7. Capturas antigas aparecem no `Re-Extrair` com o toggle em `Ignorar`, fixo e bloqueado.
8. No `whatsapp.html`, clicar na mensagem anotada mostra a nota e/ou os contatos.
9. Com exatamente 1 contato, o trecho capturado e exibido diretamente, sem lista.
10. Com 2 ou mais contatos, a lista e exibida e cada contato abre o seu trecho.
11. Contato sem captura mostra aviso, sem quebrar a pagina.
12. `manifest.json` novo tem `version: 2`, `pluginVersion: 2.0.0` e bloco `annotations` opcional.
13. `manifest.json` v1 continua legivel e sem alteracoes.
14. Nenhum teste de regressao de R1 a R5 falha.
15. Captura feita sem usar Notas/Links e sem o toggle nao contem bloco `annotations`,
    nem secao em `texto.txt`, nem `anotacoes.json` — e igual a uma captura da versao 1.
## Atualizacoes recentes

- Painel externo de anotacoes no whatsapp.html.
- Busca textual movida para a coluna lateral esquerda.
- Captura vinculada com nome deterministico por chatRefId.
- Modo de anotacao de chat salvo.
- Botao Salvar Trecho-Notas.

