# Especificacao Tecnica de Correcao — Plugin, Verificador e Instalador (`chat-captura IPED` / `verificador-integridade`)

> Escopo: eliminar todas as falhas funcionais, incompatibilidades causadas na distribuicao base do IPED `4.3.1` e vulnerabilidades de seguranca identificadas na auditoria (`encontrar-vulnerabilidade`: SCA + SAST + CWE + contrato ponta a ponta) sobre `chat-captura IPED`, `verificador-integridade` e `instalar_captura_chat_zap`, **preservando integralmente a ideia, interface e funcionalidades** do plugin e do verificador. Vulnerabilidades isoladas de bibliotecas originais de terceiros do IPED base fora deste escopo nao fazem parte desta especificacao, exceto onde o plugin/instalador substitui ou duplica artefatos em `IPED\lib`.

---

## Secao A — Impactos e Falhas Causadas na Distribuicao Base do IPED `4.3.1`

### A-01 (`H-01`) — Incompatibilidade de compilacao e `NoSuchMethodError` em runtime contra o IPED `4.3.1` oficial
- **Arquivo**: `src/iped/viewers/HtmlLinkViewer.java` (`openRecaptureChat`, chamada `attachSearcher.openItem(query, null, job.sourceName)`).
- **Problema**: A distribuicao oficial intacta do IPED `4.3.1` (`iped-viewers-api-4.3.1.jar`) declara 8 metodos em `iped.viewers.api.AttachmentSearcher` e **nao** possui `openItem(String, String)` nem `openItem(String, String, String)`. Compilar o plugin contra o `iped-4.3.1.zip` oficial falha com 1 erro de simbolo inexistente (`HtmlLinkViewer.java:2533`); executar re-extracao em um IPED `4.3.1` oficial sem API modificada dispararia `NoSuchMethodError`.
- **Correcao**:
  1. Introduzir o helper reflexivo `openSearcherItem(AttachmentSearcher searcher, String query, String sourceName)`:
     - Tenta `searcher.getClass().getMethod("openItem", String.class, String.class, String.class)` e invoca `(query, null, sourceName)`.
     - Se inexistente, tenta fallback `openItem(String.class, String.class)` com `(query, null)`.
     - Se ausente (IPED `4.3.1` oficial sem extensao), registra log de aviso claro (`LOGGER.warn(...)`) uma unica vez ou por tentativa e retorna `false` graciosamente, sem excecao fatal.
  2. Validacao obrigatoria: compilar todos os `.java` de `src/iped/viewers/` contra os JARs extraidos diretamente do `iped-4.3.1.zip` oficial (+ `opencv-4120.jar`) com **0 erros**.

### A-02 (`H-02`) — Duplicacao de classes no `lib\` do IPED, bugs de fluxo no `.bat` / localizacao e artefatos obsoletos
- **Arquivos**: `instalar_captura_chat_zap/instalar_captura_chat_zap.bat`, `instalar_captura_chat_zap/install-localization.ps1`, `instalar_captura_chat_zap/validate-plugin-jar.ps1`, artefatos de distribuicao.
- **Problema**:
  1. `instalar_captura_chat_zap.bat` atualiza `lib\json-20250517.jar` (necessario pelo `Class-Path` dos manifests de `iped.jar` e `iped-search-app.jar`, mitigando inclusive a `CVE-2026-59171` do `org.json:json:20250517` original), mas tambem copia `lib\json-20260719.jar` e `lib\opencv-4120.jar` mesmo com `org/opencv/**` ja empacotado dentro de `iped-viewers-impl-4.3.1.jar` (exigencia do `Class-Path` do `MANIFEST.MF` de `iped.jar`). Isso cria duas copias das mesmas classes (`org.json.*` e `org.opencv.*`) em `IPED\lib\`.
  2. No loop `for /L %%L` de deteccao de `IPED_ROOT`, o `.bat` nao verifica `if "!IPED_ROOT!"==""`, podendo sobrescrever o diretorio alvo caso existam multiplos candidatos.
  3. Erros nas etapas de copia/backup de JSON nao disparam `call :ROLLBACK`, arriscando deixar o IPED alvo em estado parcial.
  4. `install-localization.ps1` apenas adiciona chaves ausentes nos `.properties` do IPED alvo e nunca atualiza chaves do proprio plugin cujos textos foram corrigidos.
  5. Presenca de `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.8.jar` (copia antiga/desatualizada) e `.zip`s de versoes antigas desatualizadas na raiz.
- **Correcao**:
  1. Manter a atualizacao atômica de `lib\json-20250517.jar` (com backup `.bak`) para satisfazer o `Class-Path` dos manifests do IPED base e neutralizar a `CVE-2026-59171`, removendo a instalacao redundante de `lib\json-20260719.jar` e `lib\opencv-4120.jar` em `IPED\lib\` (removendo inclusive copias duplicadas deixadas por instalacoes anteriores apos backup, mantendo `dependencies\json-20260719.jar` e `dependencies\opencv-4120.jar` no instalador apenas para build/testes).
  2. Corrigir selecao de `IPED_ROOT` (`if "!IPED_ROOT!"==""`) e redirecionar falhas das etapas de backup/copia/localizacao para `:ROLLBACK`.
  3. Atualizar `install-localization.ps1` para **inserir chaves ausentes e atualizar valores modificados das chaves que pertencem aos arquivos de localizacao do plugin**, preservando intactas todas as chaves nativas do IPED e gerando backup `.bak`.
  4. Remover `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.8.jar` obsoleto, recompor `iped-viewers-impl-4.3.1.jar` preservando todas as classes originais do pacote `iped.viewers`, recalcular SHA-256 em `.bat`, `CHECKSUMS.md`, `versao_hash.md`, `RELEASE_NOTES.md`, e gerar o `.zip` atualizado validado em teste de instalacao limpa contra uma copia extraida do `iped-4.3.1.zip` oficial.

### A-03 (`H-03` / `CWE-401`) — Vazamento de threads e objetos nativos OpenCV no ciclo de vida do Viewer no IPED
- **Arquivos**: `src/iped/viewers/HtmlLinkViewer.java`, `src/iped/viewers/ChatCaptureLinkedWindow.java`.
- **Problema**: `HtmlLinkViewer` instancia `ExecutorService uiScriptExecutor` (executor em background) e `SensitiveImageMatcher` (descritores `Mat`/`ORB` nativos em C++/JNI), mas nao sobrescreve `dispose()`. Ao fechar abas/janelas ou reciclar visualizadores no IPED (especialmente `ChatCaptureLinkedWindow`, que cria um `new HtmlLinkViewer(attachSearcher)` por janela e chama apenas `JFrame.dispose()`), a thread e recursos nativos nao sao liberados.
- **Correcao**:
  1. Sobrescrever `@Override public void dispose()` em `HtmlLinkViewer`: invocar `closeSensitiveImageMatcher()`, `uiScriptExecutor.shutdownNow()` e `super.dispose()`.
  2. Sobrescrever `@Override public void dispose()` em `ChatCaptureLinkedWindow` garantindo `linkedViewer.dispose()` em bloco `try/finally` antes de `super.dispose()`.

---

## Secao B — Vulnerabilidades e Falhas no Plugin, Verificador de Integridade e Launcher

### B-01 (`P-01` / `CWE-1104` / SCA) — Runtime `.NET 10.0.14` com 5 CVEs HIGH (`CVSS 8.8`) no `integridade-verifier.exe`
- **Arquivos**: `verificador-integridade/launcher/NuGet.Config`, `verificador-integridade/launcher/Launcher.csproj`, `verificador-integridade/build-single-exe.ps1`.
- **Problema**: `launcher/NuGet.Config` declara `<packageSources><clear /></packageSources>` sem adicionar `nuget.org`, bloqueando restauracao online e forcando o SDK a embutir o runtime local em cache `Microsoft.NETCore.App.Runtime.win-x64` **`10.0.14`**, afetado por **5 CVEs HIGH** no OSV (`CVE-2026-114151`, `CVE-2026-114152`, `CVE-2026-114158`, `CVE-2026-114159`, `CVE-2026-118745`). A versao **`10.0.15`** disponivel no NuGet oficial apresenta **0 vulnerabilidades** no OSV.
- **Correcao**:
  1. Configurar `launcher/NuGet.Config` com `<add key="nuget.org" value="https://api.nuget.org/v3/index.json" protocolVersion="3" />` (consistente com `verificador-integridade/nuget.config`).
  2. Fixar explicitamente em `Launcher.csproj` `<RuntimeFrameworkVersion>10.0.15</RuntimeFrameworkVersion>` (ou versao `10.0.x` mais recente livre de CVEs) para `Microsoft.NETCore.App` / `Microsoft.WindowsDesktop.App` `win-x64`.
  3. Recompilar `integridade-verifier.exe` e verificar via inspecao de payload/OSV que o runtime embutido e `10.0.15` (**0 CVEs**), atualizando os hashes oficiais em `VerifierIntegrity.java`, `VerifierIntegrityHashTest.java`, `instalar_captura_chat_zap.bat` e documentacao.

### B-02 (`P-02` / `CWE-59`, `CWE-409`) — Extracao/Verificacao no Launcher `.NET` sem bloqueio de `ReparsePoint` e limites de descompressao
- **Arquivo**: `verificador-integridade/launcher/Program.cs`.
- **Problema**: `ExtractArchiveSafely` e `VerifyExtractedFiles` validam caminhos lexicais contra path traversal, mas nao verificam `FileAttributes.ReparsePoint` em diretorios pai/destino nem no arquivo lido para calculo de SHA-256 (`File.OpenRead`), nem limitam total de entradas / bytes descomprimidos ao reutilizar `ExtractArchiveSafely`.
- **Correcao**:
  1. Adicionar helper `EnsureNoReparsePointHierarchy(string rootDir, string targetPath)` validando ausencia de `FileAttributes.ReparsePoint` de `rootDir` ate `targetPath`.
  2. Adicionar limites defensivos em `ExtractArchiveSafely` (`MaxArchiveEntries = 4096`, `MaxArchiveTotalBytes = 512L * 1024L * 1024L` com leitura limitada no stream de copia de cada entrada).
  3. Chamar `EnsureNoReparsePointHierarchy(root, full)` em `VerifyExtractedFiles` antes de `File.OpenRead(full)`.
  4. Cobrir em `RunLauncherSelfTest()` a rejeicao de archive excedendo o limite / caminho invalido, garantindo `--self-test` passando sem alterar o fluxo normal de execucao.

### B-03 (`F-01`) — Divergencia de contrato HTML/MIME entre `HtmlLinkViewer` e `IntegrityVerifier` (Falso Positivo de Adulteracao)
- **Arquivos**: `src/iped/viewers/HtmlLinkViewer.java` (`ALLOWED_HEADER_HTML_TAGS`, `ALLOWED_SEPARATOR_HTML_TAGS`, `ALLOWED_HEADER_ATTRS`, `ALLOWED_SEPARATOR_ATTRS`, `validateCaptureHeaderHtml`, `sanitizeMessageTagAttributes`), `verificador-integridade/src/IntegrityVerifier.java` (`ALLOWED_HTML_ATTRIBUTES`, `validateHtmlStructure`, `validateEmbeddedImage`).
- **Problema**: O verificador valida `whatsapp.html` inteiro com `ALLOWED_HTML_ATTRIBUTES`, que hoje aceita um subconjunto menor de tags/atributos do que `validateCaptureHeaderHtml` / `validateCaptureSeparatorHtml` gravam a partir do `#topbar` / separadores do relatorio IPED (ex.: `u, hr, h1..h6, table, tbody, tr, td, th, em`, `dir, lang, colspan, rowspan` e `data-[a-z0-9_-]{1,64}`), alem de pequenas divergencias de validacao de MIME inline `data:image/*`. Isso faria capturas autenticas produzidas pelo plugin serem reprovadas pelo verificador.
- **Correcao**:
  1. Alinhar simetricamente as allowlists seguras no plugin e no verificador:
     - Suportar em `IntegrityVerifier` as mesmas tags estruturais seguras de cabecalho/tabela (`u, hr, h1..h6, table, thead, tbody, tr, td, th, em`) com seus atributos passivos (`class, title, role, aria-label, aria-hidden, dir, lang, colspan, rowspan, alt, width, height, id` restrito a valores seguros de layout) e restringir atributos `data-*` nao executaveis por regra uniforme explicita (`isAllowedSafeDataAttribute`: prefixo `data-` em `[a-z0-9_-]{1,64}` proibido apenas de carregar URIs/handlers ou sobrescrever atributos internos de captura, com conjunto identico validado tanto em `HtmlLinkViewer` quanto em `IntegrityVerifier`).
     - Alinhar `decodeHtmlAttachment` / `validateCaptureHeaderHtml` e `IntegrityVerifier.validateEmbeddedImage` no mesmo subconjunto seguro (`image/png`, `image/jpeg`, `image/gif`, `image/webp` com magic bytes validos e teto de tamanho).
  2. Adicionar teste ponta a ponta comprovando que cabecalho com tabela/hierarquia `h1..h6`/`dir`/`data-iped-*` e imagem inline gerado por `HtmlLinkViewer` passa integralmente pelo `IntegrityVerifier` (`aprovado`), enquanto atributo/tag insegura continua bloqueada nos dois lados.

### B-04 (`F-02` / `CWE-79` / Bug de parser HTML) — Busca imprecisa de tag `<a` bloqueia linkagem apos `<audio>`/`<aside>`
- **Arquivo**: `src/iped/viewers/HtmlLinkViewer.java` (`linkInlineCaptureImages` L4262, `replaceInlineCapturePlayers` L4282).
- **Problema**: `before.lastIndexOf("<a") > before.toLowerCase(Locale.ROOT).lastIndexOf("</a>")` casa falsamente com `<audio`, `<aside`, `<abbr`, `<article` ou `<A` maiusculo sem correspondente `</a>`, fazendo anexos posteriores deixarem de receber `<a href="...">`.
- **Correcao**:
  1. Implementar helper estatico `static boolean isInsideOpenAnchorTag(String beforeHtml)` usando limite de tag regex `(?is)<a(?=[\\s>/])` vs `(?is)</a\\s*>` (encontrando a ultima ocorrencia de abertura valida `<a` apos a ultima `</a>`).
  2. Substituir ambas as ocorrencias e adicionar caso de teste unitario em `tests/iped/viewers/`.

### B-05 (`S-01` / `CWE-79`, `CWE-749`) — Endurecimento de `html.disable` no Swing e validacao estrita de esquema HTTPS no link externo
- **Arquivos**: `src/iped/viewers/ChatAnnotationActionDialog.java`, `src/iped/viewers/HtmlLinkViewer.java`, `src/iped/viewers/VerifierIntegrity.java`, `verificador-integridade/src/IntegrityVerifier.java`.
- **Problema**:
  1. `ChatAnnotationActionDialog.showNote` e `showLinkedChats`, dialogos de erro dinamicos em `HtmlLinkViewer.java` (mensagens com `e.getMessage()` ou nomes de pastas/capturas) e `IntegrityVerifier.showTopMessage` passam `String` diretamente ao `JOptionPane`, o que permite ao parser HTML do Swing interpretar texto iniciado por `<html>` se nao houver protecao explicita.
  2. `VerifierIntegrity.java` abre `e.getURL().toURI()` em `Desktop.getDesktop().browse(...)` sem validar explicitamente o esquema `https`.
- **Correcao**:
  1. Criar/reutilizar helper para caixa de texto/label segura com `putClientProperty("html.disable", Boolean.TRUE)` (`ChatCapturePanel.plainLabel(...)` / `JTextArea` nao editavel em `JScrollPane` para textos longos em `showNote` e `showLinkedChats`, e em mensagens dinamicas no `IntegrityVerifier`).
  2. Em `VerifierIntegrity.java`, validar `"https".equalsIgnoreCase(uri.getScheme())` antes de invocar `Desktop.getDesktop().browse(uri)`.

### B-06 (`S-02` / `CWE-400`) — Limites explicitos em `Files.list`, varredura `discover` e carga de referencias no `SensitiveImageMatcher`
- **Arquivos**: `src/iped/viewers/HtmlLinkViewer.java`, `verificador-integridade/src/IntegrityVerifier.java`.
- **Problema**: Streams `Files.list(...)` sem `.limit(...)` em `rebuildCaptureRootIndex`, `cleanupRecaptureAuxiliaryFolders`, `listCaptureEntries`, `loadFrames` e `SensitiveImageMatcher` (sem limite de quantidade de imagens de referencia carregadas em `Mat`), alem de `IntegrityVerifier.discover` iterando subpastas nao-captura sem limite total de entradas escaneadas (`MAX_SCAN_ENTRIES`).
- **Correcao**:
  1. Aplicar `.limit(MAX_SCAN_ENTRIES)` (`100_000L` / limite adequado ao método, ex. `10_000L` em `loadFrames`) nas streams `Files.list`.
  2. Definir limite `MAX_REFERENCE_IMAGES = 512` em `SensitiveImageMatcher` durante a leitura das referencias (ignorando excedentes com log de aviso).
  3. Interromper com erro controlado em `IntegrityVerifier.discover` se `scanned > MAX_SCAN_ENTRIES` (`100_000L`).

### B-07 (`S-03` / `CWE-59`, `CWE-367`) — Eliminacao de `Files.size` isolados sem `NOFOLLOW_LINKS` e leitura acotada de imagens
- **Arquivos**: `src/iped/viewers/CaptureIntegrity.java`, `src/iped/viewers/HtmlLinkViewer.java`, `verificador-integridade/src/IntegrityVerifier.java`.
- **Problema**: Diversos trechos verificam `Files.isRegularFile(..., LinkOption.NOFOLLOW_LINKS)` ou `isSafeRegularFile` e chamam `Files.size(path)` em chamada separada (que segue symlinks por padrao em caso de corrida NTFS), `CaptureIntegrity.listRegularFilesSafe` depende de o chamador checar `root`, e `SensitiveImageMatcher.readBoundedImageFile` le `ImageIO.createImageInputStream(imgIn)` sem contador estrito de bytes durante o consumo do stream.
- **Correcao**:
  1. Usar helper unico `safeRegularFileSize(Path)` baseado em `Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)` checando `isRegularFile() && !isSymbolicLink() && !isOther()` antes de ler/retornar tamanho, eliminando chamadas nuas `Files.size(...)` nos fluxos criticos de `CaptureIntegrity`, `HtmlLinkViewer` e `IntegrityVerifier`.
  2. Garantir `isLinkOrReparse(root)` dentro de `CaptureIntegrity.listRegularFilesSafe` e usar atributos `NOFOLLOW_LINKS` + leitura limitada em `HtmlLinkViewer.readFilePrefix` e `SensitiveImageMatcher.readBoundedImageFile` (lendo em buffer limitado via `readBoundedImageBytes`).

---

## Criterios de Aceite e Plano de Verificacao

1. **Validacao da Especificacao (antes de aplicar em definitivo ou por ciclo de validacao)**: cada regra critica (compatibilidade de API IPED oficial, deteccao `<a\b` apos `<audio>`, contrato HTML/MIME Plugin ↔ Verificador, OSV 0 CVEs para `.NET 10.0.15`, ausencia de `Files.size` desprotegidos e limites de arquivo) deve possuir caso de teste deterministico reprodutivel.
2. **Suite completa apos aplicacao**:
   - Compilacao limpa do plugin contra o IPED `4.3.1` oficial (`iped-4.3.1.zip`) + `opencv-4120.jar`: **0 erros**.
   - Recompilacao do verificador (`integridade-verifier.jar` + `integridade-verifier.exe` self-contained `.NET 10.0.15`), verificacao OSV com **0 vulnerabilidades** em todas as dependencias Java e `.NET`.
   - Execucao sem `-Djava.awt.headless=true` de todos os testes Java do verificador (`IndexHashParityTest`, `SecurityRegressionTest`), todos os testes Java do plugin (`teste_antes_de_aplicar\run_all_tests.ps1`, testes JDK 11 completos, `MigrationIntegrityHistoryHarness`, 3 scripts `.ps1`), e `--self-test` do `.jar` e `.exe`.
   - Teste de instalacao em copia limpa do IPED `4.3.1` oficial confirmando ausencia de duplicidade `json-*.jar`/`opencv-*.jar` em `lib\` e validacao SHA-256 de todos os binarios.
   - Loop final de reauditoria com `C:\Users\Tom\.agents\skills\encontrar-vulnerabilidade` resultando em **0 vulnerabilidades e 0 falhas**.
