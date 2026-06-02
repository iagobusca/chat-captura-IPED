# Chat Captura IPED

Plugin/customizacao para o IPED 4.3.1 que adiciona captura visual avancada de conversas WhatsApp no viewer HTML.

Este pacote foi montado para ficar em um repositorio GitHub e permitir reinstalar a versao modificada do `iped-viewers-impl-4.3.1.jar` sem precisar recompilar o IPED.

## O que este plugin altera

Arquivos principais alterados:

```text
src/iped/viewers/HtmlLinkViewer.java
src/iped/viewers/ChatCapturePanel.java
```

JAR gerado para instalacao:

```text
instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar
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

## Estrutura da pasta

```text
chat-captura IPED/
  README.md
  CHECKSUMS.md
  src/
    iped/
      viewers/
        HtmlLinkViewer.java
        ChatCapturePanel.java
  instalar_captura_chat_zap/
    instalar_captura_chat_zap.bat
    iped-viewers-impl-4.3.1.jar
```

## Como instalar

Feche o IPED antes de instalar.

Depois execute:

```text
instalar_captura_chat_zap/instalar_captura_chat_zap.bat
```

O BAT substitui este arquivo do IPED:

```text
C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\iped-4.3.1\lib\iped-viewers-impl-4.3.1.jar
```

pelo JAR modificado que esta dentro da pasta:

```text
instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar
```

## O que o BAT faz

O `instalar_captura_chat_zap.bat` faz os seguintes passos:

1. Localiza o JAR modificado na mesma pasta do instalador.
2. Localiza o JAR original dentro do IPED.
3. Cria um backup automatico do JAR atual antes de substituir.
4. Copia o JAR modificado para a pasta `lib` do IPED.
5. Mostra mensagem de sucesso ou erro.

O backup fica ao lado do JAR original, com nome parecido com:

```text
iped-viewers-impl-4.3.1.jar.bak-YYYYMMDD-HHMMSS
```

## Como usar no IPED

1. Abra o IPED depois de instalar o JAR modificado.
2. Abra uma conversa WhatsApp no viewer HTML.
3. Clique com o botao direito na mensagem inicial e marque como inicio.
4. Clique com o botao direito na mensagem final e marque como fim.
5. Escolha a pasta de saida.
6. Opcionalmente, informe uma pasta de imagens para censura por similaridade.
7. Escolha a similaridade e o nivel de blur, se usar censura.
8. Execute a captura ou use `Re-Extrair` para refazer capturas ja salvas.

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

## Observacoes importantes

- Sempre feche o IPED antes de rodar o instalador, pois o Java pode travar o JAR em uso.
- Este plugin foi feito para IPED 4.3.1.
- O JAR incluido neste pacote ja contem as classes compiladas modificadas.
- O codigo-fonte alterado esta incluido para controle de versao e manutencao futura.
