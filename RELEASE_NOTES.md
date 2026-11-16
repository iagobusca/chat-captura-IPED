# Release Notes — Chat Captura IPED

Histórico consolidado das releases `beta` do plugin e do **Verificador de Integridade**.
Para hashes detalhados e versões mais antigas, consulte [CHECKSUMS.md](CHECKSUMS.md), [versao_hash.md](versao_hash.md) e [CHANGELOG.md](CHANGELOG.md).

---

## v2.6.9 (Estável / `main`) / Verificador 1.4.4

- **Tag**: [v2.6.9](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.9) (Release estável na branch `main`)

### Downloads

- [instalador-chat-captura-IPED-2.6.9-verificador-1.4.4.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.9/instalador-chat-captura-IPED-2.6.9-verificador-1.4.4.zip) (`95.520.077` bytes | SHA-256: `7AE66926EF857C35EF73B6192B54EAD7D2E8751E8D984D89CDD5DBAC144A6ABC`)
- [integridade-verifier-1.4.4.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.9/integridade-verifier-1.4.4.exe) (`77.874.266` bytes | SHA-256: `A57CB7E8C15B256DDBDE99899157D8CC2B8AA8B3ABCD8F30D7CACE17F2962291`)
- [integridade-verifier-1.4.4.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.9/integridade-verifier-1.4.4.jar) (`159.189` bytes | SHA-256: `C37D52F2D12A9AB0E954744F84B3E47805D9F3BF8B460A864C70AC06AE4BF7FF`)
- [iped-viewers-impl-4.3.1-2.6.9.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.9/iped-viewers-impl-4.3.1-2.6.9.jar) (`1.513.268` bytes | SHA-256: `725CA7E02538A5B5EEBB043A29868E90515CBCAC7BFBA4CC518A7EEF55150B9C`)

### Novidades e Correções

- **Verificador `1.4.4`**: consolidação do scanner HTML léxico determinístico por máquina de estados (`scanHtmlElements` / `parseHtmlTagStrict`), bloqueando bypasses com apóstrofos/aspas isolados em atributos sem aspas; proteção `LinkOption.NOFOLLOW_LINKS` e limite `maxBytes` em `openFile`; remoção de código morto (`isAllowedPath`); `safeRegularFileSize` sem corrida de symlink; allowlists HTML/MIME simétricas com o plugin; `html.disable` nos componentes Swing; `MAX_SCAN_ENTRIES`; executável x64 autocontido com Java 11 + runtime `.NET 10.0.15` (0 CVEs) e sem tetos artificiais de relatório.
- **Plugin `2.6.9`**: defesa em profundidade Swing com `putClientProperty("html.disable", Boolean.TRUE)` antes da medição de texto em `SearchHitRenderer` (`ChatCapturePanel`) e em `SavedChatPickerDialog`; liberação determinística de WebView/OpenCV/executor em `ChatCaptureLinkedWindow` (`DISPOSE_ON_CLOSE` + `closed.get()`) e `HtmlLinkViewer.dispose()` + guarda `if (disposed)` em `loadFile`; compatibilidade reflexiva com `AttachmentSearcher.openItem` no IPED `4.3.1` oficial; allowlist exclusiva sincronizada com o Verificador `1.4.4` (`C37D52F2...` / `A57CB7E8...`); fat-JAR `Implementation-Version: 2.6.9` (`725CA7E0...`).
- **Instalador**: correções de expansão atrasada em caminhos com `!`, rollback em falhas e atualização de chaves de localização modificadas sem duplicar `json-*.jar`/`opencv-*.jar` em `IPED\lib\`.

---

## v2.6.8 (Estável / `main`) / Verificador 1.4.3

- **Tag**: [v2.6.8](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.8) (Release estável na branch `main`)

### Downloads

- [instalador-chat-captura-IPED-2.6.8-verificador-1.4.3.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.8/instalador-chat-captura-IPED-2.6.8-verificador-1.4.3.zip) (`94.677.720` bytes | SHA-256: `FBD4657A55686A32252C5CF81BBD35ABD1E06C7A44639142080F21ED4BDF5AF7`)
- [integridade-verifier-1.4.3.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.8/integridade-verifier-1.4.3.exe) (`77.874.266` bytes | SHA-256: `17D8F5DCED38029B68849AF11A2358B38B31FD4A01BDAF4BFEA514FC27842E05`)
- [integridade-verifier-1.4.3.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.8/integridade-verifier-1.4.3.jar) (`159.189` bytes | SHA-256: `104F69AABCB86851C2DA905AD6E8428177BDEC1E6D3750580291A8ECD008B382`)
- [iped-viewers-impl-4.3.1.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.8/iped-viewers-impl-4.3.1.jar) (`1.513.265` bytes | SHA-256: `E817A1743BCB50DCF36026769C156E6ACC2796A9446E435DBFAFC501C2B9D058`)

### Novidades e Correções

- Promoção da build `2.6.8-beta` validada para a branch `main` como versão estável **`2.6.8`** (sem sufixo `-beta`), acompanhando o **Verificador de Integridade `1.4.3`**.
- **Verificador `1.4.3`**: remoção do limite de caracteres estimado de relatório (`enforceReportBudget` / `4.194.304`), do limite de `16 MiB` em `atomic` / `atomicBatch` e dos tetos cumulativos entre capturas em `verifyAllBounded` / `--verify-only`; runtime `.NET 10.0.15` (0 CVEs), proteção anti-reparse/zip-bomb no launcher, `safeRegularFileSize` sem corrida de symlink, `html.disable`, limite `MAX_SCAN_ENTRIES` e allowlists HTML/MIME simétricas com o plugin.
- **Plugin `2.6.8`**: compatibilidade binária e de compilação com o IPED `4.3.1` oficial via invocação reflexiva de `AttachmentSearcher.openItem`, limpeza determinística de recursos em `dispose()` (`HtmlLinkViewer` e `ChatCaptureLinkedWindow`), helper `isInsideOpenAnchorTag` para tags `<a\b`, limites em streams `Files.list` e `SensitiveImageMatcher`, e instalador atualizado para sincronizar chaves de localização modificadas sem duplicar `json-*.jar`/`opencv-*.jar` em `lib\`.

---

## v2.6.8-beta / Verificador 1.4.3

- **Tag**: [v2.6.8-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.8-beta) (Pre-Release)

### Downloads

- [instalador-chat-captura-IPED-2.6.8-beta-verificador-1.4.3.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.8-beta/instalador-chat-captura-IPED-2.6.8-beta-verificador-1.4.3.zip) (`96.948.771` bytes | SHA-256: `5593E3EA0701C39806A976273A4C14750BBFF8C204DECD50B8C39DEBAFB9CE81`)
- [integridade-verifier-1.4.3.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.8-beta/integridade-verifier-1.4.3.exe) (`77.867.588` bytes | SHA-256: `723DD7507D44AB14E3FFA723D2838CFC0C3F8B77B53725919A2E1CBCBA145581`)
- [integridade-verifier-1.4.3.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.8-beta/integridade-verifier-1.4.3.jar) (`154.638` bytes | SHA-256: `8CB04812B4BD68C0DB9959DECEB63C9F7D8F8D99CF4BD5B4DC56A9148B305A1D`)
- [iped-viewers-impl-4.3.1-2.6.8-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.8-beta/iped-viewers-impl-4.3.1-2.6.8-beta.jar) (`1.506.195` bytes | SHA-256: `8BD552C41AFD9A3D63A57F06A21FB0AD4A8ECA016FAD1F3A3CADABF3272CFFB3`)

### Corrigido e Melhorado

- **Verificador `1.4.3`**:
  - Remoção do limite artificial de tamanho estimado de relatório (`enforceReportBudget` / `4.194.304` caracteres), do limite de `16 MiB` na gravação de relatórios (`atomic` / `atomicBatch`) e dos tetos cumulativos entre capturas em `verifyAllBounded` / `--verify-only`, permitindo verificar pastas com grandes conjuntos de capturas ou chats completos sem falha de orçamento.
- **Plugin `2.6.8-beta`**:
  - Tolerância a anexos acima do limite individual de `8 MiB` (ex.: vídeos grandes como `VID-*.mp4`), ausentes no caso ou que ultrapassem o orçamento acumulado da captura em `runHtmlMessageCapture`, ignorando apenas o anexo excedente com `WARN` e preservando a mensagem e a criação da pasta final da captura.
  - Allowlist exclusiva sincronizada com o Verificador `1.4.3`, rejeitando `<= 1.4.2`.

---

## v2.6.7-beta / Verificador 1.4.2

- **Tag**: [v2.6.7-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.6.7-beta) (Pre-Release)

### Downloads

- [instalador-chat-captura-IPED-2.6.7-beta-verificador-1.4.2.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.7-beta/instalador-chat-captura-IPED-2.6.7-beta-verificador-1.4.2.zip) (`96.949.777` bytes | SHA-256: `6996BC8F01D514B586C5C5B0680EC548FBDAF348B609178E614E2CA018A286DB`)
- [integridade-verifier-1.4.2.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.7-beta/integridade-verifier-1.4.2.exe) (`77.868.100` bytes | SHA-256: `4859980DC532340427EEF2983B62EA4596BC6B8FA1D6F80462BB76CF03278105`)
- [integridade-verifier-1.4.2.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.7-beta/integridade-verifier-1.4.2.jar) (`155.216` bytes | SHA-256: `08877EFF5F2FA0A1E2F64AD0072490F9400AAA26E51ACD8E18C4D2DEE5F59475`)
- [iped-viewers-impl-4.3.1-2.6.7-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.6.7-beta/iped-viewers-impl-4.3.1-2.6.7-beta.jar) (`1.516.022` bytes | SHA-256: `8B7E1432992C6DD41C41E805A2B3A492AF9E13D689EA49063B78C6AB580F7FDB`)

### Segurança e Corrigido (`F-01..F-10`)

- **Verificador `1.4.2` (`IntegrityVerifier` e launcher `.exe`)**:
  - `F-01`: rejeição de `artifacts` vazio ou duplicado (case-insensitive), amarração de `contentRootHash` ao último evento e verificação do inventário completo de `integridade.json` em `html-message-v1`.
  - `F-02`: reprovação fail-closed em `verifyMessageCapture` para `captureRootHash` divergente e validação estrita de `historyStatus` no `--verify-only`.
  - `F-03`: classificação correta de estados saudáveis de `html-message-v1` e reconciliação em `writeReports` (`isHealthyVerifierItemState`).
  - `F-04`/`F-05`: `html.disable` em todos os `JLabel`/`JCheckBox` do verificador e bloqueio de comentários HTML, CDATA e instruções `<?` em `validateRenderedCaptureHtml`.
  - `F-06`/`F-10`: launcher C# com exclusão segura sem seguir junctions/reparse points, janela de tolerância de 2 min na limpeza de diretórios sem lock e autoteste de entrada ZIP de 0 bytes no `--self-test`.
  - `F-07`/`F-08`/`F-09`: comparação pública com normalização de chaves e vínculo `(domId, key, messageHash)`, rejeição de `integridade.json` com `schemaVersion < 2` em `html-message-v1` e carregamento exclusivo de ícone pelo classpath.
  - Remoção do limite artificial de tamanho estimado de relatório (`enforceReportBudget` / `4.194.304` caracteres), do limite de `16 MiB` na gravação de relatórios (`atomic` / `atomicBatch`) e dos tetos cumulativos entre capturas em `verifyAllBounded` / `--verify-only`, permitindo verificar pastas com grandes conjuntos de capturas ou chats completos sem falha de orçamento.
- **Plugin `2.6.7-beta`**:
  - `F-04`: `html.disable` em diálogos/janelas (`HtmlLinkViewer`, `ChatNoteDialog`, `ChatPreflightDialog`, `ChatCaptureLinkedWindow`).
  - `F-08`: comparação de `migration.json` via `CaptureIntegrity.canonicalJson()`.
  - `F-09`: escape HTML em `VerifierIntegrity.showAlertIfNeeded` e allowlist exclusiva sincronizada com o Verificador `1.4.2`, rejeitando `<= 1.4.1`.
  - Tolerância a anexos acima do limite individual de `8 MiB` (ex.: vídeos grandes como `VID-*.mp4`), ausentes no caso ou que ultrapassem o orçamento acumulado da captura em `runHtmlMessageCapture`, ignorando apenas o anexo excedente com `WARN` e preservando a mensagem e a criação da pasta final da captura.

---

## v2.6.6-beta / Verificador 1.4.1

- **Downloads**:
  - `instalador-chat-captura-IPED-2.6.6-beta-verificador-1.4.1.zip` (`96.101.446` bytes | SHA-256: `A3739FF8AF94A438272777DB0BDDF858A9553A653BB4E46FE73389F7E925B135`)
  - `integridade-verifier.exe` 1.4.1 (`77.864.004` bytes | SHA-256: `A7283005AF86ED5DD0E47C07619DCD1F3A6F4618B10DE3AC208B7F2A1549A29C`)
  - `integridade-verifier.jar` 1.4.1 (`153.719` bytes | SHA-256: `CC2958812B0D56CF60B96641EE75F5776F463E0986503AC0FE15ABF82CE71345`)
  - `iped-viewers-impl-4.3.1-2.6.6-beta.jar` (`1.515.669` bytes | SHA-256: `C0025DB803739F3C9509F4E24DDAC23C1C4E0D77CC751A5DE890102B1968E7A4`)
- **Destaques (`V-01..V-04`, `P-01..P-04`)**:
  - Detecção fail-closed de arquivos extras em capturas legadas no `--verify-only` (`V-01`), bloqueio de raiz como NTFS junction/symlink (`V-02`), cancelamento silencioso com código `0` (`V-03`) e CSP por hash SHA-256 em `integridade.html` sem `onclick=` inline (`V-04`).
  - Validação em `AttachmentHandler.open` (`P-01`), validação de coordenadas/caminhos e CSP por hash SHA-256 nos relatórios HTML do plugin (`P-02`–`P-03`), proteção em `setSelected` (`P-04`) e allowlist exclusiva do Verificador `1.4.1`.

---

## v2.6.5-beta / Verificador 1.4.0

- **Downloads**:
  - `instalador-chat-captura-IPED-2.6.5-beta-verificador-1.4.0.zip` (`96.095.058` bytes | SHA-256: `237BF525B55DEE59AD9C7534E19D7C2B0F6583047C70CA9606034ED97CFF7BF7`)
  - `integridade-verifier.exe` 1.4.0 (`77.862.980` bytes | SHA-256: `11094FA3CCE812AF3421490FF1BC2F3B8100C9B5D7551DBBA7F05F78C8136809`)
  - `integridade-verifier.jar` 1.4.0 (`152.916` bytes | SHA-256: `9CA17D7BA2EC50808D333495716D277F4DA963BBBC946C3EF7F765079F48A6E5`)
  - `iped-viewers-impl-4.3.1-2.6.5-beta.jar` (`1.513.355` bytes | SHA-256: `32044F09D1299D787061B76F2150DF0ED82590C5C01E9123377005DE8407170B`)
- **Destaques**:
  - Seleção de modo (**Integridade Local** vs **Integridade Público**) no início do fluxo GUI, barras de progresso dedicadas, correção global de `alwaysOnTop`/EDT e comparação offline direta em `integridade.html` com resultado `OK`/`FALHA`.
  - Allowlist exclusiva atualizada para o Verificador `1.4.0`.

---

## v2.6.4-beta / Verificador 1.3.9

- **Downloads**:
  - `instalador-chat-captura-IPED-2.6.4-beta-verificador-1.3.9.zip` (`94.640.895` bytes | SHA-256: `1EC278138D3FC4C47003CC9C24B5E731619B32BA806B10B173A4E8205CC03CC8`)
  - `integridade-verifier.exe` 1.3.9 (`77.853.252` bytes | SHA-256: `FF06525056E008A91AABF49D9B98B3E0CEE55CCED3846A89AA76D7B39BC6BC85`)
  - `integridade-verifier.jar` 1.3.9 (`142.687` bytes | SHA-256: `043AFEC21D79B4DF3C8A3DDB42F49CF5CB9467CECD11397E724091DFA310E2A7`)
  - `iped-viewers-impl-4.3.1-2.6.4-beta.jar` (`1.513.352` bytes | SHA-256: `5C69E3548B937DE8FF8085F91C91D07B38E7F5657F415E41F516E1B55302B817`)
- **Destaques**:
  - Decodificação de entidades HTML em `parseHtmlAttributes` no Verificador `1.3.9` e emissão direta de `CAPTURE_CSP` canônico no plugin `2.6.4-beta`, corrigindo falso positivo de política CSP com apóstrofo codificado (`&#39;`).

---

## v2.6.3-beta / Verificador 1.3.8

- **Downloads**:
  - `instalador-chat-captura-IPED-2.6.3-beta-verificador-1.3.8.zip` (`94.635.426` bytes | SHA-256: `F17137E71F5972556A2953A771FB724BDECAB28BFBE63BD75F7DD73E3639444E`)
  - `integridade-verifier.exe` 1.3.8 (`77.852.740` bytes | SHA-256: `B9D77A7F88F7D87D3697B855191427C3CE790EFA827830DEABE263E4E2962DD2`)
  - `integridade-verifier.jar` 1.3.8 (`142.154` bytes | SHA-256: `556B1A652183A343B168057029CCF10006361D8CB53526BA610E81A4D76532B6`)
  - `iped-viewers-impl-4.3.1-2.6.3-beta.jar` (`1.509.222` bytes | SHA-256: `CED5CCFE4C5F1C8A15C52D32E891468B9836F620BB3E3FFBC47198FE41C3AC60`)
- **Otimizado**:
  - Política estrita de versão única no Verificador e no plugin, aceitando exclusivamente o Verificador **1.3.8** (`556B1A65...` e `B9D77A7F...`).
  - Preparo do `Re-Extrair` movido para fora da Swing EDT, decodificação limitada em `createReportThumbnails`, `HTML_GENERATOR_VERSION = 4`, limpeza de temporários/locks com `--self-test` e auditoria NuGet ativa.
- **Corrigido**:
  - Sanitização HTML/CSS/SVG estrutural no plugin e no Verificador, classificação `"registro inválido"` para `integridade.json` corrompido, verificação canônica `recordHash` (`"canonical-v1"`), preservação de `whatsapp.html` em falha de migração, `absolutePath` final, restauração de `.properties` no `:ROLLBACK` e remoção de MD5 decisório no `SensitiveImageMatcher`.

---

## v2.6.2-beta / Verificador 1.3.7

- **Downloads**:
  - `instalador-chat-captura-IPED-2.6.2-beta-verificador-1.3.7.zip` (`95.471.318` bytes | SHA-256: `7E8C342F285C55F418A4195B61584AB3F7354F64FEDE61C19CEBD75BB9AA1854`)
  - `integridade-verifier.exe` 1.3.7 (`77.850.180` bytes | SHA-256: `D102E17D02F39E5D6E85249E9B9585BC26C3D005E7DBFF2296C22FB2E18E926A`)
  - `integridade-verifier.jar` 1.3.7 (`139.632` bytes | SHA-256: `7A137AE3286231246AF335380C3E8716B17A6696552096474563169DB4F5F2EE`)
  - `iped-viewers-impl-4.3.1-2.6.2-beta.jar` (`1.496.622` bytes | SHA-256: `5576E9E5CE0BCB6D3C65ECF560403CC0098F0D4A1F8FE18DBC6597238DB9C8AC`)
- **Segurança (`P1`–`P11`)**:
  - Allowlist estrita na desserialização legada (`P1`), validação estrita de caminhos relativos (`P2`–`P3`), `html.disable` em componentes Swing e neutralização HTML/CSV (`P4`–`P8`), substituição de parsing JSON por regex via `org.json` (`P9`), despacho fora da EDT / pastas auxiliares ignoradas / redução `jlink` (`P10`), pré-validação e decodificação Java `ImageIO` antes do OpenCV sem `Imgcodecs.imread` (`P11`).

---

## v2.6.1-beta / Verificador 1.3.6

- **Downloads**:
  - `instalador-chat-captura-IPED-2.6.1-beta-verificador-1.3.6.zip` (`94.604.780` bytes | SHA-256: `9D6087BE80B9BE1A329C81FC4D7B8B10CB6FF10EE8098CF2ECEACA214336DE5C`)
  - `integridade-verifier.exe` 1.3.6 (`77.843.012` bytes | SHA-256: `24E84B1B014D9677E238A94FD9EBFFB5CD2E8CF5CCA244AF8D47D1ED48C704D4`)
  - `integridade-verifier.jar` 1.3.6 (`137.188` bytes | SHA-256: `F7990400A98445B8FA718626DA08145CD974866487650BD443E95427DD015FAC`)
  - `iped-viewers-impl-4.3.1-2.6.1-beta.jar` (`1.490.429` bytes | SHA-256: `406238754C5951AA8BA2EAC2E3E0E44D96C7C29F9AF6C4A83AC34F18F6AC9F4B`)
- **Segurança**:
  - Correções `T1`–`T10` (anotações, arquivos extras, instalador, histórico, OpenCV, hash esperado, junções NTFS, allowlist HTML, nomes de arquivo e verificação no instalador); allowlist exclusiva do Verificador `1.3.6`.

---

## v2.6.0-beta / Verificador 1.3.5

- **Downloads**:
  - `instalador-chat-captura-IPED-2.6.0-beta-verificador-1.3.5.zip` (`95.427.705` bytes | SHA-256: `2F67E5EA2D7172D3097DF1872A084F6220D14C2AB4BCE7F276E355F7AC9884F4`)
  - `integridade-verifier-1.3.5.exe` (`77.839.428` bytes | SHA-256: `75FB542BCB53C44C7B17553EEC761C16F17C670F8033DD3F901F80A0897AC42B`)
  - `integridade-verifier-1.3.5.jar` (`133.282` bytes | SHA-256: `31B0CE725708ED53AD9BE319FFE51439940C65BB46EF8593AB78376C07BF46B9`)
  - `iped-viewers-impl-4.3.1-2.6.0-beta.jar` (`1.482.427` bytes | SHA-256: `DFC47C2F050837A7A334E06615D731252E75FDF674F80CEF9AD5EFDD28F24936`)
- **Otimizado / Corrigido**:
  - Novo botão **Copiar histórico completo público**, campo **SHA-256 do histórico completo público** e comparação dual no modo **Integridade Público** (`CAPTURA|*-YYYYMMDD-HHMMSS ...` ou `CAPTURA|<pasta>-...`), mais `SHA-256_HISTORICO_PUBLICO=...` no `--export-history` e endurecimento do `build-single-exe.ps1` contra travamento de servidores MSBuild/Roslyn.

---

## v2.5.9-beta / Verificador 1.3.4

- **Downloads**:
  - `instalador-chat-captura-IPED-2.5.9-beta-verificador-1.3.4.zip` (`94.584.283` bytes | SHA-256: `26014CE5951BE89E37CA2057B25DB359A64CECB65A9547A5CD192849FC4E7A10`)
  - `integridade-verifier-1.3.4.exe` (`77.838.404` bytes | SHA-256: `20FAF8A5AD2690A909F09B6A60DFBFE12A271493DA3331595F8BB2A95EF2BCA0`)
  - `integridade-verifier-1.3.4.jar` (`132.375` bytes | SHA-256: `B456074C393949FE63DC33B404F539B569830CE92FC657FF43B622D9E03500DD`)
  - `iped-viewers-impl-4.3.1-2.5.9-beta.jar` (`1.472.202` bytes | SHA-256: `D881B345A9ACC4F54219867870959EA659FF7C91551458A2DA6192AEAAB35507`)
- **Otimizado / Corrigido**:
  - Resolução unificada de fronteiras para mensagens numéricas (`^\d+$`), chamadas (`^\d+_call:[0-9a-fA-F]+$`), IDs carved/apagados (`^-?\d+_\d+$`) e timestamps (`iped-capture-date-...`), fat-JAR autocontido com classes `org/opencv/**` e tratamento tolerante de anexos em `html-message-v1`.
