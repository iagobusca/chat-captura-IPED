# Especificação Técnica de Correção — Auditoria de Segurança e Confiabilidade (Fase 3)

- **Status:** **Aplicada e verificada** (plugin `2.6.7-beta` + Verificador `1.4.2` compilados, allowlists e pacote `.zip` atualizados e validados)
- **Escopo**:
  - Plugin IPED (`chat-captura IPED`): `v2.6.6-beta` → **`v2.6.7-beta`**
  - Verificador de Integridade (`verificador-integridade`): `v1.4.1` → **`v1.4.2`**
- **Base de Classificação**: Skill `encontrar-vulnerabilidade` (`security-vulnerability` vs. `functional-bug`, CVSS v4.0 / CWE)
- **Total de Correções Especificadas**: **10 itens** (`F-01` a `F-10`), divididos em **3 Críticos/Altos (`High`)**, **4 Médios (`Medium`)** e **3 Baixos (`Low`)**.

---

## Sumário Executivo das Alterações por Arquivo

| Arquivo Alvo | Findings Atendidos | Resumo da Mudança Realizada |
|---|---|---|
| [IntegrityVerifier.java](verificador-integridade/src/IntegrityVerifier.java) | `F-01`, `F-02`, `F-03`, `F-04`, `F-05`, `F-07`, `F-08`, `F-09` | Vincula `declaredRoot` ao último evento e valida unicidade/completude de `artifacts` em `verifyHistory` + `verify` (`html-message-v1`, rejeitando `schemaVersion < 2`); bloqueia bypass de `captureRootHash` no CLI (`isConsistentLocalHistoryStatus`) e em `verifyMessageCapture`; corrige `writeReports` com `isHealthyVerifierItemState`; adiciona `plainLabel`/`plainCheckBox` (`html.disable`); bloqueia `<!--`, `-->`, `<![cdata[`, `<?` em `validateRenderedCaptureHtml` (`presentationVersion` `0` e `2`); substitui comparação por `PublicMessageIndex`/`parsePublicMessageIndex`; carrega ícone exclusivamente via classpath em `verifierIcon`; atualiza `VERIFIER_VERSION = "1.4.2"`. |
| [Program.cs](verificador-integridade/launcher/Program.cs) | `F-06`, `F-10` | Adiciona `ShouldCleanupStaleTempDirectory` (janela de graça de `2 min` para diretórios `instance-*` sem `.lock`) e `DeleteDirectorySafely` com proteção contra `FileAttributes.ReparsePoint`; corrige `ExtractArchiveSafely` usando `string.IsNullOrEmpty(entry.Name)` para preservar arquivos de `0` bytes; adiciona `--self-test` (`RunLauncherSelfTest`) cobrindo ZIP de `0` bytes e expiração de diretórios temporários. |
| [HtmlLinkViewer.java](src/iped/viewers/HtmlLinkViewer.java) | `F-04`, `F-08` | Aplica `ChatCapturePanel.plainLabel`/`plainCheckBox` nos controles dinâmicos e de recaptura; substitui comparação `JSONObject.toString().equals(...)` de `migration.json` por `CaptureIntegrity.canonicalJson`; atualiza `CHAT_CAPTURE_PLUGIN_VERSION = "2.6.7-beta"`. |
| [ChatCapturePanel.java](src/iped/viewers/ChatCapturePanel.java) | `F-04` | Torna `plainLabel` e `plainCheckBox` `static` em pacote para reutilização nas janelas/diálogos (mantendo `linkedDestinationLabel` como único `JLabel` com HTML intencional escapado). |
| [ChatCaptureLinkedWindow.java](src/iped/viewers/ChatCaptureLinkedWindow.java), [ChatNoteDialog.java](src/iped/viewers/ChatNoteDialog.java), [ChatPreflightDialog.java](src/iped/viewers/ChatPreflightDialog.java) | `F-04` | Aplicam `ChatCapturePanel.plainLabel` e `putClientProperty("html.disable", Boolean.TRUE)` em `JLabel`/`JCheckBox`. |
| [VerifierIntegrity.java](src/iped/viewers/VerifierIntegrity.java) | `F-09` | Adiciona `htmlEscape(String)` e escapa `f.getFileName()` e `hash` em `showAlertIfNeeded`; atualiza allowlist exclusiva (`VALID_JAR_HASHES`/`VALID_EXE_HASHES`) para os binários `1.4.2`. |
| [ChatAnnotationStore.java](src/iped/viewers/ChatAnnotationStore.java) | `F-10` | Auditado: `sha1Hex` (`L461-L473`) já lança `IllegalStateException("SHA-1 unavailable", e)` sem fallback `hashCode()`. |
| [instalar_captura_chat_zap.bat](instalar_captura_chat_zap/instalar_captura_chat_zap.bat) | Release | Atualiza versões exibidas (`2.6.7-beta` / `1.4.2`) e hashes SHA-256 esperados do fat-JAR do plugin e do verificador (`JAR` e `EXE`). |

---

## Detalhamento Técnico e Patches Prontos para Aplicação

---

### F-01 — Desacoplamento entre `declaredRoot`/`artifacts` e a cadeia `events` + ausência de verificação de completude de arquivos em `verifyHistory`
- **Severidade**: `High` | **Categoria**: `security-vulnerability` | **CWE**: `CWE-345` (Insufficient Verification of Data Authenticity)
- **Arquivo**: [IntegrityVerifier.java](verificador-integridade/src/IntegrityVerifier.java#L971-L1535)

#### Diagnóstico
1. `verifyHistory` não rejeitava `artifacts.length() == 0` nem caminhos duplicados após normalização case-insensitive.
2. Em capturas `html-message-v1`, faltava garantir no fluxo `verify` que todos os arquivos presentes em `c.allowedFiles` (exceto metadados próprios de integridade/auditoria) constassem explicitamente em `integrity.optJSONArray("artifacts")`.
3. `verifyHistory` verificava a cadeia de `events`, mas não vinculava o `contentRootHash` do último evento (`lastEventRoot`) ao `declaredRoot` de topo do `integridade.json`.

#### Implementação Aplicada (`IntegrityVerifier.java` — `#L971-L1023`, `#L1482-L1535`)
- Em `verifyHistory(Capture c, JSONObject record)` (`#L1482`):
  - Rejeita `artifacts == null || artifacts.length() == 0 || events == null || events.length() == 0`.
  - Detecta caminhos duplicados após normalização case-insensitive em `artifacts`.
  - Valida monotonicidade de `sequence`, cadeia de hashes e verifica que o `contentRootHash` do último evento (`lastEventRoot`) coincide exatamente (case-insensitive) com `declaredRoot`.
- No fluxo `html-message-v1` de `verify` (`#L971-L1023`):
  - Garante que todos os arquivos de `c.allowedFiles` (exceto `manifest.json`, `integridade.json` e `reextracao-auditoria.jsonl`) constem em `integrity.optJSONArray("artifacts")`.

---

### F-02 — Bypass no modo CLI `--verify-only` e contagem `ok` quando `captureRootHash` de `manifest.json` diverge
- **Severidade**: `High` | **Categoria**: `security-vulnerability` + `functional-bug` | **CWE**: `CWE-345`, `CWE-697`
- **Arquivo**: [IntegrityVerifier.java](verificador-integridade/src/IntegrityVerifier.java#L161-L166) (e `#L341`, `#L1151`)

#### Diagnóstico e Correção Aplicada
1. Em `verifyMessageCapture` (`#L1151`), divergência de `captureRootHash` em `manifest.json` passa a lançar `IOException` (falha fechada).
2. Adicionado `isConsistentLocalHistoryStatus(String historyStatus)` (`#L161-L166`) exigindo correspondência exata com um dos três estados locais consistentes válidos (`"consistente com registro local"`, `"consistente com registro local (manifesto de mensagens)"` e a combinação dos dois), substituindo `.startsWith(...)` em `runCli` (`#L341`) e no resumo de capturas ok.

---

### F-03 — Relatório `integridade.html` reporta `0 íntegros` e estiliza como `bad` todos os itens de capturas `html-message-v1`
- **Severidade**: `High` | **Categoria**: `functional-bug`
- **Arquivo**: [IntegrityVerifier.java](verificador-integridade/src/IntegrityVerifier.java#L167-L178) (`writeReports` em `#L1704-L1736`)

#### Correção Aplicada
Substituído `"integro".equals(i.state)` por `isHealthyVerifierItemState(i.state)` (`#L167-L178`) tanto no contador de itens íntegros (`#L1729`) quanto na classe CSS `<td class="ok|bad">` (`#L1736`), cobrindo `"integro"`, `"anexo íntegro"`, `"mensagem estruturada"`, `"anotações íntegras"`, `"reconciliação por mensagem íntegra"`, `"registro de migração íntegro"`, `"anotações legadas preservadas"`, `"inalterada desde a captura anterior"`, `"alterada desde a captura anterior"` e `"adicionada desde a captura anterior"`.

---

### F-04 — Injeção Swing BasicHTML (`<html>...`) em `JCheckBox` e `JLabel` sem `html.disable`
- **Severidade**: `Medium` | **Categoria**: `security-vulnerability` | **CWE**: `CWE-79`, `CWE-116`
- **Arquivos**:
  - [IntegrityVerifier.java](verificador-integridade/src/IntegrityVerifier.java#L149-L160)
  - [ChatCapturePanel.java](src/iped/viewers/ChatCapturePanel.java#L43-L55)
  - [HtmlLinkViewer.java](src/iped/viewers/HtmlLinkViewer.java#L1397-L1704)
  - [ChatCaptureLinkedWindow.java](src/iped/viewers/ChatCaptureLinkedWindow.java#L65)
  - [ChatNoteDialog.java](src/iped/viewers/ChatNoteDialog.java#L42-L61)
  - [ChatPreflightDialog.java](src/iped/viewers/ChatPreflightDialog.java#L43-L127)

#### Diagnóstico
O Swing interpreta qualquer texto iniciado por `<html>` em `JLabel` e `JCheckBox` via `BasicHTML` (capaz de requisitar recursos externos ou falsear visualmente o diálogo) a menos que `putClientProperty("html.disable", Boolean.TRUE)` esteja definido. Em vários componentes de `IntegrityVerifier`, `HtmlLinkViewer`, `ChatCaptureLinkedWindow`, `ChatNoteDialog` e `ChatPreflightDialog`, rótulos e caixas de seleção eram instanciados com `new JLabel(...)` / `new JCheckBox(...)` sem `html.disable`.

#### Implementação Aplicada (`F-04`)
1. **`IntegrityVerifier.java` (`#L149-L160`)**: adicionados `static JLabel plainLabel(String text)` e `static JCheckBox plainCheckBox(String text, boolean selected)` com `putClientProperty("html.disable", Boolean.TRUE)` e convertidos todos os `JLabel` e `JCheckBox` do verificador para usá-los (`#L454`, `#L548`, `#L590-L591`, `#L615`, `#L626`, `#L679`, `#L716`, `#L725-L726`, `#L857`, `#L897`).
2. **`ChatCapturePanel.java` (`#L43-L55`)**: `plainLabel` e `plainCheckBox` promovidos para `static` em pacote (mantido apenas `linkedDestinationLabel = new JLabel(" ")` pois exibe HTML intencional devidamente escapado e verificado por `SwingHtmlDisableTest`).
3. **`HtmlLinkViewer.java` (`#L1397`, `#L1405`, `#L1481`, `#L1486`, `#L1493`, `#L1704`)**: todos os `JLabel` e `JCheckBox` convertidos para `ChatCapturePanel.plainLabel` / `ChatCapturePanel.plainCheckBox`.
4. **`ChatCaptureLinkedWindow.java` (`#L65`), `ChatNoteDialog.java` (`#L42`, `#L56`, `#L61`), `ChatPreflightDialog.java` (`#L43`, `#L126-L127`)**: `JLabel` e `JCheckBox` (`toggle.putClientProperty("html.disable", Boolean.TRUE)`) protegidos com `html.disable`.

---

### F-05 — Comentários HTML (`<!-- ... -->`) e seções CDATA aceitos pelo validador regex de `whatsapp.html`
- **Severidade**: `Medium` | **Categoria**: `security-vulnerability` | **CWE**: `CWE-436`, `CWE-185`
- **Arquivo**: [IntegrityVerifier.java](verificador-integridade/src/IntegrityVerifier.java#L1243)

#### Correção Aplicada
`validateRenderedCaptureHtml` (`#L1243`) rejeita explicitamente `<!--`, `-->`, `<![cdata[` (case-insensitive via `Locale.ROOT`) e `<?` com `IOException`, tanto para `presentationVersion == 0` quanto para `presentationVersion == 2`.

---

### F-06 — Janela TOCTOU na limpeza de `instance-*` e deleção recursiva sem proteção contra `ReparsePoint` em `Program.cs`
- **Severidade**: `Medium` | **Categoria**: `security-vulnerability` | **CWE**: `CWE-367`, `CWE-59`
- **Arquivo**: [Program.cs](verificador-integridade/launcher/Program.cs#L218-L301)

#### Correção Aplicada
1. Adicionado `ShouldCleanupStaleTempDirectory(string fullPath, DateTime nowUtc)` (`#L258`) exigindo janela de graça de `2` minutos quando `.lock` estiver ausente antes de considerar `instance-*` órfão em `CleanupStaleTempDirectories` (`#L218-L234`).
2. Adicionado `DeleteDirectorySafely(string rootPath)` (`#L275-L300`), evitando travessia recursiva de diretórios marcados com `FileAttributes.ReparsePoint` e usado tanto na limpeza inicial quanto no `finally` (`#L114`).

---

### F-07 — `comparePublicMessageManifest` ignora `localByKey`/`referenceByKey` e `publicMessageMap` faz lookup case-sensitive
- **Severidade**: `Medium` | **Categoria**: `functional-bug` | **CWE**: `CWE-178`
- **Arquivo**: [IntegrityVerifier.java](verificador-integridade/src/IntegrityVerifier.java#L768-L820)

#### Correção Aplicada
Substituída a modelagem antiga de `comparePublicMessageManifest` / `publicMessageMap` por `PublicMessageIndex` (`#L790`) e `parsePublicMessageIndex(JSONArray rows)` (`#L795`), enquanto `publicMessageMap` (`#L814`) e `publicMessageDomMap` (`#L815`) delegam a ele:
- Normaliza `key` e `messageHash` para minúsculas (`Locale.ROOT`).
- Rejeita chaves (`key`) ou `domId` duplicados mesmo com variação de caixa.
- Valida a consistência completa da tupla `(domId, key, messageHash)` durante a comparação pública, reportando `"chave_divergente"` quando aplicável.

---

### F-08 — Comparação não-determinística `migration.toString().equals(migrationFile.toString())` em `HtmlLinkViewer` e aceitação de `integridade.json` com `schemaVersion < 2` em `html-message-v1`
- **Severidade**: `Low` | **Categoria**: `functional-bug`
- **Arquivos**:
  - [HtmlLinkViewer.java](src/iped/viewers/HtmlLinkViewer.java#L2186)
  - [IntegrityVerifier.java](verificador-integridade/src/IntegrityVerifier.java#L971-L1023)

#### Correção Aplicada
1. `HtmlLinkViewer.java` (`#L2186`): substituído por `if (!CaptureIntegrity.canonicalJson(migration).equals(CaptureIntegrity.canonicalJson(migrationFile)))`.
2. `IntegrityVerifier.java` (`#L971-L1023`): o ramo `html-message-v1` de `verify` agora lança `IOException` quando `integrity != null && schemaVersion < 2` e garante que todo arquivo em `c.allowedFiles` (exceto `manifest.json`, `integridade.json`, `reextracao-auditoria.jsonl`) esteja registrado em `integrity.optJSONArray("artifacts")`.

---

### F-09 — Escape de `f.getFileName()` em `VerifierIntegrity.showAlertIfNeeded` e carregamento seguro de ícone em `IntegrityVerifier.verifierIcon`
- **Severidade**: `Low` | **Categoria**: `security-vulnerability` | **CWE**: `CWE-116`, `CWE-427`, `CWE-400`
- **Arquivos**:
  - [VerifierIntegrity.java](src/iped/viewers/VerifierIntegrity.java#L235-L278)
  - [IntegrityVerifier.java](verificador-integridade/src/IntegrityVerifier.java#L87-L100)

#### Correção Aplicada
1. `VerifierIntegrity.java` (`#L235`, `#L275-L277`): adicionado `static String htmlEscape(String value)` e aplicado ao nome do arquivo (`f.getFileName()`) e ao hash exibido no diálogo HTML de alerta.
2. `IntegrityVerifier.java` (`#L87`): removida a busca relativa no diretório de trabalho atual (`new File(...)`); `verifierIcon()` carrega exclusivamente do classpath via `getResource("/imagem doc/verificador-icon.png")` e `getResource("/verificador-icon.png")` (com fallback vetorial seguro).

---

### F-10 — Fallback inseguro `hashCode()` em `ChatAnnotationStore.sha1Hex` e correção de entradas de `0` bytes em `Program.cs`
- **Severidade**: `Low` | **Categoria**: `functional-bug` + `security-vulnerability` | **CWE**: `CWE-328`
- **Arquivos**:
  - [ChatAnnotationStore.java](src/iped/viewers/ChatAnnotationStore.java#L461-L473)
  - [Program.cs](verificador-integridade/launcher/Program.cs#L236-L257)

#### Diagnóstico e Correção Aplicada
1. `ChatAnnotationStore.sha1Hex` (`#L461-L473`): auditado no código atual; já lança `IllegalStateException("SHA-1 unavailable", e)` sem qualquer fallback para `hashCode()`.
2. `Program.cs` (`ExtractArchiveSafely`, `#L236-L257`): substituída a detecção de diretório `entry.Length == 0` por `EndsWith("/")`, `EndsWith("\\")` e `string.IsNullOrEmpty(entry.Name)`, garantindo extração correta de arquivos regulares vazios (`0` bytes).

---

## Plano de Testes de Regressão Executado

1. [SecurityRegressionTest.java](verificador-integridade/tests/SecurityRegressionTest.java): `6` novos testes cobrindo `F-01`, `F-02`, `F-03`, `F-05`, `F-07` e `F-08`.
2. [SwingHtmlDisableTest.java](tests/iped/viewers/SwingHtmlDisableTest.java): expandido com `verifyChatNoteDialog`, `verifyChatPreflightDialog` e `verifyChatCaptureLinkedWindow` (`F-04`).
3. [CaptureIntegrityTest.java](tests/iped/viewers/CaptureIntegrityTest.java): adicionado `canonicalJsonEquivalenceAndVerifierHtmlEscaping` (`F-08`, `F-09`).
4. [VerifierIntegrityHashTest.java](tests/iped/viewers/VerifierIntegrityHashTest.java): atualizado exclusivamente para `EXPECTED_JAR_142` / `EXPECTED_EXE_142` com verificação explícita de rejeição de `<= 1.4.1`.
5. [test_verifier_java_integration.py](teste_antes_de_aplicar/test_verifier_java_integration.py): atualizado para `1.4.2` (`--version`, `--self-test`) e acrescido de testes de regressão `--verify-only` para `captureRootHash` adulterado + downgrade `schemaVersion: 1` (`F-02`, `F-08`) e carregamento exclusivo do ícone pelo classpath (`F-09`).
6. [Program.cs](verificador-integridade/launcher/Program.cs#L302): `RunLauncherSelfTest()` (`--self-test`) exercitando extração ZIP de `0` bytes (`F-10`) e janela de graça/limpeza temporária (`F-06`).

---

## Checklist de Build, Versionamento e Atualização de Hashes (Executado)

1. **Constantes de versão**:
   - `IntegrityVerifier.VERIFIER_VERSION = "1.4.2"` (`#L38`)
   - `HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION = "2.6.7-beta"` (`#L150`)
2. **Build do Verificador `1.4.2`**:
   - Compilado `verificador-integridade\integridade-verifier.jar` via `verificador-integridade\build.bat` (JDK 8, `155.216` bytes, SHA-256 `08877EFF5F2FA0A1E2F64AD0072490F9400AAA26E51ACD8E18C4D2DEE5F59475`) e copiado para `instalar_captura_chat_zap\integridade-verifier.jar`.
   - Compilado `verificador-integridade\integridade-verifier.exe` via `verificador-integridade\build-single-exe.ps1 -OfflineBuild` (`77.868.100` bytes, SHA-256 `4859980DC532340427EEF2983B62EA4596BC6B8FA1D6F80462BB76CF03278105`) e copiado para `instalar_captura_chat_zap\integridade-verifier.exe`; validado com `--version` e `--self-test`.
3. **Allowlist exclusiva SHA-256 em [VerifierIntegrity.java](src/iped/viewers/VerifierIntegrity.java#L21-L22)**:
   - Política estrita de versão única: `VALID_JAR_HASHES` e `VALID_EXE_HASHES` aceitam **exclusivamente** os hashes da versão `1.4.2` (binários `<= 1.4.1` rejeitados).
4. **Build do Plugin `2.6.7-beta` e Pacote de Distribuição**:
   - Empacotados `instalar_captura_chat_zap\iped-viewers-impl-4.3.1-2.6.7-beta.jar` e `instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar` (Java 11 bytecode `major=55`, `1.506.206` bytes, SHA-256 `2A1E409C6B2229425178E8528D47AE0D001C85B8DFD272ABB54D1574452E3327`).
   - Atualizado [instalar_captura_chat_zap.bat](instalar_captura_chat_zap/instalar_captura_chat_zap.bat) e gerado `instalador-chat-captura-IPED-2.6.7-beta-verificador-1.4.2.zip` (`96.949.777` bytes, SHA-256 `6996BC8F01D514B586C5C5B0680EC548FBDAF348B609178E614E2CA018A286DB`).
5. **Documentação de Versão e Hashes**:
   - Atualizados [CHECKSUMS.md](CHECKSUMS.md), [versao_hash.md](versao_hash.md), [CHANGELOG.md](CHANGELOG.md), [LEIA-ME.txt](LEIA-ME.txt), [README.md](README.md), [verificador-integridade\README.md](verificador-integridade/README.md), [verificador-integridade\doc.md](verificador-integridade/doc.md) e [verificador-integridade\CHANGELOG.md](verificador-integridade/CHANGELOG.md).
6. **Validação Completa Executada**:
   - `teste_antes_de_aplicar\run_all_tests.ps1` (`ALL_RUN_ALL_TESTS_PASSED`, incluindo `28` testes em `test_verifier_java_integration.py`, `SecurityRegressionTest`, `SwingHtmlDisableTest`, `CaptureIntegrityTest`, `VerifierIntegrityHashTest` e `IndexHashParityTest`).
   - `test_verifier_java_integration.py` com `TEST_VERIFIER_EXE` apontando para `verificador-integridade\integridade-verifier.exe` (`28` testes OK).
   - Scripts em `tests\run_capture_header_mime_regression.ps1`, `tests\run_svg_sanitizer_compatibility.ps1`, `tests\run_verifier_embedded_image_compatibility.ps1`, `verificador-integridade\prototype\test-security-spec.ps1` (`8 PASS; 0 FAIL`) e `instalar_captura_chat_zap\validate-plugin-jar.ps1` aprovados; `integridade-verifier.exe --version` e `--self-test` retornando código `0` (`Verificador de Integridade 1.4.2` / `SELF_TEST_OK 1.4.2`). (Nota: `verificador-integridade\prototype\test-two-phase-discovery.ps1` aprovou os 3 primeiros blocos e falhou na linha `78` em PowerShell 5.1 por depender de `[IO.Path]::GetRelativePath` do .NET Core/PowerShell 7+.)
