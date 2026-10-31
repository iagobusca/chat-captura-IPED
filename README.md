# Chat Captura IPED

Plugin/customizacao para o IPED 4.3.1 que adiciona captura visual avancada de conversas WhatsApp no viewer HTML.

Este pacote foi montado para ficar em um repositorio GitHub e permitir reinstalar a versao modificada do `iped-viewers-impl-4.3.1.jar` sem precisar recompilar o IPED.

Versao atual do plugin: **2.3.0-beta**

## O que este plugin altera

Arquivos principais alterados:

```text
src/iped/viewers/HtmlLinkViewer.java
src/iped/viewers/CaptureIntegrity.java
src/iped/viewers/ChatCapturePanel.java
src/iped/viewers/ChatAnnotation.java
src/iped/viewers/ChatAnnotationSet.java
src/iped/viewers/ChatAnnotationStore.java
src/iped/viewers/ChatContactRef.java
src/iped/viewers/ChatNoteDialog.java
src/iped/viewers/ChatContactPickerDialog.java
src/iped/viewers/ChatPreflightDialog.java
src/iped/viewers/AnnotationEligibility.java
src/iped/viewers/AnnotationPreflight.java
```

JAR gerado para instalacao:

```text
instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar
```

Especificacao tecnica completa:

```text
ESPECIFICACAO_NOTAS_LINKS.md
```

As alteracoes incluem:

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

## Notas e Links por mensagem (versao 2.0.0)

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
- total de contatos vinculados;
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

O icone **nunca e capturado**: durante a captura o `captureRunning` bloqueia a
renderizacao dos icones e o CSS aplica `display:none`, entao o `PNG` de cada frame
e a imagem final ficam limpos. Capturas antigas precisam ser refeitas ou passar por
`Re-Extrair` para receber essa marcacao.

### No HTML gerado

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

Durante a captura o realce e apagado e escondido por CSS, entao nunca entra no
PNG dos frames.

### No HTML gerado

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

### Onde as anotacoes ficam

As anotacoes **so existem na RAM** enquanto o IPED esta aberto. Nada e gravado no
`anotacoes.json` a cada nota ou link:

- adicionar, editar ou apagar marca o armazenamento como alterado;
- o `anotacoes.json` e escrito **na hora da captura**, junto com o manifesto, o
  `texto.txt` e o `whatsapp.html`;
- captura sem anotacoes selecionadas nao grava o arquivo, preservando o
  manifesto `version: 1` e a ausencia de anotacoes no resultado.

Ao abrir uma pasta de saida que ja tenha `anotacoes.json`, o arquivo e apenas
**carregado e exibido**. Alteracoes feitas na sessao ficam na RAM e nao apagam o
que estava gravado: para mudar o resultado e preciso refazer a captura ou usar
`Re-Extrair`. Se houver alteracao nao salva, o plugin nunca relê o arquivo do
disco, porque a RAM e sempre mais nova.

Se o IPED for fechado sem capturar, as anotacoes da sessao se perdem. Por isso a
captura e o momento de consolidação do trabalho.

Todas as janelas do plugin abrem com no minimo **640x640**.

### Capturas antigas

Capturas com `manifest.json` `version: 1` sao tratadas como **legadas**: no `Re-Extrair`
o toggle aparece fixo em `Ignorar` e bloqueado, garantindo resultado identico ao da
versao anterior.

### Marcacao forense

Anotacoes nao sao evidencia original. O bloco `annotations` sai no manifesto com
`"notOriginalEvidence": true` e `"authoredBy": "analista"`, e o `texto.txt` traz uma secao
isolada. Nada e gravado dentro do caso.

## Estrutura da pasta

```text
chat-captura IPED/
  README.md
  CHECKSUMS.md
  ESPECIFICACAO_NOTAS_LINKS.md
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

## Downloads da versão beta 2.3.0

Na página de releases, baixe os três arquivos de execução ou, se quiser auditar/recompilar, escolha também os pacotes de código-fonte:

| Arquivo | Para que serve |
|---|---|
| instalador-chat-captura-IPED-2.3.0-beta.zip | Instalador completo, JAR do plugin e arquivos de idioma |
| integridade-verifier.jar | Verificador independente com Java 11 ou superior |
| integridade-verifier.exe | Mesmo verificador para Windows x64, sem Java instalado |
| src-plugin-2.3.0-beta.zip | Código-fonte separado do plugin, testes e especificações |
| src-integridade-verifier-1.2.1.zip | Código-fonte separado do verificador, testes e scripts de compilação |

Os dois códigos-fonte são downloads independentes e também permanecem organizados no repositório. Os SHA-256 dos cinco arquivos estão em CHECKSUMS.md.

## Como instalar

1. Feche o IPED.
2. Baixe e extraia instalador-chat-captura-IPED-2.3.0-beta.zip.
3. Execute instalar_captura_chat_zap/instalar_captura_chat_zap.bat da pasta extraída.
4. Reabra o IPED depois que o instalador confirmar a cópia e a instalação dos idiomas.

O ZIP é autocontido: inclui o JAR 2.3.0-beta, o instalador, o validador do pacote e os dois bundles de idioma.

## O que o BAT faz

O `instalar_captura_chat_zap.bat` faz os seguintes passos:

1. Localiza o JAR modificado na mesma pasta do instalador.
2. Localiza o JAR original dentro do IPED.
3. Cria um backup automatico do JAR atual antes de substituir.
4. Copia o JAR modificado para a pasta lib do IPED.
5. Instala os bundles de idioma do plugin.
6. Confere os hashes do pacote e do JAR instalado e mostra o resultado.

O backup fica ao lado do JAR original, com nome parecido com:

```text
iped-viewers-impl-4.3.1.jar.bak-YYYYMMDD-HHMMSS
```

## Verificador independente

O verificador não é necessário para instalar o plugin. Baixe o JAR ou o EXE separadamente na release e copie o arquivo escolhido para a pasta raiz das capturas, ao lado de index.html e relatorio.htm.

- Execute integridade-verifier.exe no Windows, sem instalar Java; ou
- execute java -jar integridade-verifier.jar com Java 11 ou superior.

Na abertura, escolha **Integridade Local** para comparar com o registro presente nas capturas ou **Integridade Público** para comparar com um histórico/hash recebido por canal externo. Para uma comparação pública, selecione o mesmo conjunto de capturas que foi usado para gerar a referência.

## Como usar no IPED

1. Abra o IPED depois de instalar o JAR modificado.
2. Abra uma conversa WhatsApp no viewer HTML.
3. Clique com o botao direito na mensagem inicial e marque como inicio.
4. Clique com o botao direito na mensagem final e marque como fim.
5. Escolha a pasta de saida.
6. Opcionalmente, informe uma pasta de imagens para censura por similaridade.
7. Escolha a similaridade e o nivel de blur, se usar censura.
8. Opcionalmente, adicione Notas e Links nas mensagens (ver secao "Notas e Links por mensagem" acima).
9. Execute a captura ou use `Re-Extrair` para refazer capturas ja salvas.

Os principais arquivos gerados no resultado sao:

- `screenshots/frame_0001.png`, `frame_0002.png`, etc.
- `whatsapp.html`
- `whatsapp-coordinates.json`
- `manifest.json`
- `capturas-index.json`
- `index.html`
- `relatorio.htm`
- `texto.txt`
- `hashes.txt`

## Censura por imagem de referencia

No campo `Pasta imagens blur`, informe uma pasta contendo imagens de referencia que devem ser censuradas quando thumbnails semelhantes aparecerem no chat.

O plugin compara as imagens encontradas no intervalo inicial/final com as imagens da pasta informada. Quando uma imagem semelhante e encontrada, o blur e aplicado no HTML antes da captura, sem alterar a imagem original do caso.

Opcoes disponiveis:

- `Similaridade blur`: controla o rigor da comparacao. `90%` e mais restrito; `50%` e mais permissivo.
- `Blur censura`: controla a intensidade visual da censura. `90%` e mais forte; `20%` e mais leve.

## Re-Extrair

O botao `Re-Extrair` usa o `capturas-index.json` e os dados de cada pasta capturada para refazer capturas existentes.

O fluxo esperado e:

1. Ler as capturas existentes.
2. Permitir escolher quais pastas/chats serao re-extraidos.
3. Abrir o chat correto no IPED.
4. Localizar a mensagem inicial e final.
5. Aplicar blur sensivel, se configurado.
6. Capturar novamente.
7. Validar a nova captura.
8. Substituir a pasta antiga pela nova.
9. Recriar `index.html`, `relatorio.htm` e `capturas-index.json`.

## Atualizacoes recentes

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


## Versao beta 2.3.0-beta

- Assinatura semantica por mensagem/frame e SHA-256 separado do PNG.
- Fingerprint de origem/intervalo/configuracao e inventario dos artefatos no manifesto.
- Re-Extrair ignora captura inalterada com inventario valido e pode regenerar whatsapp.html a partir das coordenadas salvas.
- Arquivos ausentes/alterados sao auditados com hashes esperado e recuperado; sem migracao seletiva segura, usa reextracao completa.

## Re-Extrair incremental e auditavel (2.3.0-beta)

O manifesto registra a versao do plugin, versao dos geradores, fingerprint da captura, assinatura por mensagem/frame, hash SHA-256 do PNG e inventario dos arquivos de saida. Uma captura so e ignorada quando a origem, o intervalo, as configuracoes, os geradores e todos os hashes conferem.

Se somente whatsapp.html estiver ausente/alterado ou seu gerador precisar de atualizacao, o viewer pode recria-lo usando whatsapp-coordinates.json valido. Para arquivos de origem, frames, anexos e demais divergencias sem migracao especifica, Re-Extrair usa o fluxo completo e registra no reextracao-auditoria.jsonl os hashes esperados e recuperados. Transcricoes de IA nao entram na identidade de audio/video; sem hash da midia original a assinatura fica incompleta e nao autoriza ignorar a captura.

A especificacao e o plano detalhado estao em ESPEC_REEXTRAIR_INCREMENTAL_AUDITAVEL.md.
