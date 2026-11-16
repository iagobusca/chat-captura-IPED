# Changelog — Chat Captura IPED

Mudanças relevantes do plugin.

> **Nota de publicação:** As releases públicas oficiais no GitHub iniciam na série estável **`v2.6.8`** (`v2.6.8`, `v2.6.9`, `v2.7.0`). As entradas `<= 2.6.8-beta` preservam o histórico completo de desenvolvimento e endurecimento de segurança publicado originalmente no repositório Gitea local.

## [Não Lançado] - Em desenvolvimento

## [2.7.0] - 2026-11-16

### Build Reprodutível Auditável & Integração GiteaRunner
- **Compilador Determinístico (`build-reproducible.ps1`)**: adicionado script multiplataforma (`Windows`, `Linux` e `macOS`) com `ReproducibleZipPacker` (timestamps UTC `1980-02-01T00:00:00Z`, ordenação lexicográfica e manifesto fixo), suporte a `--verify-reproducibility` (compilação dupla com prova bit a bit) e toolchain pinável (`Temurin JDK 21.0.13+9` + `OpenJFX 21.0.5` + `IPED 4.3.1`).
- **Workflow Gitea (`GiteaRunner`)**: `.gitea/workflows/release.yml` compila de forma reprodutível no runner local, valida igualdade bit a bit de todos os SHA-256 contra `VerifierIntegrity.java`, `instalar_captura_chat_zap.bat` e `CHECKSUMS.md`, executa todas as suítes Java/PowerShell/Python e publica a release `v2.7.0` com os 4 artefatos verificados.
- **Verificador `1.4.5` (`IntegrityVerifier` e launcher `.exe`)**: recompilado deterministicamente (`159.164` bytes JAR `E013F31E...`; `77.616.218` bytes EXE `AF1C41C0...` com runtime `.NET 10.0.15` intacto).
- **Plugin `2.7.0` & Instalador**: `CHAT_CAPTURE_PLUGIN_VERSION = "2.7.0"`, `Implementation-Version: 2.7.0` (`1.510.768` bytes, SHA-256 `BAFEB635...`), allowlist exclusiva em `VerifierIntegrity` e `instalar_captura_chat_zap.bat` sincronizada com os binários do Verificador `1.4.5` (rejeitando `<= 1.4.4`), e instalador determinístico `instalador-chat-captura-IPED-2.7.0-verificador-1.4.5.zip` (`94.409.121` bytes, SHA-256 `C9F70B88...`).

## [2.6.9] - 2026-11-16

### Segurança e Corrigido

- **Verificador `1.4.4` (`IntegrityVerifier` e launcher `.exe`)**:
  - Consolidação do scanner HTML léxico determinístico por máquina de estados (`scanHtmlElements` / `parseHtmlTagStrict`), bloqueando bypasses com apóstrofo/aspa isolada em atributos sem aspas em `presentationVersion` `0` e `2`.
  - Endurecimento de `openFile` (`LinkOption.NOFOLLOW_LINKS`, verificação de arquivo regular e limite `maxBytes`), remoção de método morto vulnerável (`isAllowedPath`), `safeRegularFileSize` sem corrida de symlink, `html.disable` em componentes Swing, `MAX_SCAN_ENTRIES` e executável x64 autocontido com `.NET 10.0.15` (0 CVEs).
- **Plugin `2.6.9` (`HtmlLinkViewer` / `ChatCapturePanel` / `SavedChatPickerDialog` / `ChatCaptureLinkedWindow` / `VerifierIntegrity` / instalador)**:
  - Defesa em profundidade Swing com `putClientProperty("html.disable", Boolean.TRUE)` antes do cálculo de texto em `SearchHitRenderer` (`ChatCapturePanel`) e nos renderizadores/diálogos locais (`SavedChatPickerDialog`), prevenindo renderização HTML em rótulos derivados de conteúdo indexado ou nomes de pastas.
  - Fechamento e limpeza determinística em `ChatCaptureLinkedWindow` (`DISPOSE_ON_CLOSE` e checagem `closed.get()`), `HtmlLinkViewer.dispose()` e guarda `if (disposed)` em `loadFile`, além do helper reflexivo `AttachmentSearcher.openItem` para compatibilidade com o IPED `4.3.1` oficial e correções no instalador `.bat` (caminhos com `!`, rollback em falhas e não duplicação de `json-*.jar`/`opencv-*.jar`).
  - Incremento de versão para `2.6.9` (`CHAT_CAPTURE_PLUGIN_VERSION`, `Implementation-Version: 2.6.9`, SHA-256 `725CA7E0...`) e allowlist exclusiva de `VerifierIntegrity` sincronizada com os binários oficiais do Verificador `1.4.4` (`C37D52F2...` / `A57CB7E8...`), rejeitando `<= 1.4.3`.

## [2.6.8] - 2026-11-14

### Estável (`main`)

- Promoção da release `2.6.8-beta` validada para a branch `main` como versão estável **`2.6.8`** (sem sufixo `-beta`), mantendo o **Verificador de Integridade `1.4.3`** (`8CB04812...` / `723DD750...`) e todas as correções de captura, segurança (`F-01..F-10`) e relatórios sem limite de tamanho.
- Plugin recompilado (`iped-viewers-impl-4.3.1-2.6.8.jar` / `Implementation-Version: 2.6.8`, SHA-256 `AB6B8A5F...`).

---

## [2.6.8-beta] - 2026-11-14

### Corrigido e Melhorado

- **Verificador `1.4.3` (`IntegrityVerifier` e launcher `.exe`)**:
  - Remoção do limite artificial de tamanho estimado de relatório (`enforceReportBudget` / `4.194.304` caracteres), do limite de `16 MiB` na gravação de relatórios (`atomic` / `atomicBatch`) e dos tetos cumulativos entre capturas em `verifyAllBounded` / `--verify-only`, permitindo verificar pastas com grandes conjuntos de capturas ou chats completos sem falha de orçamento.
- **Plugin `2.6.8-beta` (`HtmlLinkViewer` / `VerifierIntegrity` / instalador)**:
  - Correção em `runHtmlMessageCapture` e `sanitizeCaptureMessageFragment` para não abortar a captura inteira (nem remover a pasta temporária `.__html_capture_<uuid>` sem criar a pasta final) quando uma conversa contém anexos relacionados acima de `8 MiB`, ausentes no caso ou que excedam o orçamento acumulado, contabilizando tamanho único por SHA-256 e ignorando de forma segura apenas o anexo excedente.
  - Incremento de versão para `2.6.8-beta` e allowlist exclusiva de `VerifierIntegrity` atualizada para os binários oficiais do Verificador `1.4.3` (`8CB04812...` / `723DD750...`), rejeitando `<= 1.4.2`.

---

## [2.6.7-beta] - 2026-11-14

### Segurança e Corrigido

- **Verificador `1.4.2` (`IntegrityVerifier` e launcher `.exe`)**:
  - `F-01`: rejeição de `artifacts` vazio ou duplicado (case-insensitive), amarração de `contentRootHash` do último evento e verificação do inventário completo de `integridade.json` em `html-message-v1`.
  - `F-02`: reprovação fail-closed em `verifyMessageCapture` para `captureRootHash` divergente e validação estrita de `historyStatus` no `--verify-only`.
  - `F-03`: classificação correta de estados saudáveis de `html-message-v1` e reconciliação em `writeReports` (`isHealthyVerifierItemState`).
  - `F-04`/`F-05`: `html.disable` em todos os `JLabel`/`JCheckBox` do verificador e bloqueio de comentários HTML, CDATA e instruções `<?` em `validateRenderedCaptureHtml`.
  - `F-06`/`F-10`: launcher C# com exclusão segura sem seguir junctions/reparse points, janela de tolerância de 2 min na limpeza de diretórios sem lock e autoteste de entrada ZIP de 0 bytes no `--self-test`.
  - `F-07`/`F-08`/`F-09`: comparação pública com normalização de chaves e vínculo `(domId, key, messageHash)`, rejeição de `integridade.json` com `schemaVersion < 2` em `html-message-v1` e carregamento exclusivo de ícone pelo classpath.
- **Plugin `2.6.7-beta` (`HtmlLinkViewer` / `VerifierIntegrity` / Swing / instalador)**:
  - `F-04`: `html.disable` nos diálogos e visualizadores (`HtmlLinkViewer`, `ChatNoteDialog`, `ChatPreflightDialog`, `ChatCaptureLinkedWindow`).
  - `F-08`: comparação de `migration.json` via `CaptureIntegrity.canonicalJson()` (independente da ordem de chaves JSON).
  - `F-09`: escape HTML em `VerifierIntegrity.showAlertIfNeeded` para nomes de arquivos e hashes exibidos no `JTextPane`.
  - Incremento de versão para `2.6.7-beta` e allowlist exclusiva de `VerifierIntegrity` atualizada para os binários oficiais do Verificador `1.4.2` (`08877EFF...` / `4859980D...`), rejeitando `<= 1.4.1`.

---

## [2.6.6-beta] - 2026-11-13

### Segurança e Corrigido

- **Verificador `1.4.1` (`IntegrityVerifier` e launcher `.exe`)**:
  - `V-01`: detecção e reprovação fail-closed de arquivos extras em capturas legadas (não `html-message-v1`) na verificação e no modo `--verify-only`.
  - `V-02`: bloqueio de diretório raiz quando for NTFS junction ou link simbólico em CLI, GUI e `discover()` (`LinkOption.NOFOLLOW_LINKS` / `isLinkOrReparse`).
  - `V-03`: cancelamento da busca inicial (`CancellationException`) passa a encerrar silenciosamente com código `0`.
  - `V-04`: relatório `integridade.html` passa a emitir `<meta http-equiv="Content-Security-Policy">` com hash SHA-256 (`sha256Base64`), listeners DOM (`addEventListener`) sem `onclick=` inline, escape de apóstrofo (`&#39;`) e escape JSON seguro.
- **Plugin `2.6.6-beta` (`HtmlLinkViewer` / `VerifierIntegrity` / instalador)**:
  - `P-01`: validação contra `null`/vazio em `AttachmentHandler.open` e escape seguro em `showAttachNotFound`.
  - `P-02`/`P-03`: validação de caminhos e payload em `isSafeCoordinatesRelativePath` e `validateWhatsAppCoordinatesData` antes da gravação em `regenerateWhatsAppHtmlFromSavedCoordinates` e `writeWhatsAppHtml`; endurecimento JS (`safeRelPath`, `safeLinkedUrl`, `rel="noopener noreferrer"`), remoção de double-escaping em `excerptHtml`, substituição de `onclick=` inline (uso de `data-avatar` e delegação em `#menu a[data-section]`) e política `Content-Security-Policy` por hash SHA-256 (`sha256Base64`) em `whatsapp.html`, `index.html`, `relatorio.htm` raiz e `relatorio.htm` das capturas.
  - `P-04`: guarda contra `null` e serialização via `js(hash)` em `setSelected`.
  - Incremento de versão para `2.6.6-beta` e atualização da allowlist exclusiva de `VerifierIntegrity` para aceitar somente os binários oficiais do Verificador `1.4.1` (`CC295881...` / `A7283005...`), rejeitando `1.4.0` e `<= 1.3.9`.

---

## [2.6.5-beta] - 2026-11-13

### Melhorado e Corrigido

- **Verificador `1.4.0` (`IntegrityVerifier` e launcher `.exe`)**:
  - Escolha de modo (`Escolha a referência`) movida para o início do fluxo GUI (`Opção A`): no modo **Integridade Local**, passa a exibir janela de carregamento com barra de progresso ao clicar em **Confirmar** em **Selecione as capturas**; no modo **Integridade Público**, remove **Selecione as capturas** e executa janela única de carregamento selecionando e verificando todas as capturas antes de abrir direto **Integridade Público**.
  - Na janela **Resultado**, removidos **Hash geral do conjunto**, **SHA-256 do histórico completo** e botão **Copiar lista de hashes**, preservando **SHA-256 do histórico completo público**, cópias/aberturas de histórico, relatório HTML e Fechar (e gravações auxiliares em `integridade/` + `--export-history` inalterados).
  - Relatório `integridade.html` atualizado para exibir **Hash geral Público** (campo superior aceitando SHA-256 do histórico público anonimizado e do histórico completo), remover **Comparar hash desta captura** e validar as capturas diretamente contra o `historico-capturas.txt` colado (normal ou anonimizado `CAPTURA|*-YYYYMMDD-HHMMSS`) via SHA-256 offline em JS puro, exibindo **OK** (verde) ou **FALHA** (vermelho).
  - Correção global de `alwaysOnTop`, foco e execução na Swing EDT para **Selecione as capturas**, diálogos de carregamento, alertas e erros de verificação/launcher.
- **Plugin `2.6.5-beta` (`HtmlLinkViewer` / `VerifierIntegrity` / instalador)**: incremento de versão para `2.6.5-beta` e atualização da allowlist exclusiva para aceitar somente os binários oficiais do Verificador `1.4.0` (`9CA17D7B...` / `11094FA3...`), rejeitando versões `<= 1.3.9`.

---

## [2.6.4-beta] - 2026-11-13

### Corrigido

- **Verificador `1.3.9` (`IntegrityVerifier`)**: adicionada decodificação de entidades HTML em passagem única em `parseHtmlAttributes` (`&#39;`, `&#x27;`, `&apos;`, `&quot;`, `&lt;`, `&gt;`, `&amp;` e numéricas), corrigindo o falso erro `Política CSP ausente ou não canônica.` nas capturas migradas/geradas pela `2.6.3-beta` e bloqueando atributos com esquemas perigosos ofuscados por entidades HTML.
- **Plugin `2.6.4-beta` (`HtmlLinkViewer` / `VerifierIntegrity` / instalador)**: emissão direta de `CAPTURE_CSP` canônico no `<meta http-equiv="Content-Security-Policy">` (sem escapar `'` para `&#39;`) e atualização da allowlist exclusiva para aceitar somente os binários oficiais do Verificador `1.3.9` (`043AFEC2...` / `FF065250...`), rejeitando versões `<= 1.3.8`.

---

## [2.6.3-beta] - 2026-11-13

### Segurança e Correções

- **Política estrita de versão do Verificador (`VerifierIntegrity` / instalador)**: removidos todos os hashes de versões anteriores (`<= 1.3.7`); o plugin `2.6.3-beta` aceita exclusivamente o Verificador `1.3.8` (`556B1A65...` / `B9D77A7F...`) e substitui automaticamente binários obsoletos durante a instalação/preparo da captura.
- **Sanitização HTML/CSS/SVG no plugin (`HtmlLinkViewer`)**: parser estrutural em Java para fragmentos `row.html` com allowlist de tags/atributos (preservando textos literais com marcadores de token), remoção de `<template>`, handlers e atributos `src` externos não permitidos no DOM, paridade com o Verificador na normalização de comentários/escapes CSS e bloqueio de `<style>`, `<animate*>`, `<set>`, `<mpath>` e `url(...)` com aspas em SVGs; escaping estrito em `json()`, `js()`, `html()` e `writeWhatsAppHtml`.
- **Verificador `1.3.8` (`IntegrityVerifier` e launcher C#)**: classificação `"registro inválido"` para `integridade.json` corrompido sem mascarar rejeição de junções NTFS, endurecimento da allowlist de atributos em HTML legado e normalização CSS/SVG, verificação canônica `recordHash` (`"canonical-v1"`) com retrocompatibilidade somente-leitura para eventos antigos, correção de limpeza de lock/temporários e extração ZIP de arquivos vazios (0 bytes) no launcher `.exe`, além de auditoria NuGet ativa no build padrão.
- **Confiabilidade e UI (`HtmlLinkViewer`, `ChatAnnotationStore`, `CaptureIntegrity`, `instalar_captura_chat_zap`)**: preservação de `whatsapp.html` se a migração falhar antes do backup (`C1`), `absolutePath` final em `hashes.txt`/`Lista de Arquivos.csv` (`C2`), preflight respeitando `dialog.isConfirmed()` (`C3`), fechamento CSS da barra de busca no `whatsapp.html` com `HTML_GENERATOR_VERSION = 4` (`C4`), preparo do `Re-Extrair` fora da Swing EDT (`C5`), propagação real de falhas em `persist()`/`persistOrThrow()` e rejeição de `\u+041` (`C6`), decodificação limitada de imagens antes de alocar `BufferedImage` em `createReportThumbnails` (`D4`), remoção de MD5 decisório no `SensitiveImageMatcher` (`D5`) e restauração de `.properties` no `:ROLLBACK` do instalador (`E1`).

---

## [2.6.2-beta] - 2026-11-13

### Segurança

- Importador legado (`ChatAnnotationStore`): remoção de fallback genérico por reflexão/SerialKiller, restringindo desserialização à allowlist estrita de tipos esperados e protegendo contra anotações/links inválidos.
- Validação de caminho relativo em capturas e anotações (`CaptureIntegrity` / `HtmlLinkViewer`): contenção estrita e rejeição de `..`, prefixos de drive e caracteres de controle.
- `ChatAnnotationActionDialog`, `ChatCapturePanel` e demais painéis Swing: desabilitação explícita de renderização HTML (`html.disable=true`) e neutralização contra injeção HTML/CSV em notas, pré-checagens e exportações.
- Parsing estrutural (`org.json`) e scanner JSON ciente de profundidade (`ChatAnnotationStore` / `HtmlLinkViewer`): substituição de extrações por regex em anotações, manifestos, coordenadas e jobs de recaptura.
- Decodificação segura de imagens sensíveis (`SensitiveImageMatcher`): pré-validação em Java puro (assinatura mágica PNG/JPEG/BMP/GIF/WebP, dimensões <= 8192 px, <= 33,5 MP e <= 32 MiB) e conversão via Java `ImageIO` (`TYPE_BYTE_GRAY` -> `CV_8UC1`), removendo `Imgcodecs.imread` e restringindo `Imgcodecs.imdecode` a WebP pré-validado.
- UI e pastas auxiliares: execução assíncrona fora da Swing EDT em captura, notas e busca, bloqueio de `outputFolder` vazio, sincronização das pastas auxiliares ignoradas e redução dos módulos no runtime do Verificador 1.3.7.
- `VerifierIntegrity`: allowlist atualizada exclusivamente para o Verificador 1.3.7 (`7A137AE3...` / `D102E17D...`).

---

## [2.6.1-beta] - 2026-11-13

### Segurança

- Anotações (`ChatAnnotationStore`): corrigida dupla decodificação que alterava o texto das notas ao recarregar.
- Verificador 1.3.6: detecta arquivos extras em capturas `html-message-v1`; rejeita links/junções NTFS; valida HTML por allowlist de tags/atributos (regex ciente de aspas); valida e escapa campos do histórico (`historico-capturas.txt`).
- OpenCV só é carregado de caminhos do IPED com SHA-256 conferido (`HtmlLinkViewer`).
- Nomes exportados passam por `sanitizeFileName` único (reservados do Windows, controles, `.`/`..`) e anexos são gravados com `CREATE_NEW`.
- Instalador: `IPED_ROOT` resolvido antes do uso, abort/rollback em falhas, caminhos passados por variável de ambiente ao PowerShell, SHA-256 esperado do plugin e do verificador, e cópia do verificador para `<IPED>\verificador-integridade\`.
- `VerifierIntegrity`: removidos caminhos absolutos de desenvolvimento; allowlist aceita somente o Verificador 1.3.6.

---

## [2.6.0-beta] - 2026-11-12

### Adicionado

- Verificador de Integridade **1.3.5**: novo botão **"Copiar histórico completo público"** e exibição do **SHA-256 do histórico completo público** no resumo, exportando linhas anonimizadas no formato `CAPTURA|*-YYYYMMDD-HHMMSS ...` para publicação externa com maior privacidade.
- Verificação dual no modo **Integridade Público** do Verificador 1.3.5: aceita e compara tanto o histórico completo original (`CAPTURA|<pasta>-YYYYMMDD-HHMMSS ...`) quanto o histórico completo público anonimizado (`CAPTURA|*-YYYYMMDD-HHMMSS ...`), bem como o hash SHA-256 de qualquer um dos dois formatos.
- Opção `--export-history` do Verificador 1.3.5 também emite `SHA-256_HISTORICO_PUBLICO=...`.
- Prevenção de travamento no `verificador-integridade/build-single-exe.ps1` com `MSBUILDDISABLENODEREUSE=1`, `DOTNET_CLI_DO_NOT_USE_MSBUILD_SERVER=1`, `-m:1 -nodeReuse:false` e `dotnet build-server shutdown`.

### Alterado

- `VerifierIntegrity.java` e `VerifierIntegrityHashTest.java` sincronizados com os binários oficiais do Verificador **1.3.5** (`31B0CE72...` e `75FB542B...`).
- Versão do plugin elevada para `2.6.0-beta` em `HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION`, `META-INF/MANIFEST.MF` (`Implementation-Version: 2.6.0-beta`) e `instalar_captura_chat_zap.bat`.

---

## [2.5.9-beta] - 2026-11-12

### Adicionado

- `CaptureBoundaryResolver`, `HtmlLinkViewer` e Verificador 1.3.4 agora aceitam qualquer ponto inicial/final válido no WhatsApp: mensagens numéricas (`^\d+$`), registros de chamada (`^\d+_call:[0-9a-fA-F]+$`), mensagens removidas/carved (`^-?\d+_\d+$`) e timestamps internos (`iped-capture-date-...`), normalizando automaticamente `data-iped-capture-id` e `domId`.
- Fat-JAR autocontido com classes `org/opencv/**` embutidas em `iped-viewers-impl-4.3.1.jar`, busca da DLL nativa `opencv_java4120.dll` na raiz do IPED / `plugins/` / `lib/` e `Implementation-Version: 2.5.9-beta` no `META-INF/MANIFEST.MF`.
- Tratamento tolerante de anexos em `html-message-v1` (pula anexos não suportados sem abortar a captura).
- Script `verificador-integridade/build-single-exe.ps1` atualizado com `--disable-build-servers -p:UseSharedCompilation=false` para evitar travamento de servidores MSBuild/Roslyn.

### Corrigido

- Falha ao re-extrair ou migrar capturas legadas no Re-Extrair quando o ponto inicial/final aponta para registro de chamada (`_call:<hex>`), timestamp interno da linha ou ID negativo/removido (`-?<id>_<id>`).
- `VerifierIntegrity.java` e `VerifierIntegrityHashTest.java` sincronizados com os binários do Verificador 1.3.4 recompilados (`B456074C...` e `20FAF8A5...`).
- Instalador atualizado para exibir `Versao do plugin: 2.5.9-beta`.

---

## [2.5.8-beta] - 2026-11-09

### Adicionado

- Censura visual por imagens de referência (blur) em capturas `html-message-v1` (captura nova, Re-Extrair e migração de legado), implementada em `CaptureCensorship` com CSS puro e atributos HTML (`class="capture-media-link capture-censored"` e `data-capture-censored="1"`), sem JavaScript adicional e mantendo total compatibilidade com o Verificador de Integridade 1.3.4.
- Sobrecarga em `SensitiveImageMatcher` para casamento direto sobre bytes em memória (`MatOfByte` / `Imgcodecs.imdecode` e hash exato MD5/SHA-256), com cache por SHA-256 em `runHtmlMessageCapture` cobrindo anexos do caso (`resources`) e miniaturas inline (`media`).
- Registro auditável `censorship` em `manifest.json` (com `presentationDigest`, `images`, `cssPixels`, `levelPercent`, `similarityPercent` e aviso explícito de que o blur CSS é supressão visual de apresentação, não tarja irreversível), mantendo `messageHash` e a fórmula de `captureRootHash` inalterados.

### Modificado

- A migração de capturas legadas para `html-message-v1` no Re-Extrair aceita pasta de imagens de referência e exibe aviso de confirmação quando executada sem pasta de referência.
- O Re-Extrair compara `presentationDigest` além de `isHtmlReconciliationUnchanged`, substituindo a captura quando a censura é ligada, alterada ou removida (via modo de cadeia limpa) e registrando `recapture_censorship_presentation_changed`.
- A janela **Integridade Público** do Verificador de Integridade 1.3.4 foi reorganizada para exibir apenas o campo de hash SHA-256 público e o campo único de `historico-capturas.txt` (removendo o campo duplicado de manifesto `html-message-v1` e o botão de cópia de hash calculado).
- No Re-Extrair, a opção "Limpar registros e criar como nova captura" agora apaga diretamente a pasta `integridade/` e o arquivo `integridade.html` da raiz quando capturas são substituídas, sem criar pastas `_backup_integridade_*`, deixando a raiz limpa como uma captura nova.

### Segurança

- Comportamento *fail-closed* para censura em `html-message-v1`: pasta de referência configurada porém sem imagens válidas ou sem OpenCV disponível (`CensorshipReferenceUnavailable`), erro no matcher, imagem casada sem elemento HTML correspondente ou tentativa de Re-Extrair sem pasta sobre captura previamente censurada sem o modo de cadeia limpa (`CensorshipWouldBeLost`) interrompem a operação sem promover captura sem censura.

## [2.5.7-beta] - 2026-11-09

### Corrigido

- O Re-Extrair arquiva a pasta `integridade/` da raiz quando alguma captura é realmente substituída. Os relatórios derivados de uma execução anterior deixavam de representar o conjunto atual e sugeriam uma cadeia que não existe mais; a pasta é renomeada para `_backup_integridade_<data>`, sem apagar evidência, e o próximo Verificador gera um retrato novo.
- O índice geral não marca mais `annotations.legacy` em capturas `html-message-v1`; o campo `version` do manifesto só identifica anotações legadas nos formatos antigos. Isso mantém o resultado de um Re-Extrair indistinguível de uma primeira captura.
- Somente capturas efetivamente promovidas contam para o arquivamento dos relatórios da raiz; intervalos ignorados por conteúdo inalterado não alteram a pasta `integridade/`.

## [2.5.6-beta] - 2026-11-09

### Corrigido

- A janela **Re-Extrair capturas** agora calcula largura e altura pela área útil do monitor onde o IPED está aberto, descontando a barra de tarefas. Em 1366×768, o diálogo fica com cerca de 879×638 px e cresce proporcionalmente em Full HD, 2K e 4K.
- O toggle **Limpar registros e criar como nova captura** agora fica disponível também para capturas novas em `html-message-v1`, sem depender do toggle de migração. Ao limpar uma captura nova, o Re-Extrair ignora o atalho de conteúdo inalterado e recaptura o intervalo.
- A validação da candidata limpa rejeita `migration.json`, `legacy-annotations.json`, `message-reconciliation.json` e `reextracao-auditoria.jsonl` em qualquer reset, seja de captura nova ou legada migrada.
- Capturas `html-message-v1` não são mais classificadas internamente como legadas pela ausência do campo `version` no manifesto; isso reabilita **Incluir Notas e Links** em capturas novas.

### Modificado

- O reset de capturas legadas continua exigindo a migração para HTML por mensagem; o reset de capturas novas não exige migração.
- A especificação e os testes de contrato foram atualizados para a nova matriz de decisão (`HTML_RESET`).

## [2.5.5-beta] - 2026-11-08

### Adicionado

- Re-Extrair pode migrar capturas legadas em frames para `html-message-v1` por meio do toggle opt-in **Migrar capturas legadas para HTML por mensagem**, disponível em todas as páginas da seleção.
- Toggle dependente **Limpar registros e criar como nova captura**, que inicia a nova captura em `capture_created` e descarta a cadeia, os registros de reconciliação, a auditoria e as anotações da captura anterior.
- Preservação das anotações legadas em `legacy-annotations.json` quando o histórico é mantido, com registro de `migration.legacyAnnotationsPreserved` no manifesto; as anotações não são vinculadas automaticamente às mensagens novas.
- Reuso de anexos legados no modo limpo somente após conferir SHA-256 e tamanho; MIME, nome, tipo e caminho são recalculados pela origem atual.
- Verificador de Integridade 1.3.4, que valida a cadeia local também em capturas `html-message-v1` e exige o vínculo de `migration.json` e `legacy-annotations.json` ao inventário.

### Modificado

- A primeira migração registra a proveniência da captura legada e não afirma que as mensagens dos frames eram iguais às da fonte atual; depois da migração, a comparação por hash de mensagens permite ignorar intervalos sem alteração.
- Capturas já em `html-message-v1` com `changed`, `missingInCurrentSource` e `newInInterval` iguais a zero não são substituídas; a candidata é descartada e a auditoria registra `recapture_skipped_content_unchanged`.
- A promoção usa pasta temporária irmã, renomeia a pasta antiga para uma pasta transacional e restaura o nome original se a promoção falhar.

### Segurança

- A migração é bloqueada antes de processar o primeiro item quando há pasta de imagens de referência para censura, pois o formato HTML ainda não aplica blur por similaridade.
- `migration.json` e `legacy-annotations.json` entram no inventário de `integridade.json`; o Verificador detecta alteração no registro de migração mesmo que o manifesto seja atualizado junto.
- Nenhum arquivo da pasta legada é alterado antes de a candidata passar na validação de manifesto, HTML, mensagens, anexos e inventário.

## [2.5.4-beta] - 2026-11-07

### Corrigido

- Fechar o preview de imagem, áudio, vídeo, documento ou arquivo restaura a posição anterior do chat; os controles “Fechar” e “×” usam a navegação nativa do fragmento para fechar o modal no WebView do IPED.
- O script fixo do HTML captura a rolagem ao abrir o preview e a restaura depois do evento `hashchange`, preservando a pausa de áudio e vídeo ao fechar.

## [2.5.3-beta] - 2026-11-07

### Corrigido

- O cabeçalho preservado do IPED agora normaliza o MIME de cada imagem embutida pela assinatura dos bytes. Isso corrige avatares PNG fornecidos como `data:image/jpg`, que eram rejeitados pelo Verificador como imagem embutida HTML inválida.
- Adicionado teste de regressão que confirma a rejeição do MIME divergente e a aceitação da imagem normalizada pelo Verificador 1.3.2.

## [2.5.2-beta] - 2026-11-07

### Corrigido

- O menu não permite marcar um separador de data como mensagem final; datas continuam válidas como início e são resolvidas para a primeira mensagem seguinte.
- Start valida IDs numéricos únicos e a ordem do intervalo antes de instalar verificadores ou iniciar o worker; o worker repete a validação para detectar mudanças no DOM.
- Falhas de validação não atualizam os índices da pasta de capturas. Capturas HTML e PNG são preparadas em staging e só promovidas quando o inventário está completo.
- Mensagens localizadas distinguem marcador ausente, ambíguo, fora de ordem e data sem mensagem seguinte.
- O instalador confere os bundles já compilados e atualiza os idiomas externos sem reescrever o JAR.

## [2.5.1-beta] - 2026-11-07

### Corrigido

- Captura HTML não falha ao validar o SVG do tema WhatsApp com o Xerces 2.12.2 do IPED 4.3.1; o parser rejeita DTD e referências externas usando recursos compatíveis com essa implementação.
- Pacote mantém `org.json` 20260719 também com o nome legado `json-20250517.jar`, evitando `NoClassDefFoundError` em classpaths fixos do IPED.
- Testes de contrato acompanham a assinatura atual do sanitizador SVG.

## [2.5.0-beta] - 2026-11-07

### Adicionado

- Apresentação autônoma de `html-message-v1` com tema WhatsApp preservado do IPED 4.3.1, limitada às mensagens selecionadas.
- Modal local para imagem, PDF, documentos e arquivos, com ações de baixar/abrir; áudio e vídeo usam controles sem reprodução automática.

### Corrigido

- Ao fechar o modal ou navegar para fora dele, a mídia é pausada e reiniciada.
- CSP, CSS, anexos, SVGs e preview PDF são validados pelo Verificador 1.3.1; somente o script fixo com hash autorizado é aceito.

## [2.4.1-beta] - 2026-11-07

### Corrigido

- Marcador de início colocado sobre uma data agora resolve para a primeira mensagem seguinte, registra a ID numérica no manifesto para o Re-Extrair e evita `start_anchor_missing_or_ambiguous` e capturas vazias.
- Seleção da origem do Verificador agora exige SHA-256 autorizado; cópias antigas em `iped/lib` não são propagadas para a pasta de captura.
- Substituição dos verificadores inválidos usa arquivo temporário, confere o hash e promove o arquivo somente após a validação.

## [2.4.0-beta] - 2026-11-07

### Adicionado

- Formato `html-message-v1` para novas capturas: HTML offline do intervalo, `messages.json`, chaves e hashes canônicos por mensagem e anexos locais identificados por SHA-256.
- Verificação específica do formato HTML por mensagem no Verificador de Integridade 1.3.0, incluindo ordem, IDs, caminhos confinados, hashes de anexos e hash-raiz.
- Extração de anexos que o HTML do caso referencia por `app.open('sha-256:...')`, incluindo imagem, áudio, vídeo, PDF, Office, arquivos compactados e itens genéricos `.bin` suportados.
- Reconciliação do Re-Extrair com registro por mensagem: inalterada, alterada, nova, ausente na fonte atual ou ambígua.

### Modificado

- Re-Extrair reconhece capturas HTML e localiza as âncoras pelos IDs DOM registrados antes de promover uma captura temporária.
- A impressão digital da origem usa os bytes do HTML fonte; anexos são resolvidos pelo hash SHA-256 do item do caso.
- A propriedade `-Diped.chat.capture.htmlMessageV1=false` desativa novas capturas no formato novo durante a beta. Capturas já em `html-message-v1` continuam usando Re-Extrair nesse formato.
- A censura por similaridade ainda não foi portada para HTML; a captura HTML é interrompida com aviso enquanto uma pasta de blur estiver ativa, evitando salvar mídia sem a censura solicitada.

### Segurança

- A sanitização remove conteúdo ativo e URLs externas; anexos são limitados, conferidos contra o hash do item IPED, verificados por assinatura/MIME e gravados dentro de `attachments/`.

## [2.3.6-beta] - 2026-11-06

### Otimizado

- Lista do Re-Extrair organizada verticalmente, com espaçamento compacto e apenas o nome da pasta.

### Corrigido

- Corrigida a falha que impedia a janela de seleção do Re-Extrair de abrir quando os descritores descartavam as âncoras.
- Atualizados os hashes aceitos para o Verificador 1.2.8; binários antigos 1.2.7 deixam de ser aceitos.

### Adicionado

- Plugin Chat Captura IPED 2.3.6-beta e pacote do instalador atualizado.
- Verificador de Integridade 1.2.8 em JAR e EXE; hashes publicados em `versao_hash.md`.

## [2.3.5-beta] - 2026-11-06

### Segurança

- Confinamento dos caminhos do `capturas-index.json` à raiz de saída; rejeita caminhos absolutos, segmentos ambíguos, links e entradas duplicadas sem comparação quadrática.
- O Re-Extrair carrega metadados no diálogo em segundo plano, limita cada página a 16 MiB e mantém apenas IDs selecionados; jobs são carregados individualmente durante o processamento.
- Inventário, histórico e metadados cumulativos passam a respeitar limites de tamanho antes de substituir os registros anteriores.
- Hashes permitidos sincronizados com o Verificador 1.2.8.

### Corrigido

- Verificador troca a extração de campos por regex pelo parser JSON com limite de profundidade, evitando `StackOverflowError` em caminhos longos.
- Limites globais para candidatos, itens e metadados selecionados; descoberta do verificador ocorre em segundo plano e pode ser cancelada.
- Relatórios acima do orçamento são recusados antes da montagem; os cinco relatórios são preparados e promovidos com rollback se alguma promoção falhar.
- HTML, JSON e texto usam escaping adequado; falhas de limite preservam os relatórios anteriores.

### Modificado

- Plugin continua gerando bytecode Java 11. O Verificador 1.2.8 mantém o JAR Java 8 ou superior e o EXE x64 autocontido.

## [2.3.4-beta] - 2026-11-06

### Modificado

- Atualiza os hashes aceitos para o Verificador 1.2.7, cujo JAR agora funciona com Java 8 (1.8) ou superior.
- O plugin permanece compilado para Java 11, conforme o runtime do IPED 4.3.1.

## [2.3.3-beta] - 2026-11-06

### Modificado

- Seletor do Verificador de Integridade e janela Re-Extrair agora apresentam até 100 capturas por página, com navegação anterior/próxima e contagem.
- A janela Re-Extrair valida detalhes de metadados sob demanda por página e em lotes de até 25 antes de executar os jobs selecionados.
- `Marcar todos` usa seleção global compacta; ao editar uma caixa, converte a seleção atual em IDs explícitos e novas capturas ficam desmarcadas.
- Verificador atualizado para 1.2.6; hashes aceitos pelo plugin sincronizados com os binários construídos.

## [2.3.2-beta] - 2026-11-06

### Segurança

- Atualizada a dependência `org.json` para 20260719, corrigindo o processamento de números JSON sem limite associado ao CVE-2026-59171.
- Verificador e plugin limitam arquivos JSON a 16 MiB e inventários a 25.000 arquivos; hashes canônicos são calculados em fluxo.
- Gravações de relatórios, inventários e auditoria rejeitam links simbólicos e usam arquivos temporários exclusivos.
- Instalador substitui a biblioteca JSON antiga por uma versão corrigida e mantém backups para reversão.

### Corrigido

- Leituras de manifestos e anotações limitadas para impedir consumo excessivo de memória.
- Hashes dos binários permitidos pelo plugin sincronizados com o Verificador 1.2.5.

## [2.3.1-beta] - 2026-11-06

### Adicionado

- Release beta separada com instalador ZIP, JAR compilado do plugin e código-fonte versionados.
- Verificador de Integridade 1.2.4 disponível como JAR e EXE; hashes publicados no README, CHECKSUMS.md e versao_hash.md.

### Corrigido

- Cópia de hashes e histórico executada em segundo plano, evitando travamento da janela.
- Plugin aceita somente os hashes SHA-256 dos binários oficiais 1.2.4.



## [2.3.0-beta] - 2026-11-06

### Adicionado

- Distribuição do JAR do plugin e do Verificador de Integridade em JAR/EXE como arquivos separados da release.
- Histórico de hashes do verificador por versão em `versao_hash.md`.
- Documentação consolidada da arquitetura, uso, artefatos gerados, integridade e validação do plugin.

### Corrigido

- Cópia de hashes e do histórico para a área de transferência executada em segundo plano, evitando travar a janela do verificador.
- Hashes reconhecidos pelo plugin sincronizados com os binários estáveis do verificador 1.2.4.

### Modificado

- README atualizado com os hashes SHA-256 atuais e instruções para consultar o histórico de versões.
- README organizado com seções de apresentação, instalação, uso e licença.
- Instalador ZIP recomposto com o JAR do plugin e os arquivos de instalação atuais.
