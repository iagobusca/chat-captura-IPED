# Release v2.6.2-beta

## Downloads

- `instalador-chat-captura-IPED-2.6.2-beta-verificador-1.3.7.zip` (`95.471.318` bytes | SHA-256: `7E8C342F285C55F418A4195B61584AB3F7354F64FEDE61C19CEBD75BB9AA1854`)
- `integridade-verifier.exe` 1.3.7 (`77.850.180` bytes | SHA-256: `D102E17D02F39E5D6E85249E9B9585BC26C3D005E7DBFF2296C22FB2E18E926A`)
- `integridade-verifier.jar` 1.3.7 (`139.632` bytes | SHA-256: `7A137AE3286231246AF335380C3E8716B17A6696552096474563169DB4F5F2EE`)
- `iped-viewers-impl-4.3.1-2.6.2-beta.jar` (`1.496.622` bytes | SHA-256: `5576E9E5CE0BCB6D3C65ECF560403CC0098F0D4A1F8FE18DBC6597238DB9C8AC`)

## Segurança (Fase 2 — P1–P11)

- Desserialização legada restrita à allowlist estrita de classes em `ChatAnnotationStore` (`P1`).
- Validação de caminho relativo com rejeição de `..`, prefixo de drive e caracteres de controle (`P2`–`P3`).
- Desativação explícita de renderização HTML (`html.disable`) em diálogos/componentes Swing e neutralização de injeção HTML/CSV (`P4`–`P8`).
- Substituição de parsing JSON por regex em `ChatAnnotationStore` e `HtmlLinkViewer` por parser estrutural `org.json` e scanner ciente de aspas/colchetes (`P9`).
- Despacho fora da Swing EDT, proteção contra `outputFolder` vazio, pastas auxiliares de recaptura ignoradas no Verificador 1.3.7 e redução dos módulos `jlink` (`P10`).
- Pré-validação em Java puro (magic bytes PNG/JPEG/BMP/GIF/WebP, limites 8192 px / 33,5 MP / 32 MiB) e decodificação Java `ImageIO` (`CV_8UC1`) antes do OpenCV, sem `Imgcodecs.imread` (`P11`).
- Allowlist de `VerifierIntegrity` atualizada exclusivamente para o Verificador 1.3.7.

## Compatibilidade

- Capturas reais de teste: `--verify-only` exit 0 com os mesmos `content=` e `history=`.
