# Release v2.6.3-beta

## Downloads

- `instalador-chat-captura-IPED-2.6.3-beta-verificador-1.3.8.zip` (`94.635.426` bytes | SHA-256: `F17137E71F5972556A2953A771FB724BDECAB28BFBE63BD75F7DD73E3639444E`)
- `integridade-verifier.exe` 1.3.8 (`77.852.740` bytes | SHA-256: `B9D77A7F88F7D87D3697B855191427C3CE790EFA827830DEABE263E4E2962DD2`)
- `integridade-verifier.jar` 1.3.8 (`142.154` bytes | SHA-256: `556B1A652183A343B168057029CCF10006361D8CB53526BA610E81A4D76532B6`)
- `iped-viewers-impl-4.3.1-2.6.3-beta.jar` (`1.509.222` bytes | SHA-256: `CED5CCFE4C5F1C8A15C52D32E891468B9836F620BB3E3FFBC47198FE41C3AC60`)

## Otimizado

- Politica estrita de versao unica no Verificador e no plugin: removidos todos os hashes de versoes anteriores (`<= 1.3.7`), aceitando exclusivamente os binarios oficiais do Verificador **1.3.8** (`556B1A65...` e `B9D77A7F...`).
- Preparo do `Re-Extrair` movido para fora da Swing EDT (`HtmlLinkViewer`), decodificacao limitada de imagens por `ImageReader`/`Subsampling` em `createReportThumbnails` e `whatsapp.html` atualizado para `HTML_GENERATOR_VERSION = 4` com fechamento CSS na barra de busca.
- Launcher `.exe` do Verificador **1.3.8** com limpeza automatica de diretorios temporarios/locks obsoletos (`--self-test`) e auditoria NuGet ativa no build padrao (`build-single-exe.ps1`).

## Corrigido

- Sanitizacao HTML/CSS/SVG estrutural no plugin (`HtmlLinkViewer`) e endurecimento da allowlist e normalizacao CSS/SVG no Verificador **1.3.8**, preservando marcadores literais sem quebrar o DOM, bloqueando `<template>`, `<style>`, `<animate*>`, `<set>`, `<mpath>`, handlers e `url(...)` com aspas, alem de escaping estrito em `json()`, `js()`, `html()` e `writeWhatsAppHtml`.
- Classificacao `"registro inválido"` para `integridade.json` corrompido sem mascarar rejeicao de juncoes NTFS, verificacao canonica `recordHash` (`"canonical-v1"`) com retrocompatibilidade somente-leitura para eventos legados, e extracao ZIP segura para entradas de 0 byte no launcher `.exe`.
- Preservacao do `whatsapp.html` quando a migracao falha antes do backup, uso do `absolutePath` final em `hashes.txt`/`Lista de Arquivos.csv`, `preflight` respeitando cancelamento no dialogo de confirmacao, propagacao real de falhas em `persist()`/`persistOrThrow()`, rejeicao de `\u+041`, remocao de MD5 decisorio em `SensitiveImageMatcher` e restauracao de `.properties` no `:ROLLBACK` de `instalar_captura_chat_zap.bat`.
