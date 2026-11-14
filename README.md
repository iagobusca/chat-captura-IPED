# Chat Captura IPED

Plugin/customizacao para o IPED 4.3.1 que captura intervalos de mensagens do viewer HTML e produz uma copia HTML offline auditavel.

Este pacote foi montado para ficar em um repositorio GitHub e permitir reinstalar a versao modificada do `iped-viewers-impl-4.3.1.jar` sem precisar recompilar o IPED.

Versao atual do plugin: **2.6.5-beta**
Verificador de Integridade: **1.4.0**

## About

Plugin Java para IPED 4.3.1 que captura intervalos de conversas WhatsApp e gera HTML offline, manifesto por mensagem e anexo, anotações, inventários e hashes para revisão forense. Capturas anteriores baseadas em screenshots continuam compatíveis.

A versão 2.6.0-beta atualiza a allowlist oficial e o pacote de distribuição para o **Verificador de Integridade 1.3.5**, que adiciona no resumo a opção **Copiar histórico completo público** com anonimização do nome das pastas (`CAPTURA|*-YYYYMMDD-HHMMSS` ou `CAPTURA|*`) e exibição do **SHA-256 do histórico completo público**, além de verificação dual no modo **Integridade Público** para aceitar tanto o formato completo original quanto o formato anonimizado (e o SHA-256 de ambos). A versão 2.5.9-beta corrige a resolução de fronteiras na captura, no **Re-Extrair** e na migração de capturas legadas para aceitar registros de chamada (`<digits>_call:<hex>`), mensagens removidas/carved (`-?<digits>_<id>`) e timestamps internos (`iped-capture-date-...`), normaliza `data-iped-capture-id`/`domId` no plugin (`CaptureBoundaryResolver` + `HtmlLinkViewer`) e no Verificador, embute `org/opencv/**` em um fat-JAR autocontido e torna tolerante o tratamento de anexos não suportados em `html-message-v1`. A versão 2.5.8-beta aplica a censura visual por imagens de referência (blur CSS) também em capturas `html-message-v1` (captura nova, Re-Extrair e migração de legado), sem JavaScript adicional e mantendo total compatibilidade com o Verificador, remove `integridade/` e `integridade.html` da raiz ao re-extrair com "Limpar registros" (sem criar backups) e deixa de marcar capturas `html-message-v1` como anotações legadas. A versão 2.5.7-beta permite limpar os registros também de uma captura já existente em `html-message-v1`, recriando-a como captura nova, e ajusta a janela **Re-Extrair capturas** à área útil do monitor. A versão 2.5.6-beta permite migrar capturas legadas em frames para `html-message-v1` pelo Re-Extrair, com validação completa da nova pasta antes da substituição e opção de preservar a cadeia de integridade ou iniciar uma nova captura. A versão 2.5.5-beta mantém o chat na mesma posição após fechar o preview de imagem, áudio, vídeo, PDF, documento ou arquivo. A versão 2.5.4-beta normaliza o MIME das imagens embutidas no cabeçalho pela assinatura real dos bytes, corrigindo PNGs do IPED declarados como `image/jpg`.

## Getting Started

Antes de extrair e executar o instalador, confira o SHA-256 do arquivo ZIP com o valor publicado em [CHECKSUMS.md](CHECKSUMS.md) por canal separado e confiável: o hash fixado dentro do `instalar_captura_chat_zap.bat` detecta corrupção ou troca parcial de arquivos, mas não protege se o pacote ZIP inteiro tiver sido adulterado.

Feche o IPED, extraia o ZIP do instalador e execute `instalar_captura_chat_zap/instalar_captura_chat_zap.bat`. Reabra o IPED e abra uma conversa WhatsApp no viewer HTML.

## Usage

Marque o início e o fim do trecho pelo menu de contexto, escolha a pasta de saída e inicie a captura. Um separador de data pode marcar o início e será resolvido para a próxima mensagem; o fim precisa ser uma mensagem. O plugin valida os dois limites antes de começar. Novas capturas usam `html-message-v1`, com mensagens e anexos preservados localmente. Para instruções detalhadas, consulte [doc.md](doc.md), [SPEC_CAPTURA_HTML_POR_MENSAGEM.md](SPEC_CAPTURA_HTML_POR_MENSAGEM.md) e [SPEC_RENDERIZACAO_WHATSAPP_HTML.md](SPEC_RENDERIZACAO_WHATSAPP_HTML.md).

## License

Este repositório não contém um arquivo `LICENSE`; a licença do projeto não está especificada aqui.

## O que este plugin altera

Arquivos principais alterados:

```text
src/iped/viewers/HtmlLinkViewer.java
src/iped/viewers/CaptureCensorship.java
src/iped/viewers/CaptureIntegrity.java
src/iped/viewers/ChatCapturePanel.java
src/iped/viewers/ChatCaptureLinkedWindow.java
src/iped/viewers/ChatAnnotation.java
src/iped/viewers/ChatAnnotationSet.java
src/iped/viewers/ChatAnnotationStore.java
src/iped/viewers/ChatAnnotationActionDialog.java
src/iped/viewers/ChatNoteDialog.java
src/iped/viewers/LinkedChatRef.java
src/iped/viewers/LinkedChatPickerDialog.java
src/iped/viewers/SavedChatPickerDialog.java
src/iped/viewers/ChatPreflightDialog.java
src/iped/viewers/ChatSearchHit.java
src/iped/viewers/AnnotationEligibility.java
src/iped/viewers/AnnotationPreflight.java
```

JAR gerado para instalacao:

```text
instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar
```

Especificacao tecnica completa:

```text
ESPEC_UNICA_CHAT_CAPTURE_IPED.md
```

As capturas novas da versão 2.5.6-beta incluem:

- Cópia offline do intervalo DOM selecionado, sem screenshots e coordenadas como conteúdo principal.
- Manifesto `messages.json` com identidade e SHA-256 por mensagem e por anexo.
- Recursos de anexos recuperados do caso por `app.open('sha-256:...')`, com validação de hash, MIME, caminho e limites.
- Reconciliação do Re-Extrair com estados por mensagem e preservação da captura anterior em falha.
- Migração opt-in de capturas legadas para `html-message-v1`, com localização dos marcadores persistidos, validação da candidata e substituição transacional; a pasta antiga permanece intacta em qualquer falha anterior à promoção.
- Modo **Limpar registros e criar como nova captura**, disponível para capturas legadas migradas e para capturas já existentes em `html-message-v1`. Ele inicia uma nova cadeia em `capture_created` e descarta `migration.json`, `legacy-annotations.json`, reconciliação e auditoria antigas. Em capturas novas, o Re-Extrair ignora a comparação de conteúdo e recaptura o intervalo. Anexos legados só são reutilizados após conferir SHA-256 e tamanho.
- Preservação das anotações legadas em `legacy-annotations.json` quando a cadeia anterior é mantida; elas não são vinculadas automaticamente às novas mensagens.
- Capturas já em `html-message-v1` não são substituídas quando o intervalo permanece igual; o evento `recapture_skipped_content_unchanged` é registrado na auditoria.
- Marcador de início colocado em separador de data é resolvido para a primeira mensagem seguinte e salva a ID real no manifesto para o Re-Extrair.
- A origem do verificador é aceita somente quando o SHA-256 corresponde à allowlist; arquivos inválidos são substituídos por cópias verificadas antes da captura.
- Sanitização do HTML, remoção de scripts/handlers copiados do IPED e bloqueio de URLs remotas. A apresentação usa somente o script fixo autorizado pela CSP para parar mídia.
- Preservação do tema WhatsApp do IPED 4.3.1, com modal offline para imagem, PDF, documentos e arquivos; áudio/vídeo usam controles sem autoplay e param ao fechar.
- Verificador 1.3.4 valida a cadeia local de integridade também em capturas `html-message-v1` e confere o vínculo de `migration.json` e `legacy-annotations.json` ao inventário. Mantém as validações de CSS/CSP, scripts de mídia das versões 1.3.2 e 1.3.3, PDF isolado e compatibilidade de assinatura para avatares PNG legados declarados como `image/jpg`.
- Compatibilidade de leitura com capturas legadas em screenshots.

O formato `html-message-v1` aplica censura por similaridade de imagens via CSS puro e atributos HTML (`CaptureCensorship`) e registra o resumo em `manifest.json` (`censorship`). **Aviso:** o blur CSS é supressão visual de apresentação (não tarja definitiva: os bytes originais permanecem em `attachments/` e abrem no modal).




Os recursos abaixo descrevem o fluxo legado baseado em screenshots, ainda suportado para capturas antigas:

- Marcacao de mensagem inicial e final para capturar somente um intervalo do chat.
- Captura vertical em varios frames PNG.
- Primeiro frame mantendo o head/topbar do WhatsApp, com foto e titulo do chat.
- Frames seguintes sem head/topbar, funcionando como continuacao.
- Corte e alinhamento para evitar repeticao de mensagens, audios, imagens e arquivos ja capturados.
- Parada correta na mensagem final marcada, com margem para nao cortar o ultimo elemento.
- Geracao de `manifest.json`, `capturas-index.json`, `whatsapp-coordinates.json`, `whatsapp.html`, `texto.txt`, hashes e relatorios.
- `whatsapp.html` com hotspots clicaveis sobre mensagens/anexos.
- Popup HTML para visualizar imagem ao clicar em mensagem/anexo de imagem.
- Suporte a audio, video, imagem e file no visualizador HTML.
- Melhoria visual do `Hash geral` no `index.html` final.
- Botao `Re-Extrair` para refazer capturas existentes usando os dados salvos.
- Barra de progresso por etapa durante o `Re-Extrair`.
- Localizacao de chat por nome exato de origem e localizacao de inicio/fim por texto+data, hash+data ou date.
- Censura/blur de thumbnails de imagens semelhantes a uma pasta de imagens de referencia.
- Controle de similaridade da censura: `50%`, `75%`, `90%`.
- Controle de intensidade do blur: `20%`, `30%`, `60%`, `90%`.
- Remocao do overlay listrado antigo `iped-sensitive-overlay`, mantendo apenas o efeito de blur.

## Notas e Links por mensagem

Este recurso foi introduzido na versão 2.0.0 e continua disponível no plugin 2.5.9-beta. A indicação 2.0.0 descreve a versão em que a funcionalidade surgiu; não é a versão atual do plugin.

**Recurso opcional.** Nada e obrigatorio: quem nao usar continua com o fluxo anterior,
com saida identica.

Dois itens novos no menu de botao direito do chat:

- **Adicionar nota nesta mensagem** - texto livre do analista.
- **Vincular chats a esta mensagem** - lista de conversas do caso ligadas aquele trecho.

O que se vincula e o **chat**, nao uma pessoa. O popup lista os chats do caso
(botao `Carregar chats do caso`, que consulta o indice sob demanda) e aceita
entrada manual.

Ao escolher um chat e clicar **Abrir chat e capturar trecho**, abre uma janela nova
com aquele chat e **o mesmo painel de captura** de sempre: pasta de destino, nome
custom, pasta de imagens para blur, similaridade, nivel de blur, `Start`/`Stop`,
`Re-Extrair` e o menu de botao direito para marcar inicio, final e limpar. A captura
acontece nessa janela e o chat original continua aberto no visualizador.

### Regra de disponibilidade

Os dois itens ficam **visiveis e desabilitados** (com o motivo no proprio rotulo) ate que:

- exista ao menos um ponto marcado (inicio ou fim);
- a mensagem estiver depois do inicio marcado (ou antes do fim, quando so ha fim);
- a mensagem nao seja um separador de data.

### Pre-flight do Start

Ao clicar `Start` com anotacoes presentes, aparece um popup com:

- quantas notas e quantas mensagens com link;
- total de chats vinculados;
- quantas anotacoes estao **dentro** do intervalo;
- quantas estao **fora** e serao **ignoradas**;
- quantas nao tem correspondencia no chat atual.

Confirmar o Start descarta as fora. O toggle `Incluir Notas e Links nesta captura`, no painel,
desliga o recurso e o Start segue direto, sem popup.

### Persistencia

Ver "Onde as anotacoes ficam" abaixo. Gravacao atomica, na captura.
Arquivo ausente ou invalido nao quebra o viewer.

### No chat do visualizador

A mensagem anotada ganha um icone `[nota]` e/ou `[link]` **a esquerda** do balao.
Clicar no icone abre a janela de acoes, com duas areas:

- **Notas** - `Ver` mostra o texto da nota; `Apagar` remove a nota de `anotacoes.json`
  (com confirmacao). Fica desabilitado quando a mensagem nao tem nota;
- **Chat Vinculado** - `Ver` lista os chats e onde a captura esta; `Apagar` remove
  todos os vinculos da mensagem (com confirmacao).

O icone **nunca e capturado**: durante capturas legadas o `captureRunning` bloqueia a
renderizacao dos icones e o CSS aplica `display:none`, entao ele nao entra no PNG.
No novo formato, as anotações aplicáveis são anexadas ao HTML em um bloco separado.

### HTML de capturas legadas por imagem

A captura e exibida limpa. O icone `[nota]`/`[link]` fica fora da imagem, sobreposto
a esquerda do balao correspondente, e nunca altera o `PNG` do frame. Clicar nele abre
o painel do analista:

- com **1 chat**, mostra direto o trecho capturado daquele chat;
- com **2 ou mais**, lista os chats e cada um abre o seu trecho;
- chat sem captura mostra aviso e nao quebra a pagina.

O `whatsapp.html` e estatico (aberto por `file://`), entao o popup permite apenas
**ver**. Apagar anotacao e feito no chat do visualizador, que tem acesso ao
`anotacoes.json`; depois e preciso regerar ou usar `Re-Extrair` para o HTML acompanhar.

## Pesquisa no chat

No painel da direita, abaixo dos contadores, ha a area **Pesquisar no chat**.

- a busca cobre **todas** as mensagens do chat carregado, nao apenas as visiveis;
- ignora maiusculas/minusculas e acentos: `joao` acha `Joao` e `Joao`;
- com varios termos, **todos** precisam aparecer na mesma mensagem (`joao silva`);
- `Enter` vai para o proximo resultado, `Shift+Enter` volta, `Esc` limpa;
- `Ant` / `Prox` navegam, e o duplo clique na lista tambem rola o chat;
- a mensagem encontrada fica com realce azul; a lista mostra o tipo
  (`data`, `imagem`, `audio`, `video`, `arquivo`) e o trecho em volta do termo;
- no maximo **400** resultados sao listados; se houver mais, o contador avisa o
  total real.

A area funciona **tambem na janela de chat vinculado**, porque cada janela tem o
seu proprio painel. Ali a busca nao depende de anotacao nenhuma.

Durante a captura legada o realce e apagado e escondido por CSS, entao nunca entra
no PNG dos frames.

### Busca no HTML de capturas legadas

O `whatsapp.html` mostra o chat **por imagens**, entao nao existe texto no DOM
para o navegador pesquisar. Por isso a busca funciona com um indice montado a
partir do `blocks` de cada frame, que guarda o texto de cada mensagem no momento
da captura.

- barra de busca no topo, com contador de resultados;
- resultados na lateral, com frame, tipo e trecho em volta do termo;
- clicar no resultado **rola ate o frame**, destaca o bloco em azul e abre o
  texto na lateral;
- ignora acentos e maiusculas; varios termos exigem todos na mesma mensagem;
- `Esc` limpa a busca; ate 300 resultados sao listados.

Importante: o indice vem do que foi capturado. Mensagens **fora** do intervalo
capturado nao aparecem na busca do HTML. Para pesquisar o chat inteiro, use a
busca do painel no IPED (secao acima), que le o DOM do chat carregado.

### Onde as anotações ficam

As anotações são mantidas por chat em `anotacoes.json`. Durante a edição, as mudanças ficam na RAM para não sobrescrever o arquivo salvo com dados ainda não gravados.

- Uma captura persiste as anotações aplicáveis junto aos artefatos gerados.
- Em **Anotar chat salvo**, o botão **Salvar Trecho-Notas** grava as alterações sem exigir uma nova captura. Use `Re-Extrair` depois para atualizar o HTML derivado quando necessário.
- Ao abrir uma pasta com `anotacoes.json`, o plugin carrega os dados existentes. Alterações ainda não salvas não substituem o arquivo.
- Fechar o IPED descarta somente as alterações que não foram salvas nem incluídas em uma captura.
- Sem anotações, a captura não precisa criar o bloco opcional de anotações.

Todas as janelas do plugin abrem com no minimo **640x640**. A janela
**Re-Extrair capturas** calcula largura e altura pela área útil do monitor,
descontando a barra de tarefas: em 1366x768 ela fica com cerca de 879x638 px e
cresce proporcionalmente em Full HD, 2K e 4K.

### Capturas antigas

Capturas com `manifest.json` `version: 1` sao tratadas como **legadas**. No `Re-Extrair`,
o toggle **Migrar capturas legadas para HTML por mensagem** fica disponível para as
capturas selecionadas; sem ele, o fluxo de screenshots das versoes anteriores e mantido.
Com o toggle marcado, a captura e recriada em `html-message-v1` e a pasta antiga so e
substituida depois da validacao da candidata. O toggle dependente
**Limpar registros e criar como nova captura** reinicia a cadeia de integridade.
Em capturas que ja estao em `html-message-v1`, esse mesmo toggle fica disponivel
sem exigir a migracao e recaptura o intervalo como uma nova captura.

### Marcacao forense

Anotacoes nao sao evidencia original. O bloco `annotations` sai no manifesto com
`"notOriginalEvidence": true` e `"authoredBy": "analista"`, e o `texto.txt` traz uma secao
isolada. Nada e gravado dentro do caso.

## Estrutura da pasta

```text
chat-captura IPED/
  README.md
  CHECKSUMS.md
  ESPEC_UNICA_CHAT_CAPTURE_IPED.md
  src/
    iped/
      viewers/
        HtmlLinkViewer.java
        ChatCapturePanel.java
        ChatCaptureLinkedWindow.java
        ChatAnnotation.java
        ChatAnnotationSet.java
        ChatAnnotationStore.java
        ChatAnnotationActionDialog.java
        LinkedChatRef.java
        LinkedChatPickerDialog.java
        ChatNoteDialog.java
        ChatPreflightDialog.java
        ChatSearchHit.java
        AnnotationEligibility.java
        AnnotationPreflight.java
  instalar_captura_chat_zap/
    instalar_captura_chat_zap.bat
    install-localization.ps1
    validate-plugin-jar.ps1
    iped-viewer-messages*.properties
    iped-viewers-impl-4.3.1.jar
  verificador-integridade/
    src/IntegrityVerifier.java
    integridade-verifier.jar
    dist/integridade-verifier.exe (artefato da release)
```

## Artefatos da release 2.6.0-beta

Os artefatos publicados para a versão 2.6.0-beta são:

| Arquivo | Para que serve |
|---|---|
| `instalador-chat-captura-IPED-2.6.0-beta-verificador-1.3.5.zip` | Pacote instalador com fat-JAR `2.6.0-beta`, OpenCV (`opencv-4120.jar` + `opencv_java4120.dll`) e Verificador 1.3.5 |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.0-beta.jar` | Fat-JAR compilado do plugin para instalação manual |
| `verificador-integridade/integridade-verifier.jar` | Verificador independente com Java 8 (1.8) ou superior |
| `verificador-integridade/integridade-verifier.exe` | Mesmo verificador para Windows x64, sem Java ou .NET instalados |
| `SPEC_CAPTURA_HTML_POR_MENSAGEM.md e SPEC_RENDERIZACAO_WHATSAPP_HTML.md` | Contrato, formato de captura, limites, testes e validação automatizada no IPED |
| `SPEC_MIGRACAO_REEXTRACAO_LEGADA_HTML.md` | Regras da migração legada, transação, proveniência, testes e critérios de aceite |

Os SHA-256 estão documentados em [CHECKSUMS.md](CHECKSUMS.md).


### Hashes do Verificador de Integridade 1.3.5

JAR — SHA-256: `31B0CE725708ED53AD9BE319FFE51439940C65BB46EF8593AB78376C07BF46B9`<br>
EXE — SHA-256: `75FB542BCB53C44C7B17553EEC761C16F17C670F8033DD3F901F80A0897AC42B`

JAR do plugin 2.6.0-beta — SHA-256: `DFC47C2F050837A7A334E06615D731252E75FDF674F80CEF9AD5EFDD28F24936`<br>
ZIP do instalador 2.6.0-beta — SHA-256: `2F67E5EA2D7172D3097DF1872A084F6220D14C2AB4BCE7F276E355F7AC9884F4`

O histórico de hashes das versões do verificador está em [versao_hash.md](versao_hash.md). Consulte também [CHECKSUMS.md](CHECKSUMS.md) para os hashes do instalador e do JAR do plugin.

---

## Release v2.6.0-beta

[v2.6.0-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.0-beta) Pre-Release

[iago](http://127.0.0.1:3000/iago) | [compare/v2.6.0-beta...main](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/compare/v2.6.0-beta...main)

### Downloads

- [instalador-chat-captura-IPED-2.6.0-beta-verificador-1.3.5.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.0-beta) (`95.427.705` bytes | SHA-256: `2F67E5EA2D7172D3097DF1872A084F6220D14C2AB4BCE7F276E355F7AC9884F4`)
- [integridade-verifier-1.3.5.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.0-beta) (`77.839.428` bytes | SHA-256: `75FB542BCB53C44C7B17553EEC761C16F17C670F8033DD3F901F80A0897AC42B`)
- [integridade-verifier-1.3.5.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.0-beta) (`133.282` bytes | SHA-256: `31B0CE725708ED53AD9BE319FFE51439940C65BB46EF8593AB78376C07BF46B9`)
- [iped-viewers-impl-4.3.1-2.6.0-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.0-beta) (`1.482.427` bytes | SHA-256: `DFC47C2F050837A7A334E06615D731252E75FDF674F80CEF9AD5EFDD28F24936`)

---

### Otimizado

- Verificador de Integridade **1.3.5**: novo campo **SHA-256 do histórico completo público** e botão **Copiar histórico completo público** no resumo final, exportando linhas determinísticas no formato anonimizado `CAPTURA|*-YYYYMMDD-HHMMSS ...` (ou `CAPTURA|*`) para compartilhamento público sem expor nomes das pastas de captura.
- Comparação dual no modo **Integridade Público** do Verificador 1.3.5: aceita e valida de forma segura tanto o histórico completo original (`CAPTURA|<pasta>-YYYYMMDD-HHMMSS ...`) quanto o histórico completo público anonimizado (`CAPTURA|*-YYYYMMDD-HHMMSS ...`), bem como o SHA-256 de qualquer um dos dois formatos.
- Opção CLI `--export-history` do Verificador 1.3.5 passa a emitir também `SHA-256_HISTORICO_PUBLICO=...`.
- Script `verificador-integridade/build-single-exe.ps1` reforçado com `MSBUILDDISABLENODEREUSE=1`, `DOTNET_CLI_DO_NOT_USE_MSBUILD_SERVER=1`, `-m:1 -nodeReuse:false` e `dotnet build-server shutdown` para eliminar travamentos do MSBuild/Roslyn ao empacotar o `.exe`.

---

### Corrigido

- Allowlist de binários confiáveis em `VerifierIntegrity.java` e testes (`VerifierIntegrityHashTest.java`) sincronizados com os binários oficiais do Verificador **1.3.5** (`31B0CE72...` e `75FB542B...`).
- Plugin `2.6.0-beta` compilado em fat-JAR autocontido com `Implementation-Version: 2.6.0-beta`, mantendo todas as correções da `2.5.9-beta` (registros de chamada `_call:<hex>`, IDs carved/removidos `-?<id>_<id>`, timestamps internos `iped-capture-date-...` e tolerância de anexos).

---

### Incluido

- Plugin Chat Captura IPED `2.6.0-beta` (`iped-viewers-impl-4.3.1-2.6.0-beta.jar`).
- Verificador de Integridade `1.3.5` em JAR e EXE Windows x64 autocontido.
- ZIP instalador completo com dependências (`json-20260719.jar`, `opencv-4120.jar`, `opencv_java4120.dll`).

---

### SHA-256 do Verificador 1.3.5

- JAR: `31B0CE725708ED53AD9BE319FFE51439940C65BB46EF8593AB78376C07BF46B9`
- EXE: `75FB542BCB53C44C7B17553EEC761C16F17C670F8033DD3F901F80A0897AC42B`

Veja [versao_hash.md](versao_hash.md) para os hashes das versoes anteriores e [RELEASE_NOTES_v2.5.9-beta.md](RELEASE_NOTES_v2.5.9-beta.md) para as notas da versão anterior.

---

*Tag da versão: [v2.6.0-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/src/tag/v2.6.0-beta)*

---

## Como instalar

1. Feche o IPED.
2. Baixe e extraia `instalador-chat-captura-IPED-2.6.0-beta-verificador-1.3.5.zip`.
3. Execute `instalar_captura_chat_zap/instalar_captura_chat_zap.bat` da pasta extraída.
4. Reabra o IPED depois que o instalador confirmar a cópia e a instalação dos idiomas.

O ZIP inclui o fat-JAR 2.6.0-beta, o instalador, o validador, `org.json` 20260719, `opencv-4120.jar`, `opencv_java4120.dll`, os dois bundles de idioma e o Verificador 1.3.5 em JAR/EXE. Ao instalar, o BAT preserva as cópias anteriores e instala os bytes da versão 20260719 com os nomes `json-20260719.jar` e `json-20250517.jar` para compatibilidade com classpaths do IPED.

## O que o BAT faz

O `instalar_captura_chat_zap.bat` faz os seguintes passos:

1. Localiza o JAR modificado na mesma pasta do instalador.
2. Localiza o JAR original dentro do IPED.
3. Cria um backup automatico do JAR atual antes de substituir.
4. Copia o JAR modificado para a pasta lib do IPED.
5. Substitui `json-20250517.jar` por `json-20260719.jar`, preservando as versões anteriores com sufixo `.bak`.
6. Instala os bundles de idioma do plugin.
7. Confere os hashes do pacote e do JAR instalado e mostra o resultado.

O backup fica ao lado do JAR original, com nome parecido com:

```text
iped-viewers-impl-4.3.1.jar.bak-YYYYMMDD-HHMMSS
```

## Verificador independente

O Verificador 1.3.5 mantém os limites de recursos da versão 1.2.8 e adiciona no resumo a opção **Copiar histórico completo público** (`CAPTURA|*-YYYYMMDD-HHMMSS ...`) com **SHA-256 do histórico completo público**, além de comparação dual no modo **Integridade Público** para aceitar tanto o histórico completo original quanto o histórico público anonimizado (e ambos os hashes SHA-256). Continua validando `html-message-v1`, o tema CSS/CSP, os scripts fixos de mídia 1.3.2 e 1.3.3, o preview PDF confinado, registros de chamada (`_call:<hex>`), IDs carved/removidos (`-?<id>_<id>`), hashes por mensagem/anexo, MIME/assinatura e a cadeia local de integridade incluindo vínculo de `migration.json` e `legacy-annotations.json`. JSON acima de 16 MiB, profundidade acima de 64, caminhos acima de 4.096 caracteres, capturas acima de 25.010 arquivos e seleções além dos limites globais são rejeitados. O JAR funciona com Java 8 ou superior; o EXE inclui os runtimes necessários. Consulte [versao_hash.md](versao_hash.md) para o histórico completo de hashes.

O verificador não é necessário para instalar o plugin. Durante a captura, o plugin tenta localizar verificadores em pastas conhecidas e copiar os arquivos com hashes reconhecidos para a pasta de saída. Se não encontrar os binários, baixe o JAR ou EXE da release e copie-os manualmente para a pasta raiz das capturas, ao lado de index.html e relatorio.htm.

- Execute integridade-verifier.exe no Windows, sem instalar Java; ou
- execute java -jar integridade-verifier.jar com Java 8 (1.8) ou superior.

Na abertura, escolha **Integridade Local** para comparar com o registro presente nas capturas ou **Integridade Público** para comparar com um histórico/hash recebido por canal externo. Para uma comparação pública, selecione o mesmo conjunto de capturas que foi usado para gerar a referência.

## Como usar no IPED

1. Abra o IPED depois de instalar o JAR modificado.
2. Abra uma conversa WhatsApp no viewer HTML.
3. Clique com o botao direito na mensagem inicial e marque como inicio.
4. Clique com o botao direito na mensagem final e marque como fim.
5. Escolha a pasta de saida.
6. Opcionalmente, informe uma pasta de imagens de referência para censura por similaridade (blur CSS em `html-message-v1` ou pixelização no fluxo legado PNG).
7. O formato padrão é `html-message-v1` (use `-Diped.chat.capture.htmlMessageV1=false` apenas se precisar selecionar o fluxo PNG legado).
8. Opcionalmente, adicione Notas e Links nas mensagens (ver secao "Notas e Links por mensagem" acima).
9. Execute a captura ou use `Re-Extrair` para refazer capturas ja salvas.

Novas capturas `html-message-v1` geram principalmente:

- `whatsapp.html` — intervalo selecionado, renderizável offline;
- `messages.json` — identidade, ordem, conteúdo e SHA-256 por mensagem/anexo;
- `attachments/` — recursos locais por hash, quando necessários;
- `manifest.json`
- `message-reconciliation.json` — estados por mensagem após Re-Extrair, quando aplicável;
- `migration.json` — proveniência de uma captura legada migrada, quando aplicável;
- `legacy-annotations.json` — anotações antigas preservadas sem vínculo com as novas mensagens;
- `integridade.json` — inventário de integridade da captura.

Na pasta raiz da saída, o plugin mantém `capturas-index.json`, `index.html` e
`relatorio.htm`. `texto.txt`, `hashes.txt`, screenshots e coordenadas são artefatos
do formato legado.

As capturas anteriores podem continuar contendo `screenshots/frame_*.png` e `whatsapp-coordinates.json`. O formato antigo só é reescrito quando o analista marca a migração no Re-Extrair; sem essa opção, ele permanece intacto. As seções abaixo que mencionam frames, PNG ou coordenadas descrevem esse formato legado.

## Censura por imagem de referência (`html-message-v1` e fluxo legado)

O campo `Pasta imagens blur` aceita uma pasta com imagens de referência que devem ser censuradas quando imagens semelhantes aparecerem no chat. Em `html-message-v1`, a miniatura recebe filtro CSS (`filter: blur(...)`) e faixa de aviso, abrindo o anexo original no modal ao clicar (supressão visual de apresentação, não tarja irreversível).

No fluxo PNG legado, o plugin compara as imagens encontradas no intervalo inicial/final com as imagens da pasta informada e aplica blur ao frame capturado, sem alterar a imagem original do caso.

Opcoes disponiveis:

- `Similaridade blur`: controla o rigor da comparacao. `90%` e mais restrito; `50%` e mais permissivo.
- `Blur censura`: controla a intensidade visual da censura. `90%` e mais forte; `20%` e mais leve.

## Re-Extrair

O botao `Re-Extrair` usa o `capturas-index.json` e os dados de cada pasta capturada para refazer capturas existentes.

Para `html-message-v1`, o fluxo localiza as âncoras DOM inicial e final, exige correspondência única e valida a captura temporária antes da promoção. A reconciliação registra mensagens inalteradas, alteradas, novas e não encontradas na origem atual. Se o intervalo não mudou, a candidata é descartada, a captura atual é preservada e a auditoria registra `recapture_skipped_content_unchanged`.

Para as capturas legadas, o diálogo oferece **Migrar capturas legadas para HTML por mensagem**. Quando marcado, o plugin localiza as âncoras persistidas no manifesto e nas coordenadas, gera uma candidata `html-message-v1` em pasta temporária irmã, valida manifesto, HTML, mensagens, anexos e inventário e só então substitui a pasta antiga. Sem o toggle, o fluxo de screenshots é mantido.

O toggle dependente **Limpar registros e criar como nova captura** fica habilitado para capturas novas e para capturas legadas com a migração marcada. Ele inicia uma nova cadeia em `capture_created` e descarta `migration.json`, `legacy-annotations.json`, reconciliação e auditoria antigas. No modo com histórico, as anotações legadas são preservadas em `legacy-annotations.json` e o manifesto registra a proveniência da migração. A migração aceita pasta de imagens de referência para censura visual e exibe aviso de confirmação quando executada sem pasta de referência.

O fluxo de captura legada e:

1. Ler as capturas existentes.
2. Permitir escolher quais pastas/chats serao re-extraidos.
3. Abrir o chat correto no IPED.
4. Localizar a mensagem inicial e final.
5. Aplicar blur sensivel, se configurado, ou migrar para HTML sem blur.
6. Capturar novamente no formato escolhido.
7. Validar a nova captura.
8. Substituir a pasta antiga pela nova.
9. Recriar `index.html`, `relatorio.htm` e `capturas-index.json`.

## Atualizacoes recentes

- Re-Extrair agora migra capturas legadas em frames para `html-message-v1` com validação transacional e opção de reiniciar a cadeia de integridade.

- Busca no `index.html` agora normaliza `_` e espaços, facilitando nomes como `Jean_pai_de_carlos`.

- Painel externo de anotacoes (`Nota` / `Chat vinculado`) no `whatsapp.html`, ao lado do chat.
- Busca textual do `whatsapp.html` movida para a coluna lateral esquerda.
- Captura de chat vinculado com nome deterministico (`chatRefId`), permitindo exibir o trecho sem re-extrair.
- Modo `Anotar chat salvo...`, que reutiliza a pasta de destino e lista chats ja salvos.
- Botao `Salvar Trecho-Notas` para persistir notas e links sem recapturar.
- Anotacoes em modo de chat salvo liberadas para mensagens comuns, sem exigir marcas de inicio/fim.

## Observacoes importantes

- Sempre feche o IPED antes de rodar o instalador, pois o Java pode travar o JAR em uso.
- Este plugin foi feito para IPED 4.3.1.
- O JAR incluido neste pacote ja contem as classes compiladas modificadas.
- O codigo-fonte alterado esta incluido para controle de versao e manutencao futura.


## Versao beta 2.2.1-beta (anterior)

- O dialogo `Re-Extrair capturas` ganhou selecao em massa com o botao alternavel `Marcar todos` / `Desmarcar todos`; somente capturas validas podem ser selecionadas.
- O viewer permanece compilado para Java 11 (`--release 11`), compativel com o runtime empacotado pelo IPED 4.3.1.
- O instalador valida classes e bundles de idioma antes da troca e preserva backup do JAR instalado.


## Versao beta 2.3.2-beta

- Corrige a leitura de entradas JSON grandes e atualiza `org.json` para 20260719, corrigindo o CVE-2026-59171.
- Limita arquivos JSON a 16 MiB e inventários/capturas a 25.000/25.010 arquivos.
- Usa hash em fluxo e temporários exclusivos; recusa links simbólicos nos destinos de relatórios e auditoria.
- Atualiza o Verificador de Integridade para 1.2.5 e sincroniza os hashes aceitos pelo plugin.

## Versao beta 2.3.1-beta

- Assinatura semantica por mensagem/frame e SHA-256 separado do PNG.
- Fingerprint de origem/intervalo/configuracao e inventario dos artefatos no manifesto.
- Re-Extrair ignora captura inalterada com inventario valido e pode regenerar whatsapp.html a partir das coordenadas salvas.
- Arquivos ausentes/alterados sao auditados com hashes esperado e recuperado; sem migracao seletiva segura, usa reextracao completa.

## Re-Extrair incremental e auditavel (2.3.1-beta)

O manifesto registra a versao do plugin, versao dos geradores, fingerprint da captura, assinatura por mensagem/frame, hash SHA-256 do PNG e inventario dos arquivos de saida. Uma captura so e ignorada quando a origem, o intervalo, as configuracoes, os geradores e todos os hashes conferem.

Se somente whatsapp.html estiver ausente/alterado ou seu gerador precisar de atualizacao, o viewer pode recria-lo usando whatsapp-coordinates.json valido. Para arquivos de origem, frames, anexos e demais divergencias sem migracao especifica, Re-Extrair usa o fluxo completo e registra no reextracao-auditoria.jsonl os hashes esperados e recuperados. Transcricoes de IA nao entram na identidade de audio/video; sem hash da midia original a assinatura fica incompleta e nao autoriza ignorar a captura.

A especificacao e o plano detalhado estao em ESPEC_UNICA_CHAT_CAPTURE_IPED.md.
