# Especificacao: compatibilidade do plugin com Java 11

## Evidencia

- O IPED de Prova_v2 usa o runtime empacotado em `iped\jre`, OpenJDK 11.0.13. Instalar outro JDK no Windows nao muda automaticamente esse runtime.
- O log de 2026-10-29 14:37:20 registra `UnsupportedClassVersionError`: `iped/viewers/localization/Messages` tinha class file version 65.0, mas o Java 11 aceitava no maximo 55.0. Isso confirma a incompatibilidade de bytecode naquele arranque.
- O mesmo log mostra a excecao durante a criacao do `HexViewerPlus`, seguida por erros no SwingWorker e ao fechar a janela, coerentes com a tela branca e o botao de fechar sem resposta.

## Correcao aplicada

1. Compilar `Messages.java` com `C:\Program Files\Eclipse Adoptium\jdk-11.0.32.101-hotspot\bin\javac.exe --release 11`.
2. Partir de uma copia do JAR completo e substituir somente `iped/viewers/localization/Messages.class`.
3. Validar a integridade do JAR e conferir que as 255 classes, inclusive `Messages.class`, estao em major version 55.
4. Atualizar o pacote principal, o fallback do instalador e o JAR de Prova_v2; criar backups timestamped antes de cada substituicao.
5. Manter o fallback de idioma: uma chave ausente gera um aviso e retorna o nome da chave em vez de abortar a inicializacao Swing.

## Resultado esperado e verificacao funcional

O JAR nao exige bytecode acima do Java 11. Ao abrir Prova_v2, confirmar que a janela aparece e fecha normalmente, que os controles do plugin aparecem e que `Re-Extrair` funciona. O log em disco e anterior a esta instalacao; ele nao confirma ainda o resultado da abertura posterior a correcao.

## Rollback

Com o IPED fechado, restaure `iped-viewers-impl-4.3.1.jar.bak-<timestamp>` ao nome original. Os backups ficam ao lado dos JARs instalados e dos pacotes.


## Atualizacao beta 2.2.1

O HtmlLinkViewer.class que implementa Marcar todos / Desmarcar todos tambem foi compilado com --release 11. A verificacao do JAR de release confirmou major version 55 para todas as 255 classes.
