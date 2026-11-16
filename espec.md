# Especificação Única Consolidada (`espec.md`) — Chat Captura IPED (`v2.6.9`) & Verificador de Integridade (`1.4.4`)

> **Versão de referência:** Plugin `2.6.9` | Verificador de Integridade `1.4.4` | Alvo: IPED `4.3.1` (Java 11) + Verificador JAR (Java 8+) / EXE Windows x64 (`.NET 10.0.15`)  
> **Documento único de especificação:** Este arquivo consolida e substitui todos os documentos anteriores dispersos (`ESPEC_UNICA_CHAT_CAPTURE_IPED.md`, `SPEC_CORRECAO_VULNERABILIDADES_FASE3.md`, `ESPEC_TECNICA_CORRECAO_PLUGIN_VERIFICADOR_INSTALADOR.md`, `ESPEC_TECNICA_CORRECAO_VULNERABILIDADES.md` e `ESPEC_TECNICA_CORRECAO_FASE4.md`).

---

## Legenda de Situação

- **Removido / Substituído:** Recurso, limite ou abordagem antiga excluída porque decisões arquiteturais ou de segurança mudaram a forma de atender ao requisito.
- **Ignorado / Fora do Escopo / Método Rejeitado:** Item intencionalmente não alterado (por definição explícita de escopo) ou proposta inicial de correção descartada após análise adversária porque causaria regressão, perda de atomicidade, incompatibilidade ou nova falha.
- **Não aplicado:** Item originalmente proposto em rascunhos/especificações passadas que não foi entregue na linha de código.
- **Aplicado / Especificação Ativa (O quê, Por quê e Como Aplicar):** Requisito e especificação técnica de implementação adotados em código, com motivação, arquivos-alvo, mecanismo e critérios de aceite.

---

# Parte I — O que foi Removido, Ignorado ou Não Aplicado (e o Motivo)

## 1.1 Conteúdo Removido / Substituído

| Item / Artefato Antigo | Situação | Motivo (`Por quê`) |
|---|---|---|
| `SPEC_ajustes_plugin_verificador.md`, `SPEC_CAPTURA_HTML_POR_MENSAGEM.md`, `SPEC_CENSURA_IMAGENS_HTML_MESSAGE.md`, `SPEC_CORRECAO_MARCADORES_CAPTURA_HTML.md`, `SPEC_CORRECOES_SEGURANCA_PLUGIN_VERIFICADOR.md`, `SPEC_LIMPEZA_REGISTROS_RAIZ_REEXTRACAO.md`, `SPEC_MIGRACAO_REEXTRACAO_LEGADA_HTML.md`, `SPEC_ORGANIZACAO_INTEGRIDADE_PUBLICO.md`, `SPEC_PAGINACAO_SELECAO_INCREMENTAL.md`, `SPEC_RENDERIZACAO_WHATSAPP_HTML.md`, `SPEC_verificador_integridade_plugin.md`, `ESPEC_UNICA_CHAT_CAPTURE_IPED.md`, `SPEC_CORRECAO_VULNERABILIDADES_FASE3.md`, `ESPEC_TECNICA_CORRECAO_*.md` | **Removido / Unificado** | Fragmentação dificultava saber qual contrato valia na versão atual; todo conteúdo válido, não aplicado, ignorado e removível foi consolidado exclusivamente neste `espec.md`. |
| `getRangeMediaHashes()` (MD5 no fluxo novo) e `matchesSensitiveImageFromPreviousCapture()` (MD5 de bloco do legado) em `html-message-v1` | **Removido / Não se aplica mais** | O formato `html-message-v1` usa SHA-256 sobre bytes exatos dos anexos resolvidos diretamente do caso (`SensitiveImageMatcher` em memória com cache por SHA-256). |
| Regra CSS `.capture-modal .capture-preview-image{filter:none}` | **Removido / Não se aplica mais** | O modal de pré-visualização não é descendente de `.capture-censored`; a regra tornou-se desnecessária. |
| Vinculação por `contactId` / pessoa física (`ESPECIFICACAO_NOTAS_LINKS` antiga) | **Substituído** | Substituída por referência explícita entre chats (`LinkedChatRef`, `chatRefId`); leitura de campos antigos mantida apenas por retrocompatibilidade. |
| Cálculo de hashes no JavaScript do relatório local e auditoria exclusiva em JSONL | **Substituído** | Substituído pelo Verificador Java independente (sem `crypto.subtle`) e cadeia cumulativa estruturada em `integridade.json` (mantendo JSONL apenas para log operacional do Re-Extrair). |
| `archiveStaleRootIntegrityReports` no modo **Limpar registros** | **Removido** | Substituído por `deleteRootIntegrityReportsIfExists`, que remove `integridade/` e `integridade.html` sem criar pastas de backup residuais na raiz quando há recaptura limpa (`_backup_integridade_*` tratado como auxiliar temporário). |
| Campo "Manifesto JSON público de uma captura `html-message-v1`", botão "Comparar manifesto por mensagem", área `manifestResult` e botão "Copiar hash calculado" na janela **Integridade Público** | **Removido da UI** | UI simplificada em dois blocos: (1) Hash SHA-256 público e (2) Histórico completo `historico-capturas.txt` (aceitando formato completo original ou anonimizado `CAPTURA|*`). O método `comparePublicMessageManifest` foi mantido em pacote apenas para testes. |
| Constantes de limite artificial no Verificador (`MAX_SELECTED_METADATA_BYTES`, `MAX_REPORT_ESTIMATED_CHARS`) e teto de `16 MiB` na gravação de relatórios (`atomic` / `atomicBatch`) e tetos cumulativos entre capturas em `verifyAllBounded` | **Removido** | Impediam verificar e gerar relatórios para pastas com grande volume de capturas ou chats extensos. A gravação passou a usar *streaming* transacional (`atomicBatchStreaming`), removendo o gargalo de memória sem teto artificial (preservando a assinatura `enforceReportBudget(List<Capture>)` onde referenciada por testes reflexivos). |
| Regexes de varredura HTML no Verificador (`HTML_TAG_PATTERN`, `HTML_META_TAG_PATTERN`, `HTML_SCRIPT_ELEMENT_PATTERN`, `HTML_STYLE_ELEMENT_PATTERN`) e métodos mortos `field(String, String)`, `number(String, String)`, `isAllowedPath` em `IntegrityVerifier.java` | **Removido** | A expressão regular `((?:"[^"]*"|'[^']*'|[^'">])*)` permitia bypass por apóstrofo/aspa isolada em atributo sem aspas (`CWE-184` / `CWE-79`), e os métodos privados sem chamadores configuravam código morto (`CWE-1333`/`CWE-561`). Substituídos por scanner HTML léxico determinístico por máquina de estados (`scanHtmlElements` / `parseHtmlTagStrict`). |
| Instalação redundante de `lib\json-20260719.jar` e `lib\opencv-4120.jar` em `IPED\lib\` pelo instalador e arquivo obsoleto `iped-viewers-impl-4.3.1-2.6.8.jar` | **Removido** | Causava duplicação de classes `org.json.*` e `org.opencv.*` no classpath do IPED base (`org/opencv/**` já vai embutido no fat-JAR do plugin, e `lib\json-20250517.jar` recebe a versão `20260719` com backup `.bak` para honrar o `Class-Path` dos manifests do IPED `4.3.1` e mitigar `CVE-2026-59171`). |
| Hash SHA-256 aceito para versões `<= 1.4.3` do Verificador em `VerifierIntegrity` | **Removido da allowlist** | Política de allowlist exclusiva estrita: o plugin `2.6.9` aceita apenas os binários oficiais do Verificador `1.4.4`. |

---

## 1.2 Conteúdo Ignorado / Fora de Escopo / Abordagens Iniciais Rejeitadas

### A. Exclusões Explícitas de Escopo
1. **Atualização do runtime `Microsoft.NETCore.App.Runtime.win-x64` além de `10.0.15` — Ignorada:**
   - **Motivo:** Após mitigação da série `10.0.14` (5 CVEs `HIGH` no OSV), o runtime embutido no lançador (`Launcher.csproj`) foi fixado em **`10.0.15`** (`0` CVEs no OSV) e o mantenedor solicitou explicitamente **não atualizar** o `Microsoft.NETCore.App.Runtime` além dessa versão na build estável. O script `build-single-exe.ps1 -OfflineBuild` apenas valida `>= 10.0.15` usando o cache local sem restaurar versão superior.
2. **Análise e modificação do código base do IPED `4.3.1` e suas bibliotecas de terceiros originais — Ignorada:**
   - **Motivo:** Conforme escopo definido pelo projeto, vulnerabilidades e código nativo da distribuição base do IPED `4.3.1` (`iped-4.3.1.zip`) estão fora de alcance. As únicas integrações permitidas são a substituição do `iped-viewers-impl-4.3.1.jar`, atualização segura de `lib\json-20250517.jar` (necessária pelo `MANIFEST.MF` do IPED), adição de chaves em `localization/` e cópia dos binários do Verificador em `verificador-integridade\`.

### B. Abordagens Iniciais Rejeitadas no Loop de Validação Adversária

| Achado / Alvo | Abordagem Inicial Cogitada | Motivo da Rejeição (`Por quê foi ignorada`) | Abordagem Adotada |
|---|---|---|---|
| **Gravação sem limite de relatório (`CWE-770`)** | Substituir `atomicBatch` por escrita direta em arquivo com `BufferedWriter`. | **Perda de atomicidade transacional (`CWE-459`):** gravação direta sem estágio `.tmp` + backup `.bak` quebraria o rollback tudo-ou-nada caso ocorresse falha no meio da gravação de um dos relatórios (`SecurityRegressionTest.batchFailurePreservesPreviousReports`). | `ReportWriter` + `atomicBatchStreaming` em duas fases (`.tmp` -> `.bak` -> promoção/rollback). |
| **`CaptureIntegrity.sha256(Path)` (`CWE-367`, `CWE-59`)** | Adicionar `LinkOption.NOFOLLOW_LINKS` junto com limite fixo de `64 MiB`. | **Quebra funcional:** anexos reais (vídeos/arquivos em `attachments/`) podem exceder `64 MiB` legitimamente e falhariam durante `artifactInventory` / `compareInventory`. | Checar `isLinkOrReparse` + `Files.isRegularFile(..., NOFOLLOW_LINKS)` e abrir stream via `NOFOLLOW_LINKS` mantendo leitura em fluxo $O(1)$ sem teto artificial em `sha256(Path)`. |
| **Checagem de ancestrais de `rejectLinks` (`CWE-59`)** | Trocar `Files.isSymbolicLink` por `isLinkOrReparse` (que inclui `isOther()`) em todos os ancestrais acima de `normalizedRoot`. | **Incompatibilidade Windows:** pastas ancestrais como OneDrive Known Folder Move (`IO_REPARSE_TAG_CLOUD`), pontos de montagem ou deduplicação NTFS retornam `isOther() == true`; bloquear ancestrais impediria rodar o IPED nesses volumes. | Manter `Files.isSymbolicLink` nos ancestrais acima de `normalizedRoot` e aplicar `isLinkOrReparse` + `toRealPath().startsWith(...)` estrito em `normalizedRoot` e em toda sua subárvore. |
| **Bloqueios `executeScriptAndWait` / `invokeAndWait` (`CWE-833`)** | Tornar todas as chamadas assíncronas indistintamente. | **Condição de corrida (`CWE-362`):** `validateCaptureStart` precisa devolver `boolean` de forma síncrona na thread trabalhadora `IPED-chat-capture-start` antes de iniciar a captura. | Trocar apenas `showAttachNotFound` (`invokeLater`) e tirar `getMessagePreviewText` da Swing EDT (`runOffEdtIfNeeded` em `annotationAction`), mantendo `validateCaptureStart` síncrono. |
| **Identificadores em `ChatAnnotationStore` (`CWE-328`)** | Migrar `sha1Hex` (`chatKey` 40 hex / `chatRefId` 8 hex) para SHA-256 / 16 hex chars. | **Quebra de retrocompatibilidade:** `anotacoes.json` já gravados em casos periciais dependem de `chatKeyOf` e `chatRefIdOf` exatamente como gravados. | Preservar o formato determinístico de chave e endurecer `ChatAnnotationStore.load()` com `readJsonUtf8Limited(..., MAX_STORE_BYTES)` (profundidade JSON $\le 64$). |

---

## 1.3 Itens Não Aplicados

| Item (Origem) | Situação | Motivo |
|---|---|---|
| Conversão dos diálogos do Verificador em `JFrame`/`JDialog` com botões customizados de "sempre no topo" / "minimizar" (`SPEC_ajustes_plugin_verificador.md`, seções 2–3) | **Não aplicado** | O Verificador permanece usando diálogos padrão (`JOptionPane`/`JFrame`) com decoração e controles nativos do SO. Somente o ajuste do alerta informativo no plugin (`INFORMATION_MESSAGE`, `alertShown`) foi incorporado. |
| Campos `censoredItemHashes` / `sensitiveMediaHashes` em disco/manifesto | **Não aplicado** | Nunca existiram no formato real; o mecanismo implementado armazena o resultado de censura no bloco `censorship` do `manifest.json` usando cache por SHA-256 durante `runHtmlMessageCapture`. |
| Execução automatizada no WebView real completo do IPED em caso pericial ativo | **Fora de automação (Operacional)** | A validação visual ponta a ponta dentro da interface gráfica do IPED `4.3.1` em casos reais é executada manualmente pelo operador antes do uso em produção. |

---

# Parte II — Especificação do que é / Vai Ser Aplicado (O quê, Por quê e Como Aplicar)

## 2.1 Arquitetura Principal e Contrato de Captura (`html-message-v1`, Re-Extrair, Censura e Verificador)

### O quê e Por quê
Garantir capturas offline auditáveis de trechos de conversas do WhatsApp no IPED `4.3.1` sem depender de screenshots frágeis como conteúdo principal, preservando proveniência criptográfica local, migração segura de capturas antigas, censura visual não destrutiva e auditoria offline sem confiança em JavaScript dinâmico.

### Como Aplicar (Contratos Principais)
1. **Formato `html-message-v1` (`HtmlLinkViewer`, `CaptureIntegrity`, `CaptureBoundaryResolver`):**
   - Toda nova captura grava `captureFormat: "html-message-v1"` em `manifest.json`, acompanhado de `whatsapp.html`, `messages.json`, `integridade.json` (`schemaVersion >= 2`) e pasta `attachments/` (quando não embutido).
   - O intervalo selecionado entre início e fim é **inclusivo**; elementos auxiliares de interface não entram no inventário. Separador de data como marcador inicial resolve para a primeira mensagem seguinte com âncora persistida; separador de data como fim é bloqueado.
   - Suporte obrigatório na resolução de fronteiras a IDs regulares, registros de chamada (`<digits>_call:<hex>`), mensagens removidas/carved (`-?<digits>_<id>`) e timestamps internos (`iped-capture-date-...`).
   - Anexos acima de `8 MiB`, ausentes no caso ou que excedam o orçamento acumulado são ignorados isoladamente com log `WARN` (`sanitization`), sem abortar a criação da pasta final da captura.
   - Capturas antigas em screenshots continuam suportadas em leitura e verificação polimórfica.
2. **Re-Extrair, Migração Transacional e Limpeza da Raiz:**
   - Opção opt-in *"Migrar capturas legadas para HTML por mensagem"* + opção dependente *"Limpar registros e criar como nova captura"*.
   - A preparação ocorre em pasta temporária irmã (`staging`), validada integralmente antes de renomear a pasta antiga transacionalmente; falhas anteriores à promoção mantêm a pasta original intacta.
   - Quando há migração com preservação de histórico, `migration.json` e `legacy-annotations.json` entram obrigatoriamente no inventário de `integridade.json`.
   - No modo *Limpar registros*, `deleteRootIntegrityReportsIfExists` remove `integridade/` e `integridade.html` da raiz quando `resetLegacyRecords && replaced > 0`.
3. **Censura Visual CSS (`CaptureCensorship`, `SensitiveImageMatcher`):**
   - Aplicada exclusivamente por CSS na segunda tag `<style>` + classe `capture-censored` e atributo `data-capture-censored="1"`, sem JavaScript adicional e sem alterar `messageHash` ou a fórmula de `captureRootHash`.
   - Comparação de imagens por `MatOfByte` / `Imgcodecs.imdecode` e hash exato, com falha fechada caso a pasta de referências esteja inacessível ou uma imagem casada não possua elemento HTML correspondente.
   - Re-Extrair compara `presentationDigest` e substitui a captura quando a censura muda (`recapture_censorship_presentation_changed`).
4. **Renderização Offline (`whatsapp.html`) e Verificador (`IntegrityVerifier`):**
   - Apresentação autônoma com tema WhatsApp do IPED `4.3.1`, modal local para imagem/PDF/documentos (com `sandbox=""` em iframe PDF) e controles de mídia que pausam e reiniciam ao fechar/navegar.
   - Normalização do MIME de imagens embutidas no cabeçalho pela assinatura real dos bytes (aceitando compatibilidade legada `data:image/jpg` com bytes PNG).
   - Verificador suporta modos **Integridade Local** (paginado de `100` em `100` capturas) e **Integridade Público** (seleção automática completa + comparação dual com formato original `CAPTURA|<pasta>-...` ou anonimizado `CAPTURA|*-YYYYMMDD-HHMMSS` e respectivos SHA-256).

---

## 2.2 Compatibilidade com IPED `4.3.1` Oficial, Ciclo de Vida, Runtime `.NET` e Launcher (`A-01`..`A-03`, `B-01`..`B-07`)

| ID | O que Aplicar | Por quê (Falha / CWE) | Como Aplicar |
|---|---|---|---|
| **A-01** | Invocação reflexiva de `AttachmentSearcher.openItem` | `iped-viewers-api-4.3.1.jar` oficial não declara `openItem(String,String,String)`; chamada direta quebra compilação e gera `NoSuchMethodError` em runtime. | Em [HtmlLinkViewer.java](src/iped/viewers/HtmlLinkViewer.java), centralizar em `openSearcherItem(AttachmentSearcher, String, String, String)` tentando `openItem(String,String,String)`, fallback `openItem(String,String)` e retorno `false` com `LOGGER.warn` caso ausente. |
| **A-02** | Instalador atômico sem duplicação em `lib\` | Duplicar `json-*.jar`/`opencv-*.jar` no `IPED\lib\` conflita com o classpath; falhas intermediárias sem `:ROLLBACK` e chaves modificadas em `.properties` deixavam o IPED inconsistente. | Em [instalar_captura_chat_zap.bat](instalar_captura_chat_zap/instalar_captura_chat_zap.bat): atualizar `lib\json-20250517.jar` com `.bak`, remover eventuais `json-20260719.jar`/`opencv-4120.jar` soltos em `lib\`, checar `if not defined IPED_ROOT` e chamar `:ROLLBACK` em falhas. Em [install-localization.ps1](instalar_captura_chat_zap/install-localization.ps1): inserir ausentes e atualizar chaves do plugin alteradas preservando chaves nativas do IPED. |
| **A-03** | Liberação de recursos no `dispose()` | Fechar visualizadores ou `ChatCaptureLinkedWindow` vazava threads `uiScriptExecutor` e objetos nativos OpenCV (`CWE-401`). | Sobrescrever `@Override public void dispose()` em [HtmlLinkViewer.java](src/iped/viewers/HtmlLinkViewer.java) (fechando `SensitiveImageMatcher`, executando `uiScriptExecutor.shutdownNow()` e `super.dispose()`) e em [ChatCaptureLinkedWindow.java](src/iped/viewers/ChatCaptureLinkedWindow.java) garantindo `linkedViewer.dispose()` em `try/finally`. |
| **B-01** | Runtime `.NET 10.0.15` no executável x64 | O cache `10.0.14` continha 5 CVEs `HIGH` no OSV (`CWE-1104`). | Em [Launcher.csproj](verificador-integridade/launcher/Launcher.csproj) fixar `<RuntimeFrameworkVersion>10.0.15</RuntimeFrameworkVersion>`, configurar fonte NuGet em `launcher/NuGet.Config` e validar `>= 10.0.15` em [build-single-exe.ps1](verificador-integridade/build-single-exe.ps1). |
| **B-02** | Bloqueio de `ReparsePoint` e limites de descompressão no Launcher C# | Extração e hashing ZIP no EXE não bloqueavam junções NTFS em subpastas nem impunham limites de entradas/bytes (`CWE-59`, `CWE-409`). | Em [Program.cs](verificador-integridade/launcher/Program.cs): implementar `EnsureNoReparsePointHierarchy(rootDir, targetPath)`, chamar em `VerifyExtractedFiles`, limitar `ExtractArchiveSafely` (`MaxArchiveEntries = 4096`, `MaxArchiveTotalBytes = 512 MiB`) e cobrir no `RunLauncherSelfTest()`. |
| **B-03** | Simetria HTML/MIME Plugin ↔ Verificador | Diferenças entre tags/atributos permitidos no cabeçalho gerado pelo plugin e na validação do verificador causavam falso positivo de adulteração. | Alinhar `IntegrityVerifier` e `HtmlLinkViewer` no mesmo conjunto de tags estruturais passivas (`u, hr, h1..h6, table, thead, tbody, tr, td, th, em`), atributos seguros (`dir, lang, colspan, rowspan`, `data-*` validados por `isAllowedSafeDataAttribute`) e assinatura por magic bytes para `image/png, image/jpeg, image/gif, image/webp`. |
| **B-04** | Detecção estrita de `<a\b` em `HtmlLinkViewer` | `lastIndexOf("<a")` confundia `<audio>` / `<aside>` com tag âncora, impedindo linkagem de anexos subsequentes (`CWE-79`/bug). | Adicionar `static boolean isInsideOpenAnchorTag(String beforeHtml)` usando fronteira `(?is)<a(?=[\\s>/])` vs `(?is)</a\\s*>` e usar em `linkInlineCaptureImages` e `replaceInlineCapturePlayers`. |
| **B-05** | `html.disable` em diálogos e HTTPS estrito no link externo | Diálogos Swing com mensagens dinâmicas aceitavam `<html>` e `VerifierIntegrity` abria URI sem exigir `https` (`CWE-79`, `CWE-749`). | Reutilizar `plainLabel` / `JTextArea` não editável e checar `"https".equalsIgnoreCase(uri.getScheme())` antes de `Desktop.browse` em [VerifierIntegrity.java](src/iped/viewers/VerifierIntegrity.java). |
| **B-06** | Limites explícitos em `Files.list`, `discover` e `SensitiveImageMatcher` | Varreduras sem teto e carregamento ilimitado de `Mat` permitiam exaustão de memória/tempo (`CWE-400`). | Aplicar `.limit(MAX_SCAN_ENTRIES)` (`100_000L`; `10_000L` em `loadFrames`), limitar `MAX_REFERENCE_IMAGES = 512` em `SensitiveImageMatcher`, e abortar `IntegrityVerifier.discover` se ultrapassar `MAX_SCAN_ENTRIES`. |
| **B-07** | Eliminação de `Files.size` desprotegidos | Checar regularidade e depois chamar `Files.size(path)` deixava janela TOCTOU de symlink (`CWE-59`, `CWE-367`). | Introduzir `safeRegularFileSize(Path)` via `Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)` verificando `!isSymbolicLink() && !isOther() && isRegularFile()` em `CaptureIntegrity`, `HtmlLinkViewer` e `IntegrityVerifier`, além de checar `isLinkOrReparse(root)` em `CaptureIntegrity.listRegularFilesSafe`. |

---

## 2.3 Segurança, Confiabilidade, Streaming Transacional e Concorrência (Fases 3 e Intermediária)

### Auditoria Fase 3 (`F-01`..`F-10` consolidados em `1.4.2`+)
1. **`F-01` — Amarração de `artifacts` e cadeia `events` (`IntegrityVerifier.java`):**
   - *Por quê (`CWE-345`):* Impedir que `integridade.json` com `artifacts` vazio, caminhos duplicados por diferença de caixa ou `contentRootHash` desconectado seja aceito.
   - *Como aplicar:* Em `verifyHistory`, rejeitar `artifacts` nulo/vazio, detectar caminhos duplicados em minúsculas e exigir igualdade entre o `contentRootHash` do último evento e `declaredRoot`. Em `html-message-v1`, exigir que todos os arquivos de conteúdo presentes constem em `artifacts`.
2. **`F-02` — Fail-closed em `captureRootHash` e `--verify-only` (`IntegrityVerifier.java`):**
   - *Por quê (`CWE-345`, `CWE-697`):* `.startsWith("consistente com registro local")` e divergência tolerada em `captureRootHash` podiam mascarar adulteração.
   - *Como aplicar:* Lançar `IOException` se `captureRootHash` divergir em `verifyMessageCapture` e introduzir `isConsistentLocalHistoryStatus(String)` exigindo correspondência exata com os três estados locais válidos em `runCli`.
3. **`F-03` — Estados saudáveis em `writeReports` (`IntegrityVerifier.java`):**
   - *Por quê:* Itens de capturas `html-message-v1` apareciam como `"bad"` (`0 íntegros`) no HTML.
   - *Como aplicar:* Substituir comparação rígida por `isHealthyVerifierItemState(i.state)` no contador e na classe CSS.
4. **`F-04` — `html.disable` em `JLabel`/`JCheckBox` (Swing BasicHTML `CWE-79`/`CWE-116`):**
   - *Como aplicar:* Criar e utilizar `plainLabel` / `plainCheckBox` com `putClientProperty("html.disable", Boolean.TRUE)` em `IntegrityVerifier`, `ChatCapturePanel`, `HtmlLinkViewer`, `ChatCaptureLinkedWindow`, `ChatNoteDialog` e `ChatPreflightDialog`.
5. **`F-05` — Bloqueio de comentários, CDATA e instruções `<?` (`IntegrityVerifier.java`):**
   - *Como aplicar:* Em `validateRenderedCaptureHtml`, rejeitar `<!--`, `-->`, `<![cdata[` e `<?` com `IOException` em `presentationVersion` `0` e `2`.
6. **`F-06` & `F-10` — Limpeza temporária e entradas ZIP de 0 bytes (`Program.cs`):**
   - *Como aplicar:* Adicionar `ShouldCleanupStaleTempDirectory` (janela de tolerância de `2` min quando `.lock` ausente), `DeleteDirectorySafely` (sem seguir `ReparsePoint`), checar diretório por `/` / `\` / `string.IsNullOrEmpty(entry.Name)` em `ExtractArchiveSafely`, e validar ambos em `RunLauncherSelfTest()`.
7. **`F-07` — Indexação normalizada em comparação pública (`IntegrityVerifier.java`):**
   - *Como aplicar:* Modelar `PublicMessageIndex` / `parsePublicMessageIndex` normalizando `key`/`messageHash` para minúsculas, rejeitando duplicatas e validando `(domId, key, messageHash)`.
8. **`F-08` — Comparação canônica de `migration.json` e `schemaVersion >= 2`:**
   - *Como aplicar:* Em [HtmlLinkViewer.java](src/iped/viewers/HtmlLinkViewer.java), comparar usando `CaptureIntegrity.canonicalJson(migration).equals(CaptureIntegrity.canonicalJson(migrationFile))`; em `IntegrityVerifier.java`, rejeitar `schemaVersion < 2` em `html-message-v1`.
9. **`F-09` — Escape HTML em alerta e carregamento seguro de ícone:**
   - *Como aplicar:* Em [VerifierIntegrity.java](src/iped/viewers/VerifierIntegrity.java), aplicar `htmlEscape(...)` em `f.getFileName()` e hashes exibidos no `JTextPane`; em `IntegrityVerifier.verifierIcon()`, carregar exclusivamente do classpath (`/imagem doc/verificador-icon.png` / `/verificador-icon.png`), ignorando arquivos soltos no diretório de trabalho.

### Streaming Transacional, Anti-TOCTOU e Concorrência EDT (`1.4.3` / `2.6.8`)
1. **Gravação em Streaming (`IntegrityVerifier.java`):**
   - *Por quê:* Evitar `OutOfMemoryError` e falha de orçamento ao gerar `integridade.html`, `integridade-detalhado.json`, `integridade.csv` e `historico-capturas.txt` para chats completos sem sacrificar atomicidade.
   - *Como aplicar:* Criar interface `ReportWriter` e método `atomicBatchStreaming(Path root, Map<Path, ReportWriter> outputs)` escrevendo via `BufferedWriter` em arquivos `.tmp`, com backup `.bak` e rollback em falhas. Manter delegadores `atomicBatch(Map<Path,String>)` para compatibilidade com testes.
2. **Abertura `NOFOLLOW_LINKS` antes de tamanho + `rejectLinks(normalizedRoot)`:**
   - *Como aplicar:* Validar `isLinkOrReparse` e `Files.isRegularFile(..., NOFOLLOW_LINKS)` antes de tamanho/stream em `CaptureIntegrity.sha256`, `CaptureIntegrity.readUtf8Limited`, `IntegrityVerifier.hash`, `IntegrityVerifier.readUtf8Limited` e `HtmlLinkViewer.annotateSavedChat` (via `CaptureIntegrity.resolveContained`). Em `IntegrityVerifier.rejectLinks`, validar `isLinkOrReparse(normalizedRoot)` logo após o loop de ancestrais.
3. **Resolução DOM em lote e remoção de bloqueios da Swing EDT (`HtmlLinkViewer.java`):**
   - *Como aplicar:* Implementar `getOrderedMessageIndexMap()` para resolver todos os índices de mensagens em uma única chamada `executeScriptAndWait` (`$O(M+A)$`), mover `getMessagePreviewText` para `runOffEdtIfNeeded` antes de `SwingUtilities.invokeLater` em `annotationAction`, trocar `invokeAndWait` por `invokeLater` em `showAttachNotFound`, e serializar `searchChat` via `AtomicInteger searchGeneration` no `uiScriptExecutor`.
4. **Limites estruturais em `SavedChatPickerDialog`, `ChatAnnotationStore` e default em `validate-plugin-jar.ps1`:**
   - *Como aplicar:* Impor `MAX_PICKER_CAPTURES = 10000` em `SavedChatPickerDialog.scanSafeCaptureEntries`; trocar para `CaptureIntegrity.readJsonUtf8Limited(storeFile, MAX_STORE_BYTES)` em `ChatAnnotationStore.load()`; definir `$Jar` padrão em `validate-plugin-jar.ps1`.

---

## 2.4 Endurecimento Final (Fase 4 + Defesa Swing `v2.6.9` / `1.4.4`)

| ID | O que Aplicar | Por quê (Vulnerabilidade / Falha) | Como Aplicar (`Arquivos`, `Símbolos` e `Passos`) |
|---|---|---|---|
| **Fase4 F-01** | Scanner HTML léxico determinístico por máquina de estados em `validateRenderedCaptureHtml` | `[Alta - CWE-184 / CWE-79]` Regexes de tag HTML permitiam bypass via aspa/apóstrofo isolado em atributo sem aspas (ex.: `<meta ... a=b'>`, `<iframe ... a=b'>`, `<a ... a=b'>`, `<svg ... a=b'>`). | Em [IntegrityVerifier.java](verificador-integridade/src/IntegrityVerifier.java): implementar `scanHtmlElements` / `parseHtmlTagStrict` rejeitando `<` fora de aspas, `/` fora de fechamento `/>`, caracteres `'`, `"`, `` ` ``, `<`, `=` antes de `=` ou em atributos sem aspas, fechamentos `</...>` com atributos, declarações/PIs fora de `<!doctype ...>` estrito; consumir conteúdo bruto até `</script\s*>`/`</style\s*>`; garantir validação de 100% das tags em `presentationVersion` `0` e `2`. |
| **Fase4 F-02** | Limpeza determinística ao fechar `ChatCaptureLinkedWindow` pelo `X` + guarda em `loadFile` | `[Média - CWE-401 / CWE-362]` Janela herdava `HIDE_ON_CLOSE` (vazava `WebView`/`uiScriptExecutor` ao clicar no `X`), e `loadLinkedChat` chamava `loadFile` após `300 ms` mesmo com janela já descartada. | Em [ChatCaptureLinkedWindow.java](src/iped/viewers/ChatCaptureLinkedWindow.java): definir `setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)`, manter `AtomicBoolean closed` e interromper `loadThread` no `dispose()`, abortando se `closed.get()`. Em [HtmlLinkViewer.java](src/iped/viewers/HtmlLinkViewer.java): retornar imediatamente em `loadFile` se `disposed == true`. |
| **Fase4 F-03** | Seleção paginada *"Marcar todos"* no `Re-Extrair` sem incluir capturas inválidas | `[Baixa/Média - UX/Bug]` Ao usar *"Marcar todos"* em múltiplas páginas, pastas `!job.valid` apareciam marcadas e entravam em `selectedFolders`, gerando falhas falsas em `runRecaptureBatch`. | Em [HtmlLinkViewer.java](src/iped/viewers/HtmlLinkViewer.java): utilizar `shouldSelectRecaptureCheckBox`, `collectSelectedRecaptureFolders` e `filterValidRecaptureFolders`; nunca marcar `!job.valid`, excluir `knownInvalidIds` e revalidar lotes não visitados via `loadRecaptureJobs(..., descriptorOnly=true)` antes de iniciar. |
| **Fase4 F-04** | Proteção de caminhos com `!` em `instalar_captura_chat_zap.bat` | `[Baixa/Média - Bug]` `setlocal enabledelayedexpansion` corrompia caminhos contendo `!` (ex.: `Caso!2026`). | Em [instalar_captura_chat_zap.bat](instalar_captura_chat_zap/instalar_captura_chat_zap.bat): usar `setlocal DisableDelayedExpansion`, substituir o laço por `for /L %%L in (1,1,6) do call :PROBE_IPED_STEP`, detectar `iped-4.3.1` adjacente e trocar `!IPED_ROOT!` por `if not defined IPED_ROOT`. |
| **Fase4 F-05** | `openFile` com validação `NOFOLLOW_LINKS` e `Desktop.Action.OPEN` | `[Baixa - CWE-59]` `Desktop.getDesktop().open` era chamado sem validar reparse point/symlink no arquivo ou diretório pai imediato. | Em [IntegrityVerifier.java](verificador-integridade/src/IntegrityVerifier.java): implementar `validateOpenableRegularFile(Path)` checando não-nulo, `!isLinkOrReparse` no arquivo e no pai imediato, `Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)` e `Desktop.getDesktop().isSupported(Desktop.Action.OPEN)`. |
| **Fase4 F-06** | Tratamento de `\` final em `install-localization.ps1` e remoção de código morto | `[Baixa]` Linha terminada em barra invertida ímpar fazia `java.util.Properties.load()` concatenar e engolir a primeira chave do plugin; métodos `field`/`number` eram código morto. | Em [install-localization.ps1](instalar_captura_chat_zap/install-localization.ps1): detectar `(?<!\\)(\\\\)*\\$` no final do conteúdo existente e neutralizar a continuação antes de concatenar chaves do plugin. Remover `field`/`number` de `IntegrityVerifier.java`. |
| **Defesa Swing (`v2.6.9`)** | `html.disable=true` antes do texto em `SearchHitRenderer` e `SavedChatPickerDialog` | `[Baixa - Defesa em Profundidade CWE-79]` Em `ListCellRenderer`, definir `html.disable` após `super.getListCellRendererComponent(...)` deixava a primeira medição/criação de `BasicHTML` ocorrer antes da flag se texto iniciasse com `<html>`. | Em [ChatCapturePanel.java](src/iped/viewers/ChatCapturePanel.java#L429-L454) (`SearchHitRenderer`) e [SavedChatPickerDialog.java](src/iped/viewers/SavedChatPickerDialog.java#L46-L61): chamar `putClientProperty("html.disable", Boolean.TRUE)` **antes** de `super.getListCellRendererComponent(...)` / atribuição de texto. Coberto em [SwingHtmlDisableTest.java](tests/iped/viewers/SwingHtmlDisableTest.java). |

---

# Parte III — Como Compilar, Empacotar, Verificar e Critérios de Aceite

## 3.1 Ordem Obrigatória de Build e Sincronização de Hashes (`v2.6.9` / `1.4.4`)

1. **Verificador de Integridade (`1.4.4`):**
   - Atualizar versão em `verificador-integridade/VERSION`, `IntegrityVerifier.VERIFIER_VERSION`, `Launcher.csproj`, `build-single-exe.ps1` e `build.bat`.
   - Executar com JDK 8 + JDK 11 + jpackage + `.NET 10.0.15`:
     ```powershell
     powershell -NoProfile -ExecutionPolicy Bypass -File "verificador-integridade\build-single-exe.ps1" -OfflineBuild
     ```
   - Copiar `verificador-integridade\dist\integridade-verifier.exe` e `verificador-integridade\integridade-verifier.jar` para `verificador-integridade\` e `instalar_captura_chat_zap\`.
   - Validar `--version` (`Verificador de Integridade 1.4.4`) e `--self-test` (`SELF_TEST_OK 1.4.4`, saída `0`) em ambos os binários e registrar os SHA-256 reais (`C37D52F2...` JAR / `A57CB7E8...` EXE).
2. **Allowlist do Plugin e Compilação do Fat-JAR (`2.6.9`):**
   - Atualizar `VerifierIntegrity.VALID_JAR_HASHES` e `VALID_EXE_HASHES` ([VerifierIntegrity.java](src/iped/viewers/VerifierIntegrity.java#L21-L22)) exclusivamente com os hashes da `1.4.4` e incrementar `CHAT_CAPTURE_PLUGIN_VERSION = "2.6.9"` ([HtmlLinkViewer.java](src/iped/viewers/HtmlLinkViewer.java#L150)).
   - Compilar as `19` classes em `src/iped/viewers/*.java` com `-source 11 -target 11` usando `--module-path` do JavaFX SDK e `-cp "iped-4.3.1\lib\*;instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar"`.
   - Substituir apenas as classes recompiladas (`67` `.class`) sobre a base do fat-JAR mantendo todas as `636` entradas (incluindo `iped/viewers/localization/*`, `org/opencv/**`, `META-INF/jpms.args` e bundles `.properties`), atualizar `Implementation-Version: 2.6.9` em `META-INF/MANIFEST.MF` e validar com [validate-plugin-jar.ps1](instalar_captura_chat_zap/validate-plugin-jar.ps1).
   - Atualizar [VerifierIntegrityHashTest.java](tests/iped/viewers/VerifierIntegrityHashTest.java), [test_verifier_java_integration.py](teste_antes_de_aplicar/test_verifier_java_integration.py) e [instalar_captura_chat_zap.bat](instalar_captura_chat_zap/instalar_captura_chat_zap.bat).
3. **Empacotamento do Instalador ZIP e Documentação:**
   - Finalizar [LEIA-ME.txt](LEIA-ME.txt) e os arquivos em `instalar_captura_chat_zap\` **antes** de gerar `instalador-chat-captura-IPED-2.6.9-verificador-1.4.4.zip` (`12` entradas: `LEIA-ME.txt` + `instalar_captura_chat_zap/...`).
   - Recalcular bytes/SHA-256 do `.zip` final e atualizar [CHECKSUMS.md](CHECKSUMS.md), [RELEASE_NOTES.md](RELEASE_NOTES.md) e [versao_hash.md](versao_hash.md).

## 3.2 Suíte Obrigatória de Verificação Pós-Aplicação

1. **Integridade do JAR do plugin:** `instalar_captura_chat_zap\validate-plugin-jar.ps1 -ExpectedSha256 <SHA256_PLUGIN>` -> `exit 0`.
2. **Autoteste e CLI do Verificador:** `integridade-verifier.jar` e `integridade-verifier.exe` com `--version` e `--self-test` -> `Verificador de Integridade 1.4.4` e `SELF_TEST_OK 1.4.4`.
3. **Protótipo e especificação de segurança:** `verificador-integridade\prototype\test-security-spec.ps1` -> `8 PASS; 0 FAIL`.
4. **Suíte principal (`teste_antes_de_aplicar\run_all_tests.ps1`, com `TEST_VERIFIER_EXE` apontando para o `.exe`):**
   - Contrato `html-message-v1`, aceitação de pipeline, ponta a ponta, fronteiras de marcador, migração legada (`spec` + contrato de produção), censura HTML, validação Fase 4 (`test_fase4_spec_validation.py`), `PublicManifestComparisonHarness`, `CaptureCensorshipTest`, `CaptureBoundaryResolverTest`, `VerifierIntegrityHashTest`, integração Java+EXE (`test_verifier_java_integration.py` — 28 testes), testes JDK 11 completos (`SwingHtmlDisableTest`, `CaptureIntegrityTest`, `ChatAnnotationStoreRoundTripTest`, `OpenCvLoaderTest`, etc.) e suíte JDK 8 do Verificador (`SecurityRegressionTest`, `IndexHashParityTest`) -> `ALL_RUN_ALL_TESTS_PASSED`.
5. **Regressões complementares em `tests\`:** `run_capture_header_mime_regression.ps1`, `run_svg_sanitizer_compatibility.ps1` e `run_verifier_embedded_image_compatibility.ps1` -> `0` falhas.
