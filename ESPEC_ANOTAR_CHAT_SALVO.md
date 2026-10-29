# Especificacao: Anotar chat ja salvo

## Objetivo
Permitir anotar um chat ja capturado/salvo sem obrigar recaptura completa.

## Fluxo
1. Analista clica em `Anotar chat salvo...`.
2. Escolhe a pasta de saida que contem `capturas-index.json`.
3. Dialogo lista pastas com `manifest.json`.
4. Analista seleciona um chat.
5. Plugin resolve o chat de origem no IPED.
6. Chat abre em modo de anotacao.
7. Anotacoes existentes sao carregadas de `anotacoes.json`.
8. Analista adiciona/edita notas e links.
9. Botao `Salvar anotacoes` grava `anotacoes.json`.
10. Opcionalmente `Re-Extrair` regenera HTML/PNG.

## Componentes
- `SavedChatPickerDialog`
- `HtmlLinkViewer.annotateSavedChat()`
- `HtmlLinkViewer.saveAnnotations()`
- `ChatCapturePanel` botoes `Anotar chat salvo...` e `Salvar anotacoes`
- `ChatCapturePanel.setOutputFolder(File)`

## Criterios de aceite
- Compila sem erros.
- Nao quebra captura normal.
- Carrega anotacoes existentes.
- Salva anotacoes sem recapturar.
- Falha de forma controlada se chat de origem nao for encontrado.

## Testes executados
- Compilacao Java 11 do source completo: sucesso.
- Empacotamento em JAR: sucesso.
- Instalacao em IPED de teste: sucesso.
## Atualizacoes recentes

- Reutiliza a pasta de destino ao anotar chat salvo.
- Botao renomeado para Salvar Trecho-Notas.
- Painel externo de anotacoes e busca lateral no whatsapp.html.

