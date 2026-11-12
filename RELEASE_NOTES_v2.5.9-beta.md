# Release v2.5.9-beta

[v2.5.9-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.5.9-beta) Pre-Release

[iago](http://127.0.0.1:3000/iago) | [compare/v2.5.9-beta...main](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/compare/v2.5.9-beta...main)

## Downloads

- [instalador-chat-captura-IPED-2.5.9-beta-verificador-1.3.4.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.5.9-beta) (`94.584.283` bytes | SHA-256: `26014CE5951BE89E37CA2057B25DB359A64CECB65A9547A5CD192849FC4E7A10`)
- [integridade-verifier-1.3.4.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.5.9-beta) (`77.838.404` bytes | SHA-256: `20FAF8A5AD2690A909F09B6A60DFBFE12A271493DA3331595F8BB2A95EF2BCA0`)
- [integridade-verifier-1.3.4.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.5.9-beta) (`132.375` bytes | SHA-256: `B456074C393949FE63DC33B404F539B569830CE92FC657FF43B622D9E03500DD`)
- [iped-viewers-impl-4.3.1-2.5.9-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.5.9-beta) (`1.472.202` bytes | SHA-256: `D881B345A9ACC4F54219867870959EA659FF7C91551458A2DA6192AEAAB35507`)

---

## Otimizado

- Resolucao unificada de fronteiras (`CaptureBoundaryResolver`, `HtmlLinkViewer` e Verificador 1.3.4) para qualquer marcador valido do chat no IPED: mensagens numericas (`^\d+$`), registros de chamada de voz/video/grupo (`^\d+_call:[0-9a-fA-F]+$`), mensagens apagadas/carved (`^-?\d+_\d+$`) e timestamps internos (`iped-capture-date-...`).
- Fat-JAR autocontido do plugin `2.5.9-beta` com classes `org/opencv/**` embutidas, `Implementation-Version: 2.5.9-beta` no `META-INF/MANIFEST.MF` e busca automatica de `opencv_java4120.dll` na raiz do IPED, `plugins/` e `lib/`.
- Tratamento tolerante de anexos em `html-message-v1` (pula anexos nao suportados sem interromper a captura).
- Script `verificador-integridade/build-single-exe.ps1` atualizado com `--disable-build-servers -p:UseSharedCompilation=false` para evitar travamento de servidores MSBuild/Roslyn durante o build do executavel.

## Corrigido

- Falha ao migrar capturas legadas ou executar Re-Extrair quando o ponto inicial/final selecionava um registro de chamada (`_call:<hex>`), um timestamp interno da linha ou uma mensagem com ID composto/removido (`-?<id>_<id>`), normalizando automaticamente `data-iped-capture-id` e `domId`.
- Regras de validacao de `domId`/`id` no `IntegrityVerifier.java` (Verificador 1.3.4) ampliadas nos quatro pontos de checagem JSON/DOM para reconhecer os mesmos formatos de ID aceitos pelo plugin.
- Allowlist em `VerifierIntegrity.java` e testes unitarios (`VerifierIntegrityHashTest.java` e `CaptureBoundaryResolverTest.java`) sincronizados com os binarios 1.3.4 recompilados.

## Incluido

- Plugin Chat Captura IPED `2.5.9-beta` (`iped-viewers-impl-4.3.1-2.5.9-beta.jar`).
- Verificador de Integridade `1.3.4` recompilado em JAR e EXE Windows x64 autocontido.
- ZIP instalador completo (`instalar_captura_chat_zap/`, `dependencies/json-20260719.jar`, `dependencies/opencv-4120.jar`, `opencv_java4120.dll`, bundles de idioma, `LEIA-ME.txt` e binarios do Verificador 1.3.4).

## SHA-256 do Verificador 1.3.4

- JAR: `B456074C393949FE63DC33B404F539B569830CE92FC657FF43B622D9E03500DD`
- EXE: `20FAF8A5AD2690A909F09B6A60DFBFE12A271493DA3331595F8BB2A95EF2BCA0`

Veja [versao_hash.md](versao_hash.md) para os hashes das versoes anteriores.

---

*Tag da versão: [v2.5.9-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/src/tag/v2.5.9-beta)*
