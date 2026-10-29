# Especificacao: recursos de idioma do plugin

O plugin Chat Captura usa as chaves `ChatCapturePanel.*` em `Messages.getString`. A ausencia de qualquer chave solicitada durante a construcao do painel gera `MissingResourceException`, interrompe a inicializacao Swing do IPED e pode deixar a janela branca e sem resposta ao fechar.

## Recursos exigidos

- `iped-viewer-messages.properties`: mensagens padrão em inglês.
- `iped-viewer-messages_pt_BR.properties`: mensagens em português brasileiro.
- Ambos devem ser armazenados na raiz de `iped-viewers-impl-4.3.1.jar`, ao lado das classes compiladas.
- O instalador deve fazer backup do JAR, substituir o JAR pelo pacote, inserir os dois bundles e comparar SHA-256; em qualquer erro deve restaurar o backup.

As duas listas devem manter as mesmas chaves. O JAR final deve conter `ChatCapturePanel.IncludeAnnotations`, `ChatCapturePanel.SelectFolder` e todas as outras chaves usadas pelo fonte. A aplicação deve iniciar sem `MissingResourceException`; teste funcional deve verificar o painel e os controles de captura.

## Localização externa

O IPED 4.3.1 resolve mensagens por iped.localization.Messages.getExternalBundle, priorizando os arquivos em iped\localization. Portanto, instalar apenas bundles dentro do JAR é insuficiente. O instalador agora mescla apenas chaves ausentes nos arquivos externo padrão e pt_BR, criando backups e preservando traduções existentes. As bundles completos também ficam na raiz do JAR para consistência.



## Recuperacao quando faltar uma traducao

iped.viewers.localization.Messages.getString registra um aviso curto e retorna a chave quando ela falta. Isso evita que a janela Swing seja abortada. O BAT valida as classes e chaves obrigatorias antes de substituir o JAR.


## Pacote beta 2.2.1

O JAR distribuido junto ao instalador corresponde a 2.2.1-beta; o instalador verifica classes e bundles requeridos antes da substituicao. O SHA-256 do pacote esta em plugins/chat-captura IPED/CHECKSUMS.md.
