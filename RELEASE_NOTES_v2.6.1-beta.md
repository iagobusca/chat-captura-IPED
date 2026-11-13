# Release v2.6.1-beta

## Downloads

- `instalador-chat-captura-IPED-2.6.1-beta-verificador-1.3.6.zip` (`94.604.780` bytes | SHA-256: `9D6087BE80B9BE1A329C81FC4D7B8B10CB6FF10EE8098CF2ECEACA214336DE5C`)
- `integridade-verifier.exe` 1.3.6 (`77.843.012` bytes | SHA-256: `24E84B1B014D9677E238A94FD9EBFFB5CD2E8CF5CCA244AF8D47D1ED48C704D4`)
- `integridade-verifier.jar` 1.3.6 (`137.188` bytes | SHA-256: `F7990400A98445B8FA718626DA08145CD974866487650BD443E95427DD015FAC`)
- `iped-viewers-impl-4.3.1-2.6.1-beta.jar` (`1.490.429` bytes | SHA-256: `406238754C5951AA8BA2EAC2E3E0E44D96C7C29F9AF6C4A83AC34F18F6AC9F4B`)

## Segurança

- Correções T1–T10 da spec de vulnerabilidades (anotações, arquivos extras, instalador, histórico, OpenCV, hash esperado, junções NTFS, allowlist HTML, nomes de arquivo, verificador no instalador).
- Allowlist do plugin aceita somente o Verificador 1.3.6.

## Compatibilidade

- Capturas reais de teste: `--verify-only` exit 0 com os mesmos `content=` e `history=`.
