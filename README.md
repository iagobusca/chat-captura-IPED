# Chat Captura IPED

Plugin/customizacao para o IPED 4.3.1 que melhora a captura visual de conversas do WhatsApp no viewer HTML.

Este pacote foi montado para ser guardado em um repositorio GitHub e permitir reinstalar a versao modificada do `iped-viewers-impl-4.3.1.jar` sem precisar recompilar o projeto.

## O que este plugin altera

Arquivo principal alterado:

```text
src/iped/viewers/HtmlLinkViewer.java
```

JAR gerado para instalacao:

```text
instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar
```

As alteracoes foram feitas na captura de chat do `HtmlLinkViewer`, incluindo:

- Marcacao de mensagem inicial e final para captura de intervalo.
- Captura vertical do chat em varios frames PNG.
- Primeiro frame mantendo o head/topbar do WhatsApp, com foto e titulo do chat.
- Frames seguintes sem head/topbar, funcionando como continuacao.
- Corte para evitar repeticao de mensagens, audios, imagens e arquivos ja capturados em frames anteriores.
- Ajuste para parar corretamente na mensagem final marcada.
- Geracao de `manifest.json`, `capturas-index.json`, `whatsapp-coordinates.json`, `whatsapp.html`, `texto.txt`, hashes e relatorios.
- `whatsapp.html` com hotspots clicaveis sobre mensagens/anexos.
- Popup HTML para visualizar imagem ao clicar em mensagem/anexo de imagem.
- Suporte a audio/video/imagem/file no visualizador HTML.
- Melhoria visual do `Hash geral` no `index.html` final.

## Estrutura da pasta

```text
chat-captura IPED/
  README.md
  src/
    iped/
      viewers/
        HtmlLinkViewer.java
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
5. Execute a captura para gerar a pasta de resultado.

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

## Observacoes importantes

- Sempre feche o IPED antes de rodar o instalador, pois o Java pode travar o JAR em uso.
- Este plugin foi feito para IPED 4.3.1.
- O JAR incluido neste pacote ja contem as classes compiladas modificadas.
- O codigo-fonte principal alterado esta incluido para controle de versao e manutencao futura.

