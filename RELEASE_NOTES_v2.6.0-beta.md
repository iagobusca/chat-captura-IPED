# Release v2.6.0-beta

[v2.6.0-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.0-beta) Pre-Release

[iago](http://127.0.0.1:3000/iago) | [compare/v2.6.0-beta...main](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/compare/v2.6.0-beta...main)

## Downloads

- [instalador-chat-captura-IPED-2.6.0-beta-verificador-1.3.5.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.0-beta) (`95.427.705` bytes | SHA-256: `2F67E5EA2D7172D3097DF1872A084F6220D14C2AB4BCE7F276E355F7AC9884F4`)
- [integridade-verifier-1.3.5.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.0-beta) (`77.839.428` bytes | SHA-256: `75FB542BCB53C44C7B17553EEC761C16F17C670F8033DD3F901F80A0897AC42B`)
- [integridade-verifier-1.3.5.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.0-beta) (`133.282` bytes | SHA-256: `31B0CE725708ED53AD9BE319FFE51439940C65BB46EF8593AB78376C07BF46B9`)
- [iped-viewers-impl-4.3.1-2.6.0-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.0-beta) (`1.482.427` bytes | SHA-256: `DFC47C2F050837A7A334E06615D731252E75FDF674F80CEF9AD5EFDD28F24936`)

---

## Otimizado

- Verificador de Integridade **1.3.5**: novo botão **Copiar histórico completo público** e campo **SHA-256 do histórico completo público** no resumo final, gerando linhas determinísticas no formato anonimizado `CAPTURA|*-YYYYMMDD-HHMMSS ...` (ou `CAPTURA|*`) para publicação externa com maior privacidade sem expor os nomes das pastas.
- Verificação dual no modo **Integridade Público**: aceita e valida tanto o histórico completo original (`CAPTURA|<pasta>-YYYYMMDD-HHMMSS ...`) quanto o histórico completo público anonimizado (`CAPTURA|*-YYYYMMDD-HHMMSS ...`), bem como o SHA-256 de qualquer um dos dois formatos.
- Opção CLI `--export-history` passa a imprimir também `SHA-256_HISTORICO_PUBLICO=...`.
- Script `verificador-integridade/build-single-exe.ps1` atualizado com `MSBUILDDISABLENODEREUSE=1`, `DOTNET_CLI_DO_NOT_USE_MSBUILD_SERVER=1`, `-m:1 -nodeReuse:false` e `dotnet build-server shutdown` para evitar travamento do servidor MSBuild/Roslyn ao empacotar o executável.

## Corrigido

- Allowlist de binários confiáveis em `VerifierIntegrity.java` e testes unitários (`VerifierIntegrityHashTest.java` e `test_verifier_java_integration.py`) sincronizados com os binários oficiais do Verificador **1.3.5** (`31B0CE72...` e `75FB542B...`).
- Plugin `2.6.0-beta` recompilado como fat-JAR OpenCV autocontido (`Implementation-Version: 2.6.0-beta`), preservando todas as correções da `2.5.9-beta` (fronteiras `_call:<hex>`, `-?<id>_<id>`, `iped-capture-date-...` e tolerância de anexos).

## Incluido

- Plugin Chat Captura IPED `2.6.0-beta` (`iped-viewers-impl-4.3.1-2.6.0-beta.jar`).
- Verificador de Integridade `1.3.5` em JAR e EXE Windows x64 autocontido.
- ZIP instalador completo (`instalar_captura_chat_zap/`, `dependencies/json-20260719.jar`, `dependencies/opencv-4120.jar`, `opencv_java4120.dll`, bundles de idioma, `LEIA-ME.txt` e binários do Verificador `1.3.5`).

## SHA-256 do Verificador 1.3.5

- JAR: `31B0CE725708ED53AD9BE319FFE51439940C65BB46EF8593AB78376C07BF46B9`
- EXE: `75FB542BCB53C44C7B17553EEC761C16F17C670F8033DD3F901F80A0897AC42B`

Veja [versao_hash.md](versao_hash.md) para os hashes das versoes anteriores.

---

*Tag da versão: [v2.6.0-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/src/tag/v2.6.0-beta)*
