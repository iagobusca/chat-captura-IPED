# Especificação Técnica de Correção — Auditoria de Segurança e Falhas (Fase 4)

- **Status**: Aplicada, empacotada e validada (`plugin 2.6.9` / `verificador 1.4.4`)
- **Escopo**: Plugin `chat-captura IPED` (`src/iped/viewers`), Verificador de Integridade (`verificador-integridade`) e Instalador (`instalar_captura_chat_zap`)
- **Exclusões explícitas**: Atualização de `Microsoft.NETCore.App.Runtime` (`10.0.15`) e análise de código base do IPED `4.3.1` fora do escopo do plugin/verificador/instalador.
- **Metodologia / Classificação**: `C:\Users\Tom\.agents\skills\encontrar-vulnerabilidade\SKILL.md` (SAST + SCA + CWE local `http://127.0.0.1:8081` + OSV).

---

## Sumário das Correções Especificadas (`F-01` a `F-06`)

| ID | Severidade | Categoria | CWE | Alvo(s) |
|---|---|---|---|---|
| **F-01** | **Alta** | `security-vulnerability` | `CWE-184`, `CWE-79` | `verificador-integridade/src/IntegrityVerifier.java` |
| **F-02** | **Média** | `performance-bug`, `functional-bug` | `CWE-401`, `CWE-362` | `src/iped/viewers/ChatCaptureLinkedWindow.java`, `src/iped/viewers/HtmlLinkViewer.java` |
| **F-03** | **Baixa/Média** | `functional-bug`, `usability-ux-bug` | — | `src/iped/viewers/HtmlLinkViewer.java` |
| **F-04** | **Baixa/Média** | `functional-bug`, `compatibility-bug` | — | `instalar_captura_chat_zap/instalar_captura_chat_zap.bat` |
| **F-05** | **Baixa** | `security-vulnerability` | `CWE-59` | `verificador-integridade/src/IntegrityVerifier.java` |
| **F-06** | **Baixa** | `compatibility-bug`, `integration-bug` | — (`CWE-1333` código morto) | `instalar_captura_chat_zap/install-localization.ps1`, `verificador-integridade/src/IntegrityVerifier.java` |

---

## Detalhamento Técnico e Critérios de Aceite

### F-01 `[ALTA]` — Bypass do validador regex HTML e de `<meta>` (`HTML_TAG_PATTERN` / `HTML_META_TAG_PATTERN`)
- **Localização atual**: `verificador-integridade/src/IntegrityVerifier.java` (`#L41-L42`, `#L1294-L1339`, `#L1383-L1415`).
- **Problema**: `HTML_TAG_PATTERN` e `HTML_META_TAG_PATTERN` usam `((?:"[^"]*"|'[^']*'|[^'">])*)`, que falha ao casar qualquer tag que possua `'` ou `"` isolado em atributo não-entre-aspas (ex.: `<meta http-equiv="refresh" content="0;url=https://exemplo.invalid" a=b'>`, `<iframe src="..." a=b'>`, `<a href="https://exemplo.invalid" a=b'>`, `<svg ... a=b'>`). No fluxo `presentationVersion == 2` apenas `<script>` e `<style>` possuem contagem independente de `<tag\b`; no legado (`v0`), `<meta>`, `<a>`, `<img>`, `<audio>` e `<video>` não constam na lista de bloqueio de `L1310`.
- **Correção especificada**:
  1. Substituir a varredura de tags por regex (`HTML_TAG_PATTERN`, `HTML_META_TAG_PATTERN`, `HTML_SCRIPT_ELEMENT_PATTERN`, `HTML_STYLE_ELEMENT_PATTERN`) por um **scanner HTML léxico determinístico por máquina de estados** (`scanHtmlElements` / `parseHtmlTagStrict`):
     - Rejeitar com `IOException` qualquer tag `<[a-zA-Z]...` malformada, truncada, com `<` fora de aspas, com `/` fora de fechamento `/>`, ou com `'`, `"`, `` ` ``, `<`, `=` em atributo sem aspas ou antes de `=`.
     - Rejeitar tags de fechamento `</...>` malformadas ou com atributos; permitir `<!doctype ...>` estrito e rejeitar qualquer outra declaração `<!` ou `<?` (além de `<!--`, `-->`, `<![cdata[`).
     - Tratar `<script>` e `<style>` consumindo explicitamente abertura (atributos vazios), conteúdo bruto até `</script\s*>` / `</style\s*>`, validando os scripts fixos (ou bloqueando em `v0`) e `validateCaptureCss(...)`.
     - Garantir que **100%** dos elementos `<meta>` e demais tags HTML passem obrigatoriamente pela validação estrutural (`validateMetaAttributes` e `ALLOWED_HTML_ATTRIBUTES`), eliminando qualquer possibilidade de parser differential.
- **Critério de aceite**:
  - Payloads com apóstrofo/aspa isolada (`a=b'`, `a=b"`, `'`, `"`) em `<meta>`, `<iframe>`, `<a>`, `<svg>`, `<div>` são rejeitados com `IOException` em `presentationVersion == 2` e `presentationVersion == 0`.
  - Todos os testes existentes em `SecurityRegressionTest` (incluindo `title=">"`, entidades `&#39;` na CSP, tabelas/cabeçalhos `v2` e `v0` legados válidos) continuam passando integralmente.

---

### F-02 `[MÉDIA]` — Vazamento de `WebView`/recursos em `ChatCaptureLinkedWindow` ao fechar pelo `X` + corrida em `loadLinkedChat`
- **Localização atual**: `src/iped/viewers/ChatCaptureLinkedWindow.java` (`#L45-L115`) e `src/iped/viewers/HtmlLinkViewer.java` (`#L9446-L9473`).
- **Problema**:
  1. `ChatCaptureLinkedWindow` não define `setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)` (herda `HIDE_ON_CLOSE`), fazendo o botão `X` da janela ocultar sem chamar `dispose()` -> `linkedViewer.dispose()`.
  2. `loadLinkedChat` dorme `300 ms` e chama `linkedViewer.loadFile(linkedItem, null)` mesmo que a janela já tenha sido descartada nesse intervalo; `HtmlLinkViewer.loadFile` não verifica `disposed`.
- **Correção especificada**:
  1. Em `ChatCaptureLinkedWindow`, configurar `setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)`.
  2. Guardar `AtomicBoolean closed` e a thread `loadThread` (daemon), interrompê-la em `dispose()` e abortar o carregamento se `closed.get()` antes de chamar `linkedViewer.loadFile(...)`.
  3. Em `HtmlLinkViewer.loadFile(...)`, retornar imediatamente caso `disposed` seja `true`.
- **Critério de aceite**:
  - Instanciar `ChatCaptureLinkedWindow`, confirmar `getDefaultCloseOperation() == WindowConstants.DISPOSE_ON_CLOSE`, simular fechamento via `WINDOW_CLOSING` event (equivalente ao botão `X`) e validar que `linkedViewer` tem `uiScriptExecutor.isShutdown() == true` e `disposed == true`, sem chamada tardia a `attachSearcher.updateSelectionCache()`.

---

### F-03 `[BAIXA/MÉDIA]` — "Marcar todos" na paginação de `Re-Extrair` seleciona capturas inválidas
- **Localização atual**: `src/iped/viewers/HtmlLinkViewer.java` (`#L1423-L1475`, `#L1909-L1922`).
- **Problema**: Ao clicar `"Marcar todos"` (`allFuture[0] = true`), páginas seguintes renderizam checkboxes inválidos (`!job.valid`) marcados e desabilitados (`L1445`), e a materialização final (`L1456`, `L1474`) adiciona pastas inválidas em `selectedFolders`, gerando falsas falhas (`failed++`) em `runRecaptureBatch`.
- **Correção especificada**:
  1. Extrair helpers testáveis em pacote para estado da seleção paginada (`shouldCheckRecaptureBox(boolean valid, boolean allFuture, boolean inSelected)` e filtragem final de selecionadas excluindo inválidas conhecidas e revalidando páginas não visitadas quando `allFuture` estiver ativo).
  2. Nunca renderizar `!job.valid` marcado (`job.valid && (allFuture[0] || selectedIds.contains(job.folder))`), nunca inserir `knownInvalidIds` em `selectedIds`/`selectedFolders`, e quando `allFuture[0]` estiver ativo com páginas não visitadas, filtrar os lotes via `loadRecaptureJobs(..., descriptorOnly=true)` antes de `startRecaptureBatch`.
- **Critério de aceite**:
  - Com capturas válidas e inválidas distribuídas entre múltiplas páginas, clicar `"Marcar todos"` ou avançar de página mantém checkboxes inválidos desmarcados e resulta apenas em pastas válidas em `selectedFolders`.

---

### F-04 `[BAIXA/MÉDIA]` — `setlocal enabledelayedexpansion` global no `.bat` corrompe caminhos com `!`
- **Localização atual**: `instalar_captura_chat_zap/instalar_captura_chat_zap.bat` (`#L1-L50`).
- **Problema**: `setlocal enabledelayedexpansion` na linha `2` faz o `cmd.exe` interpretar `!` dentro de `%~dp0`, `%SCRIPT_DIR%`, `%IPED_ROOT%` e variáveis derivadas quando o caminho contém `!` (ex.: `Caso!2026`).
- **Correção especificada**:
  1. Trocar a linha `2` para `setlocal DisableDelayedExpansion`.
  2. Substituir o loop `for /L %%L` (`L23-L26`) por chamada de subrotina `for /L %%L in (1,1,6) do call :PROBE_IPED_STEP` (sem argumentos expansíveis na linha `call`), incluir detecção direta do layout de ZIP extraído ao lado de `iped-4.3.1` (`"%SCRIPT_DIR%..\iped-4.3.1\lib\iped-viewers-impl-4.3.1.jar"`) e trocar `!IPED_ROOT!` por `if not defined IPED_ROOT`.
- **Critério de aceite**:
  - Executar o trecho equivalente de resolução e manipulação de caminhos dentro de diretório com nome `Caso!2026!Teste\instalar_captura_chat_zap` e confirmar 100% de preservação do caractere `!` em `SCRIPT_DIR`, `IPED_ROOT`, `PLUGIN_JAR`, `TARGET_JAR` e no caminho passado via `$env:PS_HASH_PATH`.

---

### F-05 `[BAIXA]` — `openFile` sem verificação de link simbólico / arquivo regular (`CWE-59`)
- **Localização atual**: `verificador-integridade/src/IntegrityVerifier.java` (`#L645`, `#L702`).
- **Problema**: `openFile(owner, file)` chama `Desktop.getDesktop().open(file.toFile())` sem validar `isLinkOrReparse`, `Files.isRegularFile(..., LinkOption.NOFOLLOW_LINKS)` e `Desktop.Action.OPEN`, diferente de `openInBrowser` (`#L693-L701`).
- **Correção especificada**:
  1. Criar/extrair verificação determinística `validateOpenableRegularFile(Path file)` (checando `file != null`, ausência de reparse/symlink no arquivo e pai imediato via `isLinkOrReparse`, e `Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)`).
  2. Invocar em `openFile` e exigir `Desktop.getDesktop().isSupported(Desktop.Action.OPEN)`.
- **Critério de aceite**:
  - Em teste automatizado, apontar `validateOpenableRegularFile` para link simbólico/diretório/arquivo inexistente resulta em `IOException` antes de qualquer chamada `Desktop.open`, enquanto `historico-capturas.txt` regular é aceito.

---

### F-06 `[BAIXA]` — Continuação `\` em `install-localization.ps1` e código morto regex em `IntegrityVerifier.java`
- **Localização atual**:
  - `instalar_captura_chat_zap/install-localization.ps1` (`#L74-L76`)
  - `verificador-integridade/src/IntegrityVerifier.java` (`#L1621-L1622`)
- **Correção especificada**:
  1. Em `install-localization.ps1`, caso `$updated.TrimEnd("`r", "`n")` termine com quantidade ímpar de barras invertidas (`(?<!\\)(\\\\)*\\$`), neutralizar a continuação de linha antes de concatenar os novos pares chave-valor (inserindo terminador seguro antes de `$add`) para que nenhuma chave do plugin seja concatenada pelo `java.util.Properties.load()`.
  2. Remover os métodos privados sem chamadores `field(String s, String name)` e `number(String s, String n)` de `IntegrityVerifier.java`.
- **Critério de aceite**:
  - Executar `install-localization.ps1` sobre `.properties` terminado em `Key=Valor\` e carregar o resultado via `java.util.Properties.load(...)`: 100% das chaves do plugin (`ChatCapturePanel.*`, etc.) são carregadas corretamente.
  - Compilar `IntegrityVerifier.java` com JDK 8 sem referências ou chamadores a `field`/`number`.

---

## Plano de Execução em Loop
1. Criar e executar `teste_antes_de_aplicar/test_fase4_spec_validation.py` validando a spec **antes** da aplicação (comprovando falha do estado atual e aprovação dos protótipos/regras da spec).
2. Aplicar as correções nos arquivos-fonte do Verificador, do Plugin e do Instalador; atualizar testes permanentes em `verificador-integridade/tests/SecurityRegressionTest.java` e `tests/iped/viewers/`.
3. Recompilar Verificador (`JAR` JDK 8 + `EXE` via `build-single-exe.ps1 -OfflineBuild` preservando `.NET 10.0.15`), sincronizar hashes em `VerifierIntegrity.java`, recompilar `iped-viewers-impl-4.3.1.jar` mantendo as 660 entradas originais, validar `validate-plugin-jar.ps1` e atualizar instalador + documentação de checksums.
4. Executar toda a suíte (`teste_antes_de_aplicar/run_all_tests.ps1`, scripts em `tests/`, `--self-test` do `.jar`/`.exe` e validação específica da Fase 4).
5. Reauditar com a skill `encontrar-vulnerabilidade`; repetir o ciclo caso qualquer vulnerabilidade ou falha residual apareça.
