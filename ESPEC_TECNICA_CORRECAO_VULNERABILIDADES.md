# Especificação Técnica de Correção de Vulnerabilidades e Falhas (`chat-captura IPED` v2.6.8 & `verificador-integridade` v1.4.3)

## 1. Objetivo e Escopo

Este documento especifica, em nível pronto para aplicação direta em código, as correções para os achados **F-01 a F-07** identificados na auditoria de segurança e qualidade sobre:
1. **`chat-captura IPED`** (`src/iped/viewers/*.java`, `instalar_captura_chat_zap/*.ps1`)
2. **`verificador-integridade`** (`verificador-integridade/src/IntegrityVerifier.java`)

Cada correção passou por um **ciclo de verificação adversária de regressão (Loop de Validação de Correções)** para garantir que o método de mitigação:
- Não introduz novas vulnerabilidades (`TOCTOU`, perda de atomicidade/rollback, *deadlock*, vazamento de descritores ou *DoS*);
- Não quebra compatibilidade retroativa com casos e arquivos `anotacoes.json`, `manifest.json` ou `integridade.json` existentes;
- Preserva 100% dos contratos verificados pelas suítes de testes (`SecurityRegressionTest`, `CaptureIntegrityTest`, `VerifierIntegrityHashTest`, `ChatAnnotationStoreRoundTripTest`, `PathContainmentTest` e `test_marker_boundary_contract.py`).

---

## 2. Registro do Loop de Validação Adversária das Correções

Antes de consolidar as mudanças finais da Seção 3, cada proposta inicial de correção foi auditada quanto a efeitos colaterais e falhas secundárias:

| Achado | Método Inicial Analisado | Falha / Regressão Secundária Detectada na Análise | Correção Definitiva Adotada nesta Especificação | Status Pós-Loop |
| :--- | :--- | :--- | :--- | :--- |
| **F-01** (`CWE-770`, `CWE-400`) | Substituir `atomicBatch` por gravação direta com `BufferedWriter` em cada arquivo de relatório. | **Perda de atomicidade transacional (`CWE-459` / `regression-bug`)**: gravar diretamente sem estágio `.tmp` + `.bak` quebraria o rollback tudo-ou-nada exigido por `SecurityRegressionTest.batchFailurePreservesPreviousReports()` e removeria a assinatura `atomicBatch(Map<Path,String>)` usada via reflexão. | Introduzir interface funcional `ReportWriter` e `atomicBatchStreaming(Path root, Map<Path, ReportWriter> outputs)` que faz *streaming* via `BufferedWriter` para arquivos `.tmp` com backup `.bak` e promoção/rollback atômico em duas fases, mantendo `atomicBatch(Map<Path,String>)` e `atomicBatch(Path, Map<Path,String>)` como delegadores compatíveis. | **Aprovado (0 falhas)** |
| **F-02** (`CWE-367`, `CWE-59`) | Em `CaptureIntegrity.sha256(Path)`, abrir com `NOFOLLOW_LINKS` e impor limite fixo de `64 MiB`. | **Quebra funcional em anexos grandes (`regression-bug`)**: capturas reais podem conter vídeos legítimos > `64 MiB` em `attachments/`; impor limite de `64 MiB` em `sha256(Path)` faria `artifactInventory` e `compareInventory` falharem. | Validar `isLinkOrReparse(file)` e `Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)` antes da leitura e abrir o stream com `Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)` em `CaptureIntegrity.sha256(Path)`, `CaptureIntegrity.readUtf8Limited(Path, long)` e `IntegrityVerifier.readUtf8Limited(Path, long)`, mantendo `sha256(Path)` em *streaming* $O(1)$ de memória sem teto artificial para anexos regulares. Em `HtmlLinkViewer.annotateSavedChat`, resolver `manifest.json` via `CaptureIntegrity.resolveContained` + `CaptureIntegrity.isSafeRegularFile`. | **Aprovado (0 falhas)** |
| **F-03** (`CWE-59`) | Trocar `Files.isSymbolicLink(current)` por `isLinkOrReparse(current)` em todos os diretórios ancestrais (`fsRoot.relativize(normalizedRoot)`). | **Incompatibilidade em ambientes Windows corporativos (`compatibility-bug`)**: pastas ancestrais do perfil do usuário (ex.: OneDrive Known Folder Move `IO_REPARSE_TAG_CLOUD`, pontos de montagem de volume ou volumes com deduplicação NTFS) possuem `BasicFileAttributes.isOther() == true`. Bloquear `isOther()` acima de `normalizedRoot` impediria o uso do IPED nesses volumes. | Manter `Files.isSymbolicLink(current)` nos ancestrais acima de `normalizedRoot`, adicionar a verificação `if (isLinkOrReparse(normalizedRoot))` que faltava em `IntegrityVerifier.rejectLinks(Path, Path)` (sincronizando-o com `CaptureIntegrity.ensureNoSymbolicLinkComponents`), e exigir `isLinkOrReparse` + `toRealPath().startsWith(realRoot)` em `normalizedRoot` e em toda a subárvore abaixo dele. | **Aprovado (0 falhas)** |
| **F-04** (`CWE-833`) | Substituir chamadas `executeScriptAndWait` e `SwingUtilities.invokeAndWait` indiscriminadamente por chamadas assíncronas. | **Condição de corrida na validação de início de captura (`CWE-362`)**: `validateCaptureStart` precisa retornar `boolean` de forma síncrona na thread trabalhadora `IPED-chat-capture-start`. | 1. Em `AttachmentHandler.showAttachNotFound`, trocar `SwingUtilities.invokeAndWait` por `SwingUtilities.invokeLater` (eliminando espera bloqueante JavaFX -> EDT).<br>2. Em `HtmlLinkViewer.annotationAction`, obter `getMessagePreviewText(id)` fora da *Swing EDT* (`runOffEdtIfNeeded`) antes de despachar `SwingUtilities.invokeLater(() -> showAnnotationActions(id, preview))`, eliminando a chamada de `executeScriptAndWait` na *Swing EDT*. | **Aprovado (0 falhas)** |
| **F-05** (`CWE-400`, `CWE-362`) | Executar `searchChat` diretamente no `uiScriptExecutor` sem controle de geração. | **Enfileiramento de buscas obsoletas (`usability-ux-bug`)**: se o usuário digitar várias vezes, tarefas antigas enfileiradas ainda executariam chamadas DOM desnecessárias. | 1. Adicionar `AtomicInteger searchGeneration` em `HtmlLinkViewer` e despachar `searchChat` no `uiScriptExecutor`, abortando antes e depois do `executeScriptAndWait` se `gen != searchGeneration.get()`.<br>2. Em `refreshAnnotationsUiNow` e `buildAnnotationPreflight`, substituir as $2A+2$ chamadas individuais de `getMessageIndex` por uma única consulta em lote `getOrderedMessageIndexMap()` ($O(M + A)$).<br>3. Em `SavedChatPickerDialog.scanSafeCaptureEntries`, impor limite seguro `MAX_PICKER_CAPTURES = 10000` entradas. | **Aprovado (0 falhas)** |
| **F-06** (`CWE-328`, `CWE-400`) | Trocar `sha1Hex` por `SHA-256` e aumentar `chatRefIdOf` de 8 para 16 hex chars em `ChatAnnotationStore`. | **Quebra de leitura de `anotacoes.json` existentes (`regression-bug`)**: `chatKey` (40 hex chars) e `chatRefId` (`ch-` + 8 hex chars) já estão gravados em arquivos `anotacoes.json` de casos existentes e compõem `LinkedChatRef.key()`. Alterar o formato invalidaria vínculos e notas salvas. | 1. Em `ChatAnnotationStore.load()`, trocar `CaptureIntegrity.readUtf8Limited(storeFile, MAX_STORE_BYTES)` por `CaptureIntegrity.readJsonUtf8Limited(storeFile, MAX_STORE_BYTES)` para impor limite de profundidade JSON (64 níveis) antes do parser manual.<br>2. Preservar o formato determinístico de `chatKeyOf` e `chatRefIdOf` para manter 100% de compatibilidade com `anotacoes.json` existentes. | **Aprovado (0 falhas)** |
| **F-07** (`CWE-561`) | Tornar `$Jar` opcional em `validate-plugin-jar.ps1` e remover constantes não usadas em `IntegrityVerifier.java`. | **Risco de quebra de reflexão se algum teste lesse as constantes removidas**: verificado em toda a árvore `tests/` e `verificador-integridade/tests/SecurityRegressionTest.java` que nenhuma classe consulta `MAX_SELECTED_METADATA_BYTES` ou `MAX_REPORT_ESTIMATED_CHARS`. | 1. Em `validate-plugin-jar.ps1`, definir default `$Jar = (Join-Path $PSScriptRoot 'iped-viewers-impl-4.3.1.jar')` e validar `Test-Path -LiteralPath $Jar`.<br>2. Em `IntegrityVerifier.java`, remover apenas as duas constantes mortas (`MAX_SELECTED_METADATA_BYTES` e `MAX_REPORT_ESTIMATED_CHARS`), preservando `enforceReportBudget(List<Capture>)` (usado por reflexão em `SecurityRegressionTest.java:144`). | **Aprovado (0 falhas)** |

---

## 3. Especificação Detalhada das Alterações por Arquivo

### 3.1. [IntegrityVerifier.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/verificador-integridade/src/IntegrityVerifier.java) (Achados F-01, F-02, F-03, F-07)

#### 3.1.1. Remoção de constantes órfãs (F-07)
Em [IntegrityVerifier.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/verificador-integridade/src/IntegrityVerifier.java#L83-L85), remover `MAX_SELECTED_METADATA_BYTES` e `MAX_REPORT_ESTIMATED_CHARS`, mantendo `MAX_SELECTED_ITEMS`:

```java
    private static final int MAX_SELECTED_ITEMS = 25000;
```

#### 3.1.2. Validação anti-link antes de `Files.size(p)` em `readUtf8Limited` e `hash` (F-02)
Em [IntegrityVerifier.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/verificador-integridade/src/IntegrityVerifier.java#L1561) e [L1618](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/verificador-integridade/src/IntegrityVerifier.java#L1618):
- Garantir que `hash(Path p)` e `readUtf8Limited(Path p, long maxBytes)` rejeitem links simbólicos, *junctions* ou arquivos não-regulares com `LinkOption.NOFOLLOW_LINKS` antes de consultar `Files.size(p)` ou abrir `InputStream`, e usar `while ((n = in.read(b)) != -1)` em `hash(Path p)`:

```java
    private static String hash(Path p) throws Exception {
        if (p == null || isLinkOrReparse(p) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Arquivo inválido ou link simbólico para SHA-256: " + p);
        }
        MessageDigest d = MessageDigest.getInstance(ALG);
        try (InputStream in = Files.newInputStream(p, LinkOption.NOFOLLOW_LINKS)) {
            byte[] b = new byte[32768];
            int n;
            while ((n = in.read(b)) != -1) {
                if (n > 0) d.update(b, 0, n);
            }
        }
        return hex(d.digest());
    }
```

E em `readUtf8Limited(Path p, long maxBytes)`:

```java
    private static String readUtf8Limited(Path p, long maxBytes) throws IOException {
        if (maxBytes <= 0 || maxBytes > MAX_HTML_CAPTURE_BYTES) {
            throw new IllegalArgumentException("Limite de leitura inválido: " + maxBytes);
        }
        if (p == null || isLinkOrReparse(p) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Arquivo de metadados inválido ou link simbólico: " + p);
        }
        long size = Files.size(p);
        if (size > maxBytes) {
            throw new IOException("Arquivo de metadados excede o limite de " + maxBytes + " bytes: " + p);
        }
        try (InputStream in = Files.newInputStream(p, LinkOption.NOFOLLOW_LINKS);
                ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(size, maxBytes))) {
            byte[] buffer = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buffer)) != -1) {
                if (n == 0) continue;
                total += n;
                if (total > maxBytes) {
                    throw new IOException("Arquivo de metadados excede o limite de " + maxBytes + " bytes: " + p);
                }
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
```

#### 3.1.3. Gravação Transacional em *Streaming* para `writeReports` e `atomicBatch` (F-01)
Em [IntegrityVerifier.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/verificador-integridade/src/IntegrityVerifier.java#L1702-L1768), substituir a acumulação simultânea de `html`, `txt` e `json` em `StringBuilder`/`byte[]` por escrita em *streaming* direto para os arquivos temporários do lote transacional via `BufferedWriter`:

```java
    @FunctionalInterface
    private interface ReportWriter {
        void writeTo(java.io.BufferedWriter out) throws IOException;
    }

    private static void writeReports(Path root, List<Capture> cs, String rootHash, String time,
            String history, String publicHistory, String historyFullHash, String historyPublicHash) throws Exception {
        enforceReportBudget(cs);
        StringBuilder reportScript = new StringBuilder();
        reportScript.append("var HISTORY_PUBLIC_HASH='").append(historyPublicHash).append("',HISTORY_FULL_HASH='").append(historyFullHash).append("';");
        reportScript.append("function show(el,text,ok){el.className=ok?'match':'diverge';el.textContent=text;}");
        reportScript.append("function showCmp(el,text,ok){el.className='cmp '+(ok?'match':'diverge');el.textContent=text;}");
        reportScript.append("function sha256Utf8(str){var s=unescape(encodeURIComponent(str)),K=[1116352408,1899447441,3049323471,3921009573,961987163,1508970993,2453635748,2870763221,3624381080,310598401,607225278,1426881987,1925078388,2162078206,2614888103,3248222580,3835390401,4022224774,264347078,604807628,770255983,1249150122,1555081692,1996064986,2554220882,2821834349,2952996808,3210313671,3336571891,3584528711,113926993,338241895,666307205,773529912,1294757372,1396182291,1695183700,1986661051,2177026350,2456956037,2730485921,2820302411,3259730800,3345764771,3516065817,3600352804,4094571909,275423344,430227734,506948616,659060556,883997877,958139571,1322822218,1537002063,1747873779,1955562222,2024104815,2227730452,2361852424,2428436474,2756734187,3204031479,3329325298],H=[1779033703,3144134277,1013904242,2773480762,1359893119,2600822924,528734635,1541459225],w=[],l=s.length,i,j,a,b,c,d,e,f,g,h,t1,t2,S0,S1,ch,maj,s0,s1,r=function(x,n){return(x>>>n)|(x<<(32-n));};for(i=0;i<l;i++)w[i>>2]|=(s.charCodeAt(i)&255)<<(24-(i&3)*8);w[l>>2]|=0x80<<(24-(l&3)*8);var bl=(((l+8)>>6)+1)*16;for(i=0;i<bl;i++)if(!w[i])w[i]=0;w[bl-1]=(l*8)&0xffffffff;w[bl-2]=Math.floor(l/536870912);for(i=0;i<bl;i+=16){var M=w.slice(i,i+16);for(j=16;j<64;j++){s0=r(M[j-15],7)^r(M[j-15],18)^(M[j-15]>>>3);s1=r(M[j-2],17)^r(M[j-2],19)^(M[j-2]>>>10);M[j]=(M[j-16]+s0+M[j-7]+s1)|0;}a=H[0];b=H[1];c=H[2];d=H[3];e=H[4];f=H[5];g=H[6];h=H[7];for(j=0;j<64;j++){S1=r(e,6)^r(e,11)^r(e,25);ch=(e&f)^((~e)&g);t1=(h+S1+ch+K[j]+M[j])|0;S0=r(a,2)^r(a,13)^r(a,22);maj=(a&b)^(a&c)^(b&c);t2=(S0+maj)|0;h=g;g=f;f=e;e=(d+t1)|0;d=c;c=b;b=a;a=(t1+t2)|0;}H[0]=(H[0]+a)|0;H[1]=(H[1]+b)|0;H[2]=(H[2]+c)|0;H[3]=(H[3]+d)|0;H[4]=(H[4]+e)|0;H[5]=(H[5]+f)|0;H[6]=(H[6]+g)|0;H[7]=(H[7]+h)|0;}var out='';for(i=0;i<8;i++)out+=('00000000'+(H[i]>>>0).toString(16)).slice(-8);return out;}");
        reportScript.append("function compareHash(){var v=document.getElementById('publicHash').value.trim().toLowerCase(),r=document.getElementById('comparison');if(!v){r.className='';r.textContent='Informe um hash geral.';return;}if(v===HISTORY_PUBLIC_HASH){show(r,'CONFERE com o Hash geral Público calculado.',true);return;}if(v===HISTORY_FULL_HASH){show(r,'CONFERE com o SHA-256 do histórico completo calculado.',true);return;}show(r,'DIVERGE do Hash geral Público e do histórico completo calculados.',false);}");
        reportScript.append("function extractCaptureBlocks(text){var norm=text.replace(/\\r\\n/g,'\\n').replace(/\\r/g,'\\n'),lines=norm.split('\\n'),blocks=[],cur=null,i,line;for(i=0;i<lines.length;i++){line=lines[i];if(line.indexOf('CAPTURA|')===0){if(cur){while(cur.length&&cur[cur.length-1]==='')cur.pop();blocks.push(cur.join('\\n'));}cur=[line];}else if(line.indexOf('Nota|')===0){if(cur){while(cur.length&&cur[cur.length-1]==='')cur.pop();blocks.push(cur.join('\\n'));}cur=null;break;}else if(cur){cur.push(line);}}if(cur){while(cur.length&&cur[cur.length-1]==='')cur.pop();blocks.push(cur.join('\\n'));}return blocks;}");
        reportScript.append("function compareHistory(){var raw=document.getElementById('historyInput').value,rr=document.getElementById('historyResult');if(!raw||!raw.trim()){rr.className='';rr.textContent='Cole o conteúdo de historico-capturas.txt (completo ou público).';document.querySelectorAll('.capture-row .cmp').forEach(function(el){el.className='cmp';el.textContent='Não comparado';});return;}var blocks=extractCaptureBlocks(raw),matched={};blocks.forEach(function(b){matched[sha256Utf8(b)]=true;});var okCount=0,failCount=0;document.querySelectorAll('.capture-row').forEach(function(row){var bf=row.getAttribute('data-block-full'),bp=row.getAttribute('data-block-public'),out=row.querySelector('.cmp'),ok=!!(matched[bf]||matched[bp]);if(ok)okCount++;else failCount++;showCmp(out,ok?'OK':'FALHA',ok);});show(rr,failCount===0?'Histórico confere: '+okCount+'/'+(okCount+failCount)+' capturas OK.':'Divergência ou captura ausente: '+okCount+' OK, '+failCount+' FALHA.',failCount===0);}");
        reportScript.append("function copyValue(b){var t=document.createElement('textarea');t.value=b.getAttribute('data-value');document.body.appendChild(t);t.select();document.execCommand('copy');t.remove();}");
        reportScript.append("var btnCmp=document.getElementById('btnCompareHash');if(btnCmp&&btnCmp.addEventListener)btnCmp.addEventListener('click',compareHash);");
        reportScript.append("var btnHist=document.getElementById('btnCompareHistory');if(btnHist&&btnHist.addEventListener)btnHist.addEventListener('click',compareHistory);");
        reportScript.append("var cpBtns=document.querySelectorAll('.copy-btn');for(var ci=0;ci<cpBtns.length;ci++){if(cpBtns[ci]&&cpBtns[ci].addEventListener){(function(b){b.addEventListener('click',function(){copyValue(b);});})(cpBtns[ci]);}}");
        final String scriptStr = reportScript.toString();
        final String reportCsp = "default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-" + sha256Base64(scriptStr) + "'; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'";
        Path reportDir = root.resolve("integridade");
        createDirectoriesSafe(root, reportDir);
        Map<Path, ReportWriter> outputs = new LinkedHashMap<>();
        outputs.put(root.resolve("integridade.html"), html -> {
            html.write("<!doctype html><html lang=\"pt-BR\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta http-equiv=\"Content-Security-Policy\" content=\"");
            html.write(reportCsp);
            html.write("\"><title>Integridade das capturas</title><style>body{font:16px Arial;margin:2em;color:#20313a}table{border-collapse:collapse;width:100%;margin-bottom:1em}td,th{border:1px solid #bbb;padding:.5em;text-align:left;overflow-wrap:anywhere}.ok{color:green}.bad,.diverge{color:#8b0000;font-weight:700}.match{color:#006400;font-weight:700}input{padding:.5em;width:min(40em,90%)}code{overflow-wrap:anywhere}</style><h1>Relatório de integridade</h1><p>Verificação e relatório gerados em: ");
            html.write(esc(time));
            html.write("</p><p>Hash geral Público: <code id=\"publicSummaryHash\">");
            html.write(historyPublicHash);
            html.write("</code> <button class=\"copy-btn\" data-value=\"");
            html.write(historyPublicHash);
            html.write("\">Copiar hash público</button></p><label>Hash geral Público (ou SHA-256 do histórico completo): <input id=\"publicHash\" placeholder=\"Cole aqui o SHA-256 recebido\"></label> <button id=\"btnCompareHash\">Comparar hash geral</button><p id=\"comparison\">Nenhum hash geral informado.</p>");
            html.write("<label for=\"historyInput\">Cole o histórico de capturas completo ou público (historico-capturas.txt):</label><br><textarea id=\"historyInput\" rows=\"7\" style=\"width:min(70em,95%)\" placeholder=\"HISTORICO_PUBLICO_V1&#10;SHA256|...&#10;&#10;CAPTURA|... (ou CAPTURA|*-YYYYMMDD-HHMMSS)\"></textarea><br><button id=\"btnCompareHistory\">Comparar histórico em todas as capturas</button> <span id=\"historyResult\"></span><table><tr><th>Captura</th><th>Hash calculado</th><th>Arquivos</th><th>Comparação com histórico</th></tr>");
            for (Capture c : cs) {
                long good = c.items.stream().filter(i -> isHealthyVerifierItemState(i.state)).count();
                String blockFullSha;
                String blockPublicSha;
                try {
                    blockFullSha = hashText(captureHistoryBlock(c, false));
                    blockPublicSha = hashText(captureHistoryBlock(c, true));
                } catch (Exception e) {
                    throw new IOException("Falha ao calcular hash de bloco do histórico.", e);
                }
                html.write("<tr class=\"capture-row\" data-capture=\"");
                html.write(esc(c.name));
                html.write("\" data-block-full=\"");
                html.write(blockFullSha);
                html.write("\" data-block-public=\"");
                html.write(blockPublicSha);
                html.write("\"><td>");
                html.write(esc(c.name));
                html.write("</td><td><code>");
                html.write(c.hash);
                html.write("</code> <button class=\"copy-btn\" data-value=\"");
                html.write(c.hash);
                html.write("\">Copiar hash</button></td><td>");
                html.write(Long.toString(good));
                html.write(" íntegros / ");
                html.write(Integer.toString(c.items.size()));
                html.write(" verificados<br>Histórico: ");
                html.write(esc(c.historyStatus));
                if (!c.historyHash.isEmpty()) {
                    html.write("<br>Hash do registro: <code>");
                    html.write(esc(c.historyHash));
                    html.write("</code> <button class=\"copy-btn\" data-value=\"");
                    html.write(esc(c.historyHash));
                    html.write("\">Copiar</button>");
                }
                html.write("</td><td><span class=\"cmp\">Não comparado</span></td></tr>");
            }
            html.write("</table><h2>Arquivos</h2>");
            for (Capture c : cs) {
                html.write("<h3>");
                html.write(esc(c.name));
                html.write("</h3><table><tr><th>Arquivo</th><th>Estado</th><th>Esperado</th><th>Encontrado</th></tr>");
                for (Item i : c.items) {
                    html.write("<tr><td>");
                    html.write(esc(i.path));
                    html.write("</td><td class=\"");
                    html.write(isHealthyVerifierItemState(i.state) ? "ok" : "bad");
                    html.write("\">");
                    html.write(esc(i.state));
                    html.write("</td><td><code>");
                    html.write(esc(i.expected));
                    html.write("</code></td><td><code>");
                    html.write(esc(i.actual));
                    html.write("</code></td></tr>");
                }
                html.write("</table>");
            }
            html.write("<p>Este relatório é um retrato desta execução. O hash é um código de comparação, não uma assinatura digital.</p><p>Arquivos: <a href=\"integridade/hashes-capturas.txt\">lista de hashes</a> · <a href=\"integridade/verificacao-integridade.txt\">resumo TXT</a> · <a href=\"integridade/verificacao-integridade.json\">detalhes JSON</a></p><script>");
            html.write(scriptStr);
            html.write("</script></html>");
        });
        outputs.put(reportDir.resolve("verificacao-integridade.txt"), txt -> {
            txt.write("Verificado: ");
            txt.write(time);
            txt.write("\nTODAS_AS_CAPTURAS|");
            txt.write(rootHash);
            txt.write("\n");
            for (Capture c : cs) {
                txt.write(recordField(c.name));
                txt.write(" | ");
                txt.write(c.hash);
                txt.write("\n");
            }
        });
        outputs.put(reportDir.resolve("verificacao-integridade.json"), json -> {
            json.write("{\"generatedAt\":\"");
            json.write(jsonEsc(time));
            json.write("\",\"rootHash\":\"");
            json.write(rootHash);
            json.write("\",\"historyPublicHash\":\"");
            json.write(historyPublicHash);
            json.write("\",\"captures\":[");
            boolean first = true;
            for (Capture c : cs) {
                if (!first) json.write(',');
                first = false;
                json.write("{\"name\":\"");
                json.write(jsonEsc(c.name));
                json.write("\",\"hash\":\"");
                json.write(c.hash);
                json.write("\",\"files\":[");
                boolean f = true;
                for (Item i : c.items) {
                    if (!f) json.write(',');
                    f = false;
                    json.write("{\"path\":\"");
                    json.write(jsonEsc(i.path));
                    json.write("\",\"status\":\"");
                    json.write(jsonEsc(i.state));
                    json.write("\",\"expected\":\"");
                    json.write(jsonEsc(i.expected == null ? "" : i.expected));
                    json.write("\",\"actual\":\"");
                    json.write(jsonEsc(i.actual == null ? "" : i.actual));
                    json.write("\"}");
                }
                json.write("]}");
            }
            json.write("]}");
        });
        outputs.put(reportDir.resolve("hashes-capturas.txt"), w -> w.write(publicHashList(cs, rootHash)));
        outputs.put(reportDir.resolve("historico-capturas.txt"), w -> w.write(history));
        atomicBatchStreaming(root, outputs);
    }

    private static void atomicBatch(Path root, Map<Path, String> outputs) throws IOException {
        if (outputs == null || outputs.isEmpty()) return;
        Map<Path, ReportWriter> writers = new LinkedHashMap<>();
        for (Map.Entry<Path, String> entry : outputs.entrySet()) {
            final String content = entry.getValue();
            writers.put(entry.getKey(), w -> w.write(content));
        }
        atomicBatchStreaming(root, writers);
    }

    private static void atomicBatchStreaming(Path root, Map<Path, ReportWriter> outputs) throws IOException {
        if (outputs == null || outputs.isEmpty()) return;
        Map<Path, Path> staged = new LinkedHashMap<>(), backups = new LinkedHashMap<>();
        List<Path> promoted = new ArrayList<>();
        try {
            for (Map.Entry<Path, ReportWriter> entry : outputs.entrySet()) {
                Path target = entry.getKey().toAbsolutePath().normalize(), parent = target.getParent();
                if (parent == null) throw new IOException("Caminho de relatório sem diretório pai: " + target);
                createDirectoriesSafe(root, parent);
                if (isLinkOrReparse(target)) throw new IOException("O destino do relatório é link ou junção: " + target);
                Path temp = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
                staged.put(target, temp);
                try (OutputStream raw = Files.newOutputStream(temp, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                        java.io.BufferedWriter writer = new java.io.BufferedWriter(
                                new java.io.OutputStreamWriter(raw, StandardCharsets.UTF_8), 32768)) {
                    entry.getValue().writeTo(writer);
                }
                if (isLinkOrReparse(target)) {
                    throw new IOException("O destino do relatório mudou para link ou junção: " + target);
                }
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("O destino do relatório não é arquivo regular: " + target);
                    }
                    Path backup = Files.createTempFile(parent, "." + target.getFileName() + ".", ".bak");
                    Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                    backups.put(target, backup);
                }
            }
            for (Map.Entry<Path, Path> entry : staged.entrySet()) {
                if (isLinkOrReparse(entry.getKey()) || isLinkOrReparse(entry.getValue())
                        || (Files.exists(entry.getKey(), LinkOption.NOFOLLOW_LINKS)
                                && !Files.isRegularFile(entry.getKey(), LinkOption.NOFOLLOW_LINKS))) {
                    throw new IOException("O destino do relatório mudou para link, junção ou diretório inválido: " + entry.getKey());
                }
                moveReplace(entry.getValue(), entry.getKey());
                promoted.add(entry.getKey());
            }
        } catch (IOException failure) {
            for (int i = promoted.size() - 1; i >= 0; i--) {
                Path target = promoted.get(i), backup = backups.get(target);
                try {
                    if (backup != null) {
                        moveReplace(backup, target);
                        backups.remove(target);
                    } else {
                        Files.deleteIfExists(target);
                    }
                } catch (IOException rollback) {
                    failure.addSuppressed(rollback);
                }
            }
            throw failure;
        } finally {
            for (Path temp : staged.values()) Files.deleteIfExists(temp);
            for (Path backup : backups.values()) Files.deleteIfExists(backup);
        }
    }
```

#### 3.1.4. Verificação de `isLinkOrReparse(normalizedRoot)` em `IntegrityVerifier.rejectLinks` (F-03)
Em [IntegrityVerifier.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/verificador-integridade/src/IntegrityVerifier.java#L1787-L1809), adicionar a validação de `normalizedRoot` com `isLinkOrReparse(normalizedRoot)` logo após o loop de ancestrais, alinhando-o com `CaptureIntegrity.ensureNoSymbolicLinkComponents`:

```java
        if (isLinkOrReparse(normalizedRoot)) {
            throw new IOException("Raiz de saída é link ou junção: " + normalizedRoot);
        }
```

---

### 3.2. [CaptureIntegrity.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/CaptureIntegrity.java) (Achado F-02)

Em [CaptureIntegrity.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/CaptureIntegrity.java#L41-L57) (`sha256(Path file)`) e [L206-L222](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/CaptureIntegrity.java#L206-L222) (`readUtf8Limited(Path path, long maxBytes)`):

1. Em `sha256(Path file)`:
   - Rejeitar `null`, links simbólicos/reparse points (`isLinkOrReparse(file)`) e alvos que não sejam arquivos regulares (`!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)`).
   - Abrir o arquivo com `Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)`.

```java
    static String sha256(Path file) throws IOException {
        if (file == null || isLinkOrReparse(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid or non-regular file for SHA-256: " + file);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            try (InputStream input = new BufferedInputStream(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS))) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        digest.update(buffer, 0, count);
                    }
                }
            }
            return toHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }
```

2. Em `readUtf8Limited(Path path, long maxBytes)`:
   - Verificar `isLinkOrReparse(path)` e `Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)` **antes** de chamar `Files.size(path)`:

```java
    static String readUtf8Limited(Path path, long maxBytes) throws IOException {
        if (maxBytes <= 0 || maxBytes > MAX_HTML_CAPTURE_BYTES) {
            throw new IllegalArgumentException("Invalid UTF-8 read limit: " + maxBytes);
        }
        if (path == null || isLinkOrReparse(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid or non-regular metadata file: " + path);
        }
        long size = Files.size(path);
        if (size > maxBytes) {
            throw new IOException("JSON metadata exceeds the limit of " + maxBytes + " bytes: " + path);
        }
        int limit = (int) maxBytes;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit || input.read() != -1) {
                throw new IOException("JSON metadata exceeds the limit of " + maxBytes + " bytes: " + path);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
```

---

### 3.3. [HtmlLinkViewer.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/HtmlLinkViewer.java) (Achados F-02, F-04, F-05)

#### 3.3.1. Resolução em lote dos índices de mensagens do DOM (F-05)
Em [HtmlLinkViewer.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/HtmlLinkViewer.java#L706-L745) (`refreshAnnotationsUiNow` / `classifyAnnotationRange`) e [L888-L900](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/HtmlLinkViewer.java#L888-L900) (`buildAnnotationPreflight`), adicionar o método auxiliar `getOrderedMessageIndexMap()` que obtém todos os IDs ordenados em **uma única chamada** `executeScriptAndWait`:

```java
    private Map<String, Integer> getOrderedMessageIndexMap() {
        Map<String, Integer> indexById = new HashMap<>();
        try {
            Object raw = executeScriptAndWait(
                    "(function(){if(!window.ipedChatCapture)return '[]';var m=window.ipedChatCapture.getOrderedMessages(),out=[];for(var i=0;i<m.length;i++){out.push(m[i]&&m[i].id?m[i].id:'');}return JSON.stringify(out);})()"); //$NON-NLS-1$
            if (raw instanceof String) {
                JSONArray ids = new JSONArray((String) raw);
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.optString(i, ""); //$NON-NLS-1$
                    if (!id.isEmpty() && !indexById.containsKey(id)) {
                        indexById.put(id, i);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to build ordered message index map", e); //$NON-NLS-1$
        }
        return indexById;
    }

    private int classifyAnnotationRange(String messageId, int startIndex, int endIndex) {
        if (captureStartMessageId == null || captureEndMessageId == null) {
            return ChatAnnotation.RANGE_IN;
        }
        return classifyAnnotationRange(getMessageIndex(messageId), startIndex, endIndex);
    }

    private int classifyAnnotationRange(int index, int startIndex, int endIndex) {
        if (captureStartMessageId == null || captureEndMessageId == null) {
            return ChatAnnotation.RANGE_IN;
        }
        if (index < 0) {
            return ChatAnnotation.RANGE_ORPHAN;
        }
        if (startIndex >= 0 && endIndex >= 0 && index >= startIndex && index <= endIndex) {
            return ChatAnnotation.RANGE_IN;
        }
        return ChatAnnotation.RANGE_OUT;
    }
```

E atualizar `refreshAnnotationsUiNow()` e `buildAnnotationPreflight()` para consultar `indexById.getOrDefault(id, -1)` em $O(1)$ por anotação, eliminando o loop de $2A + 2$ chamadas `executeScriptAndWait`.

#### 3.3.2. Eliminação de chamada `executeScriptAndWait` na *Swing EDT* em `annotationAction` (F-04)
Em [HtmlLinkViewer.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/HtmlLinkViewer.java#L949-L980), obter o texto de pré-visualização fora da *Swing EDT* (da mesma forma que `openNoteDialogAsync` faz na linha 614) antes de abrir o `ChatAnnotationActionDialog` na *Swing EDT*:

```java
    public void annotationAction(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        String id = messageId;
        runOffEdtIfNeeded(() -> {
            String preview = getMessagePreviewText(id);
            SwingUtilities.invokeLater(() -> showAnnotationActions(id, preview));
        });
    }

    private void showAnnotationActions(String messageId, String preview) {
        if (captureRunning || linkedCaptureViewer) {
            return;
        }
        String chatKey = currentChatKey();
        ChatAnnotation annotation = annotationStore.get(chatKey, messageId);
        if (annotation == null || annotation.isEmpty()) {
            return;
        }
        ChatAnnotationActionDialog dialog = new ChatAnnotationActionDialog(
                SwingUtilities.getWindowAncestor(chatCapturePanel), annotation, preview);
        dialog.setVisible(true);
        boolean changed = false;
        if (dialog.isNoteRemoved()) {
            annotationStore.removeNote(chatKey, messageId);
            changed = true;
        }
        if (dialog.isLinksRemoved()) {
            annotationStore.removeAllLinks(chatKey, messageId);
            changed = true;
        }
        if (changed) {
            refreshAnnotationsUi();
        }
    }
```

#### 3.3.3. Validação de contenção anti-link em `annotateSavedChat` (F-02)
Em [HtmlLinkViewer.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/HtmlLinkViewer.java#L1036-L1045), substituir `new java.io.File(outputRoot, folderName)` + `manifest.isFile()` por resolução contida e verificação de arquivo regular sem seguir links simbólicos ou *junctions*:

```java
        String title = "";
        String sourceName = "";
        String sourceHash = "";
        String sourcePath = "";
        try {
            Path rootPath = outputRoot.toPath().toAbsolutePath().normalize();
            Path savedFolderPath = CaptureIntegrity.resolveContained(rootPath, folderName);
            Path manifestPath = CaptureIntegrity.resolveContained(savedFolderPath, "manifest.json"); //$NON-NLS-1$
            if (CaptureIntegrity.isSafeDirectory(savedFolderPath) && CaptureIntegrity.isSafeRegularFile(manifestPath)) {
                String content = CaptureIntegrity.readJsonUtf8Limited(manifestPath);
                JSONObject root = new JSONObject(content);
                // ...
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to read manifest of saved chat {}", folderName, e);
        }
```

#### 3.3.4. Serialização de `searchChat` no `uiScriptExecutor` com contador de geração (F-05)
Em [HtmlLinkViewer.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/HtmlLinkViewer.java#L202-L206) e [L7936-L7954](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/HtmlLinkViewer.java#L7936-L7954):
- Declarar `private final java.util.concurrent.atomic.AtomicInteger searchGeneration = new java.util.concurrent.atomic.AtomicInteger();`.
- Em `clearChatSearchResults()`, incrementar `searchGeneration.incrementAndGet()`.
- Em `searchChat(String term)`, incrementar `final int gen = searchGeneration.incrementAndGet();` e submeter a tarefa ao `uiScriptExecutor` em vez de criar threads ilimitadas (`new Thread(...)`), descartando resultados cujo `gen != searchGeneration.get()`:

```java
    void searchChat(String term) {
        final String query = term == null ? "" : term.trim(); //$NON-NLS-1$
        if (query.isEmpty()) {
            clearChatSearchResults();
            return;
        }
        final int gen;
        synchronized (searchLock) {
            gen = searchGeneration.incrementAndGet();
        }
        uiScriptExecutor.execute(() -> {
            if (gen != searchGeneration.get()) {
                return;
            }
            Object raw = executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.searchMessages(" //$NON-NLS-1$
                    + js(query) + "," + SEARCH_RESULT_LIMIT + ");}else{'';}"); //$NON-NLS-1$ //$NON-NLS-2$
            ChatSearchResult result = parseSearchResult(raw == null ? "" : raw.toString()); //$NON-NLS-1$
            List<ChatSearchHit> hitsCopy;
            synchronized (searchLock) {
                if (gen != searchGeneration.get()) {
                    return;
                }
                searchHits.clear();
                searchHits.addAll(result.hits);
                searchTotal = result.total;
                searchIndex = -1;
                hitsCopy = new ArrayList<>(result.hits);
            }
            chatCapturePanel.setSearchResults(query, result.total, hitsCopy);
        });
    }

    private void clearChatSearchResults() {
        synchronized (searchLock) {
            searchGeneration.incrementAndGet();
            searchHits.clear();
            searchTotal = 0;
            searchIndex = -1;
        }
        chatCapturePanel.setSearchResults("", 0, new ArrayList<>()); //$NON-NLS-1$ //$NON-NLS-2$
    }
```

#### 3.3.5. Troca de `SwingUtilities.invokeAndWait` por `SwingUtilities.invokeLater` em `showAttachNotFound` (F-04)
Em [HtmlLinkViewer.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/HtmlLinkViewer.java#L9318-L9330):

```java
        private void showAttachNotFound(final String luceneQuery) {
            LOGGER.info("Attachment not found by query " + luceneQuery); //$NON-NLS-1$
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(null, Messages.getString("HtmlLinkViewer.AttachNotFound")); //$NON-NLS-1$
                }
            });
        }
```

---

### 3.4. [SavedChatPickerDialog.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/SavedChatPickerDialog.java) (Achado F-05)

Em [SavedChatPickerDialog.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/SavedChatPickerDialog.java#L95-L150), limitar a varredura de diretórios em `scanSafeCaptureEntries` com `MAX_PICKER_CAPTURES = 10000` (`stream.filter(CaptureIntegrity::isSafeDirectory).limit(MAX_PICKER_CAPTURES)`), evitando bloqueio prolongado da interface ao abrir diretórios com dezenas de milhares de subpastas.

---

### 3.5. [ChatAnnotationStore.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/ChatAnnotationStore.java) (Achado F-06)

Em [ChatAnnotationStore.java](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/src/iped/viewers/ChatAnnotationStore.java#L125-L131), validar a estrutura e a profundidade máxima de aninhamento (64 níveis) ao ler `anotacoes.json` trocando `readUtf8Limited` por `readJsonUtf8Limited`:

```java
        String content;
        try {
            content = CaptureIntegrity.readJsonUtf8Limited(storeFile, MAX_STORE_BYTES);
        } catch (IOException e) {
            LOGGER.warn("Unable to read {}, keeping annotations in memory only", storeFile, e); //$NON-NLS-1$
            return;
        }
```

---

### 3.6. [validate-plugin-jar.ps1](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/instalar_captura_chat_zap/validate-plugin-jar.ps1) (Achado F-07)

Em [validate-plugin-jar.ps1](file:///c:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura%20IPED/instalar_captura_chat_zap/validate-plugin-jar.ps1#L1-L5), substituir `[Parameter(Mandatory=$true)][string]$Jar` por um caminho padrão seguro relativo a `$PSScriptRoot`, falhando imediatamente com mensagem clara se o arquivo não existir (sem bloquear `stdin` em execuções automatizadas):

```powershell
param([string]$Jar=(Join-Path $PSScriptRoot 'iped-viewers-impl-4.3.1.jar'),[string]$ExpectedSha256='')
$ErrorActionPreference='Stop'
if([string]::IsNullOrWhiteSpace($Jar) -or !(Test-Path -LiteralPath $Jar -PathType Leaf)){
 throw "Arquivo JAR do plugin nao encontrado: $Jar"
}
```

---

## 4. Plano de Verificação e Testes Pós-Aplicação

1. **Verificador de Integridade (`verificador-integridade`)**:
   - Compilar `verificador-integridade/src/IntegrityVerifier.java` e empacotar via `build-single-exe.ps1 -OfflineBuild`.
   - Executar `SecurityRegressionTest.java` (validando `reportBudgetRejectsLargeOutputBeforeBuildingIt`, `batchFailurePreservesPreviousReports`, `htmlReportHasCspNoInlineOnclickAndEscapesApostrophe`, `htmlReportBlocksAndPublicSummaryHashesMatch` e demais 22 casos).
   - Executar `--self-test` no `.jar` e no `.exe`.
2. **Plugin Chat Captura IPED (`chat-captura IPED`)**:
   - Atualizar os hashes SHA-256 do novo verificador em `VerifierIntegrity.java`, `VerifierIntegrityHashTest.java` e `test_verifier_java_integration.py`.
   - Compilar o fat-JAR `iped-viewers-impl-4.3.1.jar` e validar com `instalar_captura_chat_zap\validate-plugin-jar.ps1` (com e sem parâmetro `-Jar`).
   - Executar `CaptureIntegrityTest`, `VerifierIntegrityHashTest`, `ChatAnnotationStoreRoundTripTest`, `PathContainmentTest`, `test_verifier_java_integration.py` e `test_marker_boundary_contract.py`.
