# Release Notes — Chat Captura IPED

Histórico consolidado das releases do plugin e do **Verificador de Integridade**.
Para hashes detalhados e versões anteriores, consulte [CHECKSUMS.md](CHECKSUMS.md), [versao_hash.md](versao_hash.md) e [CHANGELOG.md](CHANGELOG.md).

> **Nota de publicação:** As releases públicas oficiais no GitHub iniciam na estável **`v2.6.8`** (`v2.6.8`, `v2.6.9`, `v2.7.0`). As releases `<= 2.6.8-beta` abaixo documentam o histórico completo de desenvolvimento e auditoria no Gitea local.

---

## v2.9.5-beta (Beta / `beta`) / Verificador 1.6.2

- **Tag**: [v2.9.5-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.9.5-beta) (Compilação determinística via `build-reproducible.ps1`)
- **Branch base**: `beta` (canal beta — `main` estável `v2.9.4` preservada intacta)

### Downloads
- [instalador-chat-captura-IPED-2.9.5-beta-verificador-1.6.2.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.5-beta/instalador-chat-captura-IPED-2.9.5-beta-verificador-1.6.2.zip) (`120.434.850` bytes | SHA-256: `264DDF8257DC07DD9FBCD2B4DE9BB909666335C54F177E3C240C4BB318D40B96`)
- [integridade-verifier-1.6.2.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.5-beta/integridade-verifier-1.6.2.exe) (`90.593.370` bytes | SHA-256: `5430CFCA723B329E5CDA31E40870D83E0CCE397667050B3DFBE1F2606E1880A8`)
- [integridade-verifier-1.6.2.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.5-beta/integridade-verifier-1.6.2.jar) (`13.066.909` bytes | SHA-256: `D7AEE56F59A79F2396968B94EDA2C8DBD50219A7FF8C3E1241E360007C96D3C5`)
- [iped-viewers-impl-4.3.1-2.9.5-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.5-beta/iped-viewers-impl-4.3.1-2.9.5-beta.jar) (`1.684.252` bytes | SHA-256: `6EAFD76EC54837D1A35A0A2BE370378F080B17029F05EC90DD50970DB8A1C172`)

### Adicionado
- **Botão `"?"` Sobre (About) no Verificador `1.6.2`:** botão compacto no canto superior direito das 4 janelas principais (*Início*, *Resultado*, *Integridade Público* e *Auditoria de origem*) abrindo o diálogo **Sobre — Verificador de Integridade** com versão, criador (`iagobusca`), criado desde `2026`, licença GPLv3 e link do repositório oficial (`https://github.com/iagobusca/chat-captura-IPED`) com botões **Copiar link** e **Abrir no navegador** (tudo com `html.disable = true`).

### Resumo
- Adição do diálogo **Sobre (About)** acessível pelo botão `"?"` no **Verificador `1.6.2`** na branch `beta`, com allowlist exclusiva do plugin **`2.9.5-beta`** sincronizada e auditoria dupla de reprodutibilidade aprovada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`).

---

## v2.9.4 (Estável / `main`) / Verificador 1.6.1

- **Tag**: [v2.9.4](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.9.4) (Release estável compilada deterministicamente via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.9.4-verificador-1.6.1.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.4/instalador-chat-captura-IPED-2.9.4-verificador-1.6.1.zip) (`120.429.334` bytes | SHA-256: `B4A30FD9CEFBD9997D7927E738D932202A502F9806E1D0350F81B8A42332E9D9`)
- [integridade-verifier-1.6.1.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.4/integridade-verifier-1.6.1.exe) (`90.590.810` bytes | SHA-256: `B0ED015292457F5F3389755786EFCFE7961A77E18A3356C2CA6B0AFE7CFEC59B`)
- [integridade-verifier-1.6.1.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.4/integridade-verifier-1.6.1.jar) (`13.064.528` bytes | SHA-256: `D1DD34970DB68DA0442106CCF40E887B60A6FABCC7E2BDAEE92E9F87E938EFC9`)
- [iped-viewers-impl-4.3.1-2.9.4.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.4/iped-viewers-impl-4.3.1-2.9.4.jar) (`1.684.209` bytes | SHA-256: `F3554FC0FA0160E1D26697119946F37807BEAE2EBEBA78D1BC9DBE6BC7A31CDD`)

### Adicionado
- **Scroll vertical completo no menu lateral (`ChatCapturePanel`):** todo o menu lateral (pasta destino, nome custom, pasta imagens blur, origem Avilla, caso IPED pai, anotações, contadores, busca e botões `Start` / `Re-Extrair` / `Anotar chat salvo...` / `Salvar Trecho-Notas` / `Dados do Laudo...`) agora conta com `JScrollPane` vertical sem barra horizontal e rolagem suave pela rodinha do mouse, além de `Similaridade blur` e `Blur censura` lado a lado e botões de ação em largura total.

### Corrigido
- **Re-Extrair em lote (race condition + páginas divididas `_0`):** `HtmlLinkViewer` agora reaproveita o chat já aberto e pronto no WebView (evitando reloads redundantes no `FileProcessor`), sincroniza trocas de conversa por geração de carga (`loadFile` → `Worker.State.SUCCEEDED` + DOM `div.linha` pronto) e impede falsos positivos entre páginas divididas do mesmo chat (`...5511985514199` vs `...5511985514199_0`).
- **Quebra de linha responsiva no Verificador `1.6.1`:** `"Log Cronológico (laudo/Log.txt): ..."` e `"MODO: ..."` na janela de resultado usam `wrappedPlainLabel` (`JTextArea` somente leitura com quebra por palavra, `html.disable = true`, seleção/cópia com Ctrl+C e reflow dinâmico ao redimensionar a janela), eliminando truncamento com `"..."`.

### Resumo
- Promoção estável da série `2.9.1-beta`..`2.9.4-beta` para a branch **`main`** como **Plugin `2.9.4`** / **Verificador `1.6.1`**, com compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e suíte completa `full-local` aprovada.

---

## v2.9.4-beta (Beta / `beta`) / Verificador 1.6.1

- **Tag**: [v2.9.4-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.9.4-beta) (Compilação determinística via `build-reproducible.ps1`)
- **Branch base**: `beta` (canal beta — `main` estável `v2.9.0` preservada intacta)

### Downloads
- [instalador-chat-captura-IPED-2.9.4-beta-verificador-1.6.1.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.4-beta/instalador-chat-captura-IPED-2.9.4-beta-verificador-1.6.1.zip) (`120.429.185` bytes | SHA-256: `36A6B31B2665F80E87E51254D771EFA33D32C7938E0FE9AEC40C2BFFA2FC040E`)
- [integridade-verifier-1.6.1.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.4-beta/integridade-verifier-1.6.1.exe) (`90.590.810` bytes | SHA-256: `B0ED015292457F5F3389755786EFCFE7961A77E18A3356C2CA6B0AFE7CFEC59B`)
- [integridade-verifier-1.6.1.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.4-beta/integridade-verifier-1.6.1.jar) (`13.064.528` bytes | SHA-256: `D1DD34970DB68DA0442106CCF40E887B60A6FABCC7E2BDAEE92E9F87E938EFC9`)
- [iped-viewers-impl-4.3.1-2.9.4-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.4-beta/iped-viewers-impl-4.3.1-2.9.4-beta.jar) (`1.684.245` bytes | SHA-256: `B36A4D283120DA7D9D4D2ADF43B17E5CC57F4823F69516B06B30DB4295F527A9`)

### Corrigido
- **Quebra de linha responsiva no Verificador `1.6.1` (`showSummaryDialog`):** o texto `"Log Cronológico (laudo/Log.txt): ..."` e o cabeçalho `"MODO: ..."` na tela de resultado deixaram de usar `JLabel` de linha única (que cortava com `"..."` em janelas pequenas) e passaram a usar `wrappedPlainLabel` (`JTextArea` somente leitura com `setLineWrap(true)`, `setWrapStyleWord(true)`, `html.disable = true`, seleção/cópia com Ctrl+C e reflow dinâmico ao redimensionar a janela).

### Resumo
- Melhoria de legibilidade no resumo do **Verificador de Integridade `1.6.1`** na branch `beta`, com allowlist exclusiva do plugin **`2.9.4-beta`** sincronizada e auditoria dupla de reprodutibilidade aprovada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`).

---

## v2.9.3-beta (Beta / `beta`) / Verificador 1.6.0

- **Tag**: [v2.9.3-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.9.3-beta) (Compilação determinística via `build-reproducible.ps1`)
- **Branch base**: `beta` (canal beta — `main` estável `v2.9.0` e **Verificador `1.6.0`** preservados 100% intactos)

### Downloads
- [instalador-chat-captura-IPED-2.9.3-beta-verificador-1.6.0.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.3-beta/instalador-chat-captura-IPED-2.9.3-beta-verificador-1.6.0.zip) (`120.425.049` bytes | SHA-256: `C0E1A34FEBBB3384D546934DDB16D15457CBC36D93EA4150F0C4DAA846F2A77B`)
- [integridade-verifier-1.6.0.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.3-beta/integridade-verifier-1.6.0.exe) (`90.589.274` bytes | SHA-256: `1F14B7A10E9AEE2B94B699066478AAD237A424077C7D0A033005DBD2FEED4920`)
- [integridade-verifier-1.6.0.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.3-beta/integridade-verifier-1.6.0.jar) (`13.062.572` bytes | SHA-256: `E5006E345C0894E0678BFE97A71FBD5399D1FEEA77D67A30DCCF90049A580745`)
- [iped-viewers-impl-4.3.1-2.9.3-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.3-beta/iped-viewers-impl-4.3.1-2.9.3-beta.jar) (`1.684.251` bytes | SHA-256: `1E84F6E390E1CFDC727402B20F21224FA8FBF8977C725774C203699160E0D9F3`)

### Corrigido
- **Chats divididos (`_0`, `_1`, ...) no Re-Extrair (`HtmlLinkViewer.matchesRecaptureJob`):** quando a captura possui identificação explícita do item de origem (`sourceName` / `sourcePath` / `sourceHash`), o plugin não cai mais no fallback genérico por `chatId`/título — evitando reaproveitar erroneamente a página sem `_0` para capturas que pertencem à página `_0` (e vice-versa).

### Resumo
- Correção para páginas divididas de conversas grandes no **Re-Extrair** na branch `beta` (`2.9.3-beta`), mantendo o **Verificador de Integridade `1.6.0` 100% inalterado**.

---

## v2.9.2-beta (Beta / `beta`) / Verificador 1.6.0

- **Tag**: [v2.9.2-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.9.2-beta) (Compilação determinística via `build-reproducible.ps1`)
- **Branch base**: `beta` (canal beta — `main` estável `v2.9.0` e **Verificador `1.6.0`** preservados 100% intactos)

### Downloads
- [instalador-chat-captura-IPED-2.9.2-beta-verificador-1.6.0.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.2-beta/instalador-chat-captura-IPED-2.9.2-beta-verificador-1.6.0.zip) (`120.424.724` bytes | SHA-256: `924CF33B65FD4D8FA6D0AA306583F009D7E95B575FA3E28C2D40B462AE5C5AF4`)
- [integridade-verifier-1.6.0.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.2-beta/integridade-verifier-1.6.0.exe) (`90.589.274` bytes | SHA-256: `1F14B7A10E9AEE2B94B699066478AAD237A424077C7D0A033005DBD2FEED4920`)
- [integridade-verifier-1.6.0.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.2-beta/integridade-verifier-1.6.0.jar) (`13.062.572` bytes | SHA-256: `E5006E345C0894E0678BFE97A71FBD5399D1FEEA77D67A30DCCF90049A580745`)
- [iped-viewers-impl-4.3.1-2.9.2-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.2-beta/iped-viewers-impl-4.3.1-2.9.2-beta.jar) (`1.684.041` bytes | SHA-256: `1C96410CD48E0750023B28CBD30CBFAD590B84F3F2977CA6B54C824CC125C02F`)

### Corrigido
- **Race condition no Re-Extrair em lote com capturas consecutivas do mesmo chat (`HtmlLinkViewer`):** `openRecaptureChat` agora reaproveita o chat já aberto e pronto no WebView (sem disparar reload redundante no `FileProcessor`), e trocas de conversa aguardam a conclusão real da nova geração de carga (`loadFile` → `Worker.State.SUCCEEDED` + DOM `div.linha` pronto), eliminando as falsas falhas *"A mensagem marcada não está mais disponível na conversa carregada"*.

### Resumo
- Correção cirúrgica na sincronização de carregamento do WebView durante o **Re-Extrair** na branch `beta` (`2.9.2-beta`), mantendo o **Verificador de Integridade `1.6.0` 100% inalterado** (mesmos hashes oficiais).

---

## v2.9.1-beta (Beta / `beta`) / Verificador 1.6.0

- **Tag**: [v2.9.1-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.9.1-beta) (Compilação determinística via `build-reproducible.ps1`)
- **Branch base**: `beta` (canal beta — `main` estável `v2.9.0` e **Verificador `1.6.0`** preservados 100% intactos)

### Downloads
- [instalador-chat-captura-IPED-2.9.1-beta-verificador-1.6.0.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.1-beta/instalador-chat-captura-IPED-2.9.1-beta-verificador-1.6.0.zip) (`120.423.482` bytes | SHA-256: `95094BFD9DCE1DB9A8E55AC700C716331FBBD179081D5CC6B14341A7CBD54556`)
- [integridade-verifier-1.6.0.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.1-beta/integridade-verifier-1.6.0.exe) (`90.589.274` bytes | SHA-256: `1F14B7A10E9AEE2B94B699066478AAD237A424077C7D0A033005DBD2FEED4920`)
- [integridade-verifier-1.6.0.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.1-beta/integridade-verifier-1.6.0.jar) (`13.062.572` bytes | SHA-256: `E5006E345C0894E0678BFE97A71FBD5399D1FEEA77D67A30DCCF90049A580745`)
- [iped-viewers-impl-4.3.1-2.9.1-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.1-beta/iped-viewers-impl-4.3.1-2.9.1-beta.jar) (`1.682.985` bytes | SHA-256: `2E62143D0BD7162155D08A5B7FF9BD09CA7650C2C14AAEE98B88ED49965FD5A9`)

### Adicionado
- **Scroll vertical no menu lateral (`ChatCapturePanel` — Opção B):** todo o menu lateral (pasta de destino, nome custom, pasta imagens blur, origem Avilla, caso IPED pai, anotações, contadores, busca e botões `Start` / `Re-Extrair` / `Anotar chat salvo...` / `Salvar Trecho-Notas` / `Dados do Laudo...`) agora é exibido dentro de um `JScrollPane` vertical com `Scrollable` ajustado à largura da barra (sem barra horizontal e com rolagem suave pela rodinha do mouse).
- **Layout mais compacto e legível:** `Similaridade blur` e `Blur censura` posicionados lado a lado em 1 linha e grade de botões de ação em largura total acima da linha de status e do progresso de re-extração.

### Resumo
- Ajuste exclusivo de interface do plugin na branch `beta` (`2.9.1-beta`) para garantir acesso completo a todas as opções e botões em telas menores ou painéis divididos, mantendo o **Verificador de Integridade `1.6.0` 100% inalterado** (mesmos hashes oficiais).

---

## v2.9.0 (Estável / `main`) / Verificador 1.6.0

- **Tag**: [v2.9.0](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.9.0) (Release estável compilada deterministicamente via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.9.0-verificador-1.6.0.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.0/instalador-chat-captura-IPED-2.9.0-verificador-1.6.0.zip) (`120.421.798` bytes | SHA-256: `7E69581E8C954CCE2BFCF09DB7E66F13B33D7C62B2FF20EFCC08633A1506615D`)
- [integridade-verifier-1.6.0.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.0/integridade-verifier-1.6.0.exe) (`90.589.274` bytes | SHA-256: `1F14B7A10E9AEE2B94B699066478AAD237A424077C7D0A033005DBD2FEED4920`)
- [integridade-verifier-1.6.0.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.0/integridade-verifier-1.6.0.jar) (`13.062.572` bytes | SHA-256: `E5006E345C0894E0678BFE97A71FBD5399D1FEEA77D67A30DCCF90049A580745`)
- [iped-viewers-impl-4.3.1-2.9.0.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.9.0/iped-viewers-impl-4.3.1-2.9.0.jar) (`1.681.418` bytes | SHA-256: `ED3AA0F37DAC4D17846123A7A2FF958113EAFC0A8D733F7C33E026DCEF782C50`)

### Adicionado
- **Laudo Técnico Nativo (PDFBox + HTML) e Janela de Cadeia de Custódia Enxuta (10 campos):** nova janela modal exibida em **Start**, **Re-Extrair** e pelo botão **"Dados do Laudo..."** com 4 campos de Dispositivo (`Dispositivo / Modelo`, `IMEI`, `Número de série`, `Versão do SO`), 6 campos de Cadeia de Custódia (`Quem coletou`, `Quem analisou`, `Quando`, `Onde`, seletor `[Pasta | Mídia lacrada | Custom]` com campo dinâmico para `Nº do Lacre` / texto livre e `Nº do caso / Laudo`) e botão **"Recarregar do report.pdf"**.
- **Cópia Intacta de `laudo/report.pdf` + `laudo/report-plugin.pdf` Trilíngue (PT/EN/ES):** quando há `report.pdf` na origem Avilla, o plugin realiza cópia byte a byte sem edição para `laudo/report.pdf` (SHA-256 conferido), extrai metadados por seção sem vazamento de campos vazios e gera `laudo/report-plugin.pdf` nativo via Apache PDFBox 2.0.27 com aviso inicial e metodologia em 3 idiomas, filtros blur automáticos, hashes originais/exportados e **1 único SHA-256** de `laudo/Log.txt`.
- **Log Único por Sessão (`laudo/Log.txt`) com Auditoria sem PDFBox no Verificador `1.6.0`:** arquivo append-only organizado por sessões (`CAPTURA ADICIONADA`, `CAPTURA REMOVIDA`, `RE-EXTRAIR`), com alerta e opção ao usuário para recriar em caso de ausência/adulteração e verificação direta pelo Verificador `1.6.0` (Java 8 puro, sem PDFBox) via âncora `CHATCAPTURA_LOGANCHOR_V2` (com retrocompatibilidade `V1`).
- **Pacote de Relatório IPED Embutido por Captura (`relatorio.htm`, `Lista de Arquivos.csv` e `chat-captura-IPED.log`):** cada captura `html-message-v1` gera relatório frameset offline sem logos/marcas externas, inventário CSV (colunas IPED + `SHA256` + `Mensagem`, MD5/SHA-1/SHA-256 reais e proteção contra formula injection) e log técnico sem texto privado das mensagens — todos selados por SHA-256 em `integridade.json`, validados fail-closed pelo Verificador `1.6.0` e preservados no Re-Extrair, mantendo 100% de retrocompatibilidade com `historico-capturas.txt` antigos.

### Resumo
- Promoção estável da série `2.8.11-beta`..`2.8.14-beta` para a branch `main` como **Plugin `2.9.0`** / **Verificador `1.6.0`**, com compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada.

---

## v2.8.14-beta (Beta / `beta`) / Verificador 1.5.12

- **Tag**: [v2.8.14-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.8.14-beta) (Compilação determinística via `build-reproducible.ps1`)
- **Branch base**: `beta` (canal beta — `main` estável preservada intacta)

### Downloads
- [instalador-chat-captura-IPED-2.8.14-beta-verificador-1.5.12.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.14-beta/instalador-chat-captura-IPED-2.8.14-beta-verificador-1.5.12.zip) (`120.421.539` bytes | SHA-256: `068E24240DBCFC2CB8DC3298983039F0DF71B2F1B634239EA02A5700FF3F6C3C`)
- [integridade-verifier-1.5.12.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.14-beta/integridade-verifier-1.5.12.exe) (`90.589.276` bytes | SHA-256: `BF3AAC82D5F35D2D7F033D05AED2B7CF539F5620806BDE53A97B0F184E7EF393`)
- [integridade-verifier-1.5.12.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.14-beta/integridade-verifier-1.5.12.jar) (`13.062.577` bytes | SHA-256: `B84289B03C2EDA7C675FB29B72372677262417D809C991464CE26A219F6C7B3B`)
- [iped-viewers-impl-4.3.1-2.8.14-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.14-beta/iped-viewers-impl-4.3.1-2.8.14-beta.jar) (`1.681.469` bytes | SHA-256: `6C4418E8A543AE6F07C8F1240A3ADDC979C2B6F707D1D59E0DD8E090DF3F12EE`)

### Adicionado
- **Janela de Cadastro/Conferência enxuta (de 33 para 10 campos essenciais):** 4 campos de Dispositivo (`Dispositivo / Modelo`, `IMEI`, `Número de série`, `Versão do SO` — removidos `Fabricante`, `Versão do WhatsApp`, `Código da coleta` e `Ferramenta original` da tela) e 6 campos de Cadeia de Custódia (`Quem coletou`, `Quem analisou`, `Quando (coleta)`, `Onde (local)`, seletor `[Pasta | Mídia lacrada | Custom]` com 1 campo dinâmico para `Nº do Lacre` ou texto livre e `Nº do caso / Laudo`).
- **Preenchimento automático de `relatorio/caseinformation.htm` e filtro blur:** os metadados do relatório HTML da captura (`Signatário(s)`, `Laudo`/`Referências`, `Material examinado`, `Data do laudo`) são derivados automaticamente do cadastro acima, a cópia intacta do `report.pdf` de origem é sempre automática quando existir e o filtro de blur/censura visual é registrado automaticamente a partir do estado real do painel (removendo a seção manual de filtros do IPED).
- **Log único unificado (`laudo/Log.txt`) organizado por sessão:** removidos os arquivos duplicados `registro-capturas.jsonl` e `registro-capturas.log`; agora existe um único `laudo/Log.txt` legível por sessão (`CAPTURA ADICIONADA`, `CAPTURA REMOVIDA`, `RE-EXTRAIR`), mantendo alerta + opção ao usuário para recriar em caso de ausência ou adulteração.
- **PDF compacto com 1 único SHA-256 do `laudo/Log.txt` + Verificador `1.5.12`:** `laudo/report-plugin.pdf` exibe resumo limpo com total de sessões e 1 único SHA-256 de `laudo/Log.txt` (âncora `CHATCAPTURA_LOGANCHOR_V2` no `/Info`), auditado sem PDFBox pelo Verificador `1.5.12` (Java 8 puro) com retrocompatibilidade total para âncoras `V1`.

### Resumo
- Simplificação completa da experiência do laudo na branch `beta` (janela com 10 campos, log único por sessão e SHA-256 único no `report-plugin.pdf`), compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.12**.

---

## v2.8.13-beta (Beta / `beta`) / Verificador 1.5.11

- **Tag**: [v2.8.13-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.8.13-beta) (Compilação determinística via `build-reproducible.ps1` — Fase 3)
- **Branch base**: `beta` (canal beta — `main` estável preservada intacta)

### Downloads
- [instalador-chat-captura-IPED-2.8.13-beta-verificador-1.5.11.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.13-beta/instalador-chat-captura-IPED-2.8.13-beta-verificador-1.5.11.zip) (`120.419.728` bytes | SHA-256: `2ED36B93C8420A4DD2E2821A6CEB9443AD957BB42AF6C21CBE0814FF0579AB12`)
- [integridade-verifier-1.5.11.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.13-beta/integridade-verifier-1.5.11.exe) (`90.587.740` bytes | SHA-256: `A7F21554A38941543D944563C5B91A1CCD1EA3DC9BE63CEC2623FAF5E2668ACF`)
- [integridade-verifier-1.5.11.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.13-beta/integridade-verifier-1.5.11.jar) (`13.061.220` bytes | SHA-256: `CC80FA62B2A8AE85AE15D9B6A829EB59E8BBB3E996D3E9A314F288B545C0D24B`)
- [iped-viewers-impl-4.3.1-2.8.13-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.13-beta/iped-viewers-impl-4.3.1-2.8.13-beta.jar) (`1.681.985` bytes | SHA-256: `DC8D7E9193166F51046A51AD5C7DAB3A0CB8B379DAA4503010BF5E588E426737`)

### Adicionado
- **Pacote de Relatório IPED Embutido por Captura (`CaptureReportPackageWriter`):** cada captura `html-message-v1` agora gera automaticamente `relatorio.htm` + `relatorio/**` (estrutura de frameset compatível com o IPED **sem logos ou marcas institucionais externas**, incluindo `caseinformation.htm`, `contents.htm`, páginas por categoria `cat_*.html`, galeria `thumbs_001.htm`, `integridade_resumo.htm`, `ajuda.htm` offline e folhas CSS limpas sob CSP restritiva e zero scripts).
- **Inventário CSV e Log Técnico da Captura selados por SHA-256:** geração determinística de `Lista de Arquivos.csv` (cabeçalho oficial do IPED + `SHA256` + `Mensagem`, com MD5/SHA-1/SHA-256 reais e neutralização contra CSV formula injection) e `chat-captura-IPED.log` (log técnico sem exposição de texto privado das mensagens), todos registrados no inventário `integridade.json` e preservados/reparados seletivamente no **Re-Extrair**.
- **Validação Fail-Closed no Verificador `1.5.11`:** suporte dedicado a `Lista de Arquivos.csv`, `chat-captura-IPED.log`, `relatorio.htm` e `relatorio/*.htm|html|css` contra `integridade.json`, bloqueando extras não inventariados, adulteração de 1 byte, `<script>`/`on*=`, URLs externas, path traversal e CSS `url()`/`@import`, mantendo 100% de compatibilidade retroativa com capturas anteriores.
- **Formato padronizado das Releases Gitea (`Adicionado`, `Otimizado`, `Corrigido`, `Incluido`, `SHA-256 do Verificador`):** seção `## Adicionado` incorporada automaticamente pelo script de publicação das releases.

### Resumo
- Fase 3 completa da série beta (Pacote de Relatório IPED Embutido por Captura + Validação Fail-Closed no Verificador `1.5.11` + nova seção `Adicionado` nas Releases Gitea), com compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.11**.

---

## v2.8.12-beta (Beta / `beta`) / Verificador 1.5.10

- **Tag**: [v2.8.12-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.8.12-beta) (Compilação determinística via `build-reproducible.ps1` — Fases 1 e 2)
- **Branch base**: `beta` (canal beta — `main` estável preservada intacta)

### Downloads
- [instalador-chat-captura-IPED-2.8.12-beta-verificador-1.5.10.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.12-beta/instalador-chat-captura-IPED-2.8.12-beta-verificador-1.5.10.zip) (`120.396.835` bytes | SHA-256: `3ABB3DAF97E72C02FCC50EA56F326754D2F00EA8E9150D21E4E478F53F1EA90F`)
- [integridade-verifier-1.5.10.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.12-beta/integridade-verifier-1.5.10.exe) (`90.585.692` bytes | SHA-256: `8C34C3E1E744BE108E5EE0D28010C053FF35166F5E7D119B1AE0E745E79082AC`)
- [integridade-verifier-1.5.10.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.12-beta/integridade-verifier-1.5.10.jar) (`13.058.749` bytes | SHA-256: `09D7166AE5EF62C63E6888B392D12C67A6457861974B2677352FE4D65520DE30`)
- [iped-viewers-impl-4.3.1-2.8.12-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.12-beta/iped-viewers-impl-4.3.1-2.8.12-beta.jar) (`1.663.380` bytes | SHA-256: `83FED2F39B3268B7744F48D61A74B881FB16B79650BD7495B85EDA90D90904F9`)

### Resumo
- **Fase 1 (`laudo/report.pdf` intacto + `laudo/report-plugin.pdf` padrão Avilla trilíngue):** parser por seção do `report.pdf` original (sem vazamento em campos vazios), cópia byte a byte sem edição para `laudo/report.pdf`, aviso inicial PT/EN/ES explicando que `report.pdf` é o original, `Log Cronológico Encadeado` acima de `Method used`, metodologia do plugin em 3 idiomas (PT/EN/ES) e janela do laudo mantendo "Recarregar do report.pdf" + Cadeia de Custódia e Dados do Relatório HTML (sem Operador/Apreensão).
- **Fase 2 (`laudo/Log.txt` append-only + âncora PDF sem PDFBox + Auditoria no Verificador `1.5.10`):** arquivo `laudo/Log.txt` append-only (linha 0 `CRIADO` com timestamp de criação e cadeia SHA-256 `prev`/`hash`), alerta com opção ao usuário para recriar (marcando no `report-plugin.pdf` e preservando cópia corrompida), âncora ASCII `CHATCAPTURA_LOGANCHOR_V1` no `/Info` do PDF e auditoria completa no Verificador `1.5.10` (Java 8 puro, sem PDFBox) validando cadeia, prefixo `0..X`, truncamento, ausência e linhas posteriores.
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.10**.

---

## v2.8.11-beta (Beta / `beta`) / Verificador 1.5.9

- **Tag**: [v2.8.11-beta](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.8.11-beta) (Compilação determinística via `build-reproducible.ps1`)
- **Branch base**: `beta` (canal beta — `main` estável preservada intacta)

### Downloads
- [instalador-chat-captura-IPED-2.8.11-beta-verificador-1.5.9.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.11-beta/instalador-chat-captura-IPED-2.8.11-beta-verificador-1.5.9.zip) (`120.359.149` bytes | SHA-256: `4300D7F73FED0CAE7FBEDBB11D1C8A5665F584B0B5B9A4FC8BB1126CA35E80FC`)
- [integridade-verifier-1.5.9.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.11-beta/integridade-verifier-1.5.9.exe) (`90.580.058` bytes | SHA-256: `23386DC247DA905D268527CD58868390FDB33AAC3E89B86A178BA9B1CAEAB55C`)
- [integridade-verifier-1.5.9.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.11-beta/integridade-verifier-1.5.9.jar) (`13.052.705` bytes | SHA-256: `39F076C777A31BE9831BF0218FE00EF2F3B169B28010E9BD9CE8D40EEA2C187A`)
- [iped-viewers-impl-4.3.1-2.8.11-beta.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.11-beta/iped-viewers-impl-4.3.1-2.8.11-beta.jar) (`1.636.520` bytes | SHA-256: `D65910C084D19DEE0C3413623A49373BEA18E62B76B54A2E587067694689C17D`)

### Resumo
- **Laudo Técnico / Cadeia de Custódia (`laudo/`):** nova janela modal pré-preenchida exibida após o aviso do verificador em **Start** e **Re-Extrair** (além do botão **"Dados do Laudo..."** no painel), reunindo identificação do dispositivo (fabricante, modelo, IMEI, nº de série, SO, WhatsApp, `Collection Code`), ferramenta/parâmetros do IPED, hashes SHA-256 dos arquivos originais e exportados, passo a passo cronológico com data/hora local + UTC e cadeia de custódia (`Quem coletou`, `Quem analisou`, `Quando`, `Onde` e seletor `Como foi armazenado` `[Pasta, Mídia lacrada, Custom]`).
- **Leitura e Complementação do `report.pdf` + PDFBox nativo:** quando existe `report.pdf` na origem, o plugin realiza cópia bit-a-bit verificada (SHA-256) para `laudo/report-origem.pdf`, extrai os metadados já presentes e complementa os campos faltantes no relatório do plugin (`laudo/report-plugin.pdf` gerado diretamente via Apache PDFBox 2.0.27, `laudo/laudo-tecnico.html` offline com CSP restritiva, `laudo/laudo-hashes.txt` e opção de PDF unificado `laudo/report-completo-unificado.pdf`). Quando não há `report.pdf`, gera normalmente o relatório completo do plugin.
- **Log encadeado de capturas (`laudo/registro-capturas.jsonl` + `.log`):** registro cronológico encadeado por SHA-256 (`previousRecordHash` -> `recordHash`) com contagem categorizada (`msg`, `img`, `audio`, `video`, `doc`, `outros`), status `INALTERADO_OK` no Re-Extrair quando todos os arquivos permanecem íntegros e detecção automática de `ADICIONADO captura` / `DELETADO captura`.
- **Verificador `1.5.9`:** ignora `laudo/` na descoberta de capturas (preservando `rootHash` e compatibilidade retroativa) e valida `laudo/report-origem.pdf` contra o `report.pdf` de origem em modo fail-closed na Auditoria de Origem.
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`), allowlist exclusiva sincronizada para Verificador **1.5.9** e suíte completa `full-local` aprovada (`VERIFICATION_SUITES_COMPLETE=PASS`).

---

## v2.8.10 (Estável / `main`) / Verificador 1.5.8

- **Tag**: [v2.8.10](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.8.10) (Corte automatizado via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.8.10-verificador-1.5.8.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.10/instalador-chat-captura-IPED-2.8.10-verificador-1.5.8.zip) (`120.287.085` bytes | SHA-256: `724B413C2BCC8DD68ECD748A0946AA06F39A532843FA2574B08CD77197CB1DD0`)
- [integridade-verifier-1.5.8.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.10/integridade-verifier-1.5.8.exe) (`90.580.058` bytes | SHA-256: `7E26F0ABFE20CEA7277B76E5781DE262A1525DA59EF17C211FC062398D118CC0`)
- [integridade-verifier-1.5.8.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.10/integridade-verifier-1.5.8.jar) (`13.052.436` bytes | SHA-256: `07EFAB49FE93DFAD1014C6E7DA50BDEADD5108BDAEF13AE267416BF3ACF41C6E`)
- [iped-viewers-impl-4.3.1-2.8.10.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.10/iped-viewers-impl-4.3.1-2.8.10.jar) (`1.563.730` bytes | SHA-256: `659182076A67B1F8FA7B00D3317E9E0B1141FB7EE3E253144D63F3EAD387AD60`)

### Resumo
- Seguranca V-05r (v2.8.10 / Verificador 1.5.8): driver oficial sqlite-jdbc-3.41.2.2.jar (SHA-256 pinado 0CDAB410947E04B6743DF99CF1543267DDD107357D6F76948D145BE590FD497D) embutido diretamente no fat-JAR e no runtime EXE do Verificador de Integridade; removida toda busca externa de driver (resolveDynamicSqliteDriver/URLClassLoader/candidateDirs/path de dev) eliminando risco de carregamento de JAR nao confiavel a partir da pasta de captura/evidencia; preservado fallback fail-closed DRIVER_SQLITE_INDISPONIVEL_VALIDADO_VIA_BINDING e documentada a cadeia de confianca na tela de Auditoria de Origem, README e GUIA_DE_USO.
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.8**.

---
## v2.8.9 (Estável / `main`) / Verificador 1.5.7

- **Tag**: [v2.8.9](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.8.9) (Corte automatizado via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.8.9-verificador-1.5.7.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.9/instalador-chat-captura-IPED-2.8.9-verificador-1.5.7.zip) (`94.666.123` bytes | SHA-256: `D51EE1211DC95CF9980C299CCB8619C4AF5214AD23F33E3505914E342EC2572B`)
- [integridade-verifier-1.5.7.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.9/integridade-verifier-1.5.7.exe) (`77.771.354` bytes | SHA-256: `81D704B1EF8EB2E121B73FF98C583F3595E998D3F00DE72EB51C5AEF4CE3F3B3`)
- [integridade-verifier-1.5.7.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.9/integridade-verifier-1.5.7.jar) (`215.152` bytes | SHA-256: `A9170EFBF4F528AA0FD1FB5665F1E0F40C032023A7B78FE1BA94ACF5D0BEC6E1`)
- [iped-viewers-impl-4.3.1-2.8.9.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.9/iped-viewers-impl-4.3.1-2.8.9.jar) (`1.563.735` bytes | SHA-256: `D0E2D7792107C7F434FB46998369EBBFC07D9D593BDFAF3D10D9380050EE6F49`)

### Resumo
- Auditoria N3 (v2.8.9 / Verificador 1.5.7): corrigidos os 5 falsos positivos de divergenciasN3 em capturas autenticas do IPED (seletor CSS .from em div.incoming.from que igualava sender ao texto da bolha, extracao canonica do HTML da bolha decodificando emojis <img class=eHEX> para codepoints Unicode e ignorando variador FE0F, remocao balanceada de citacoes quoteFrom/quoteTo, span.time com atributos data-* e rotulo Encaminhada, suporte a message_type do SQLite para midias/audios/figurinhas/chamadas com text_data NULL e revalidacao SQLite N3 independente no Verificador standalone com jlink java.sql/java.logging e descoberta dinamica de sqlite-jdbc), preservando todas as garantias fail-closed da v2.8.8.
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.7**.

---
## v2.8.8 (Estável / `main`) / Verificador 1.5.6

- **Tag**: [v2.8.8](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.8.8) (Corte automatizado via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.8.8-verificador-1.5.6.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.8/instalador-chat-captura-IPED-2.8.8-verificador-1.5.6.zip) (`94.555.161` bytes | SHA-256: `54392E19B3E31B6CF502199E82409BE26D9F7F909D8B3FEA269855971B7309C1`)
- [integridade-verifier-1.5.6.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.8/integridade-verifier-1.5.6.exe) (`77.665.370` bytes | SHA-256: `5EF6B9AC2C6795A49A18C37029586B318FD09C3B71DA53486CF23722C9AAC917`)
- [integridade-verifier-1.5.6.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.8/integridade-verifier-1.5.6.jar) (`210.223` bytes | SHA-256: `A027A301AFFCF1D0A2310F6E1A495359AB36E08F3AA35147B19786C183CDA62A`)
- [iped-viewers-impl-4.3.1-2.8.8.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.8/iped-viewers-impl-4.3.1-2.8.8.jar) (`1.559.486` bytes | SHA-256: `5B1E86E59C0D5EF8A441566EF4CA580F8D77C0417492C1CFC7CF1C150F43DAB9`)

### Resumo
- Auditoria de seguranca e solidez forense N3: match canonico NFC + SHA-256 (sem substring/curinga/bypass apos data ISO), rechecagem SQLite independente em copia temporaria (sem mutacao -wal/-shm na origem), exigencia de SHA-256 (MD5 somente informativo), fail-closed sem Hashs.txt/ancoras e limites nos walks.
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.6**.

---
## v2.8.7 (Estável / `main`) / Verificador 1.5.5

- **Tag**: [v2.8.7](https://github.com/iagobusca/chat-captura-IPED/releases/tag/v2.8.7) (Corte automatizado via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.8.7-verificador-1.5.5.zip](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.7/instalador-chat-captura-IPED-2.8.7-verificador-1.5.5.zip) (`94.536.182` bytes | SHA-256: `3D05CF198ECE7852954DCD3CBBAB683821D10E0CD86FEA1601914A3938197EA4`)
- [integridade-verifier-1.5.5.exe](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.7/integridade-verifier-1.5.5.exe) (`77.658.202` bytes | SHA-256: `639CE7B5A78D0C4DA1D55D28ACE317F342C93F7BFF13A28F5B33D45DB8BE39D1`)
- [integridade-verifier-1.5.5.jar](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.7/integridade-verifier-1.5.5.jar) (`202.850` bytes | SHA-256: `827B4580836D7CE0811DB1975E5971A610C0C946D9DBDBECD4CCEAFB275FBEED`)
- [iped-viewers-impl-4.3.1-2.8.7.jar](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.7/iped-viewers-impl-4.3.1-2.8.7.jar) (`1.555.213` bytes | SHA-256: `680E031B29214376907662DDAC0FA83E34BA2B72895556DB1E8BF5F02AF47F46`)

### Resumo
- Verificador 1.5.5 nunca cria arquivos *.bak (e remove eventuais sobras .integridade.html.*.bak de versões anteriores), compara o integridade.html existente com o resultado da verificação para preservar o arquivo se idêntico ou editá-lo/substituí-lo se diferente, e limpa o atributo somente leitura antes de atualizar relatórios.
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.5**.

---
## v2.8.6 (Estável / `main`) / Verificador 1.5.4

- **Tag**: [v2.8.6](https://github.com/iagobusca/chat-captura-IPED/releases/tag/v2.8.6) (Corte automatizado via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.8.6-verificador-1.5.4.zip](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.6/instalador-chat-captura-IPED-2.8.6-verificador-1.5.4.zip) (`94.532.008` bytes | SHA-256: `D0A9D268027DB5FB77AAB542D77A3317FD74FCC9CE5D77F5EB535347609701BF`)
- [integridade-verifier-1.5.4.exe](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.6/integridade-verifier-1.5.4.exe) (`77.656.154` bytes | SHA-256: `959424CFC4991148F23A4E41F167512C449128392618F3B070E3837E34FFF370`)
- [integridade-verifier-1.5.4.jar](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.6/integridade-verifier-1.5.4.jar) (`200.920` bytes | SHA-256: `8180566A0BEF7D70815FBCFFA77DE37B833B9BD4B2C87B185BB1B2A5792A41EF`)
- [iped-viewers-impl-4.3.1-2.8.6.jar](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.6/iped-viewers-impl-4.3.1-2.8.6.jar) (`1.555.211` bytes | SHA-256: `D61591049C3B64EE4DB050A57D90E0018B301F8163FD43B01408F0FEE675666A`)

### Resumo
- Captura nova e Re-Extrair agora garantem em todos os fluxos (UI e direto) a verificação de integridade-verifier (.jar/.exe) na pasta de destino e subpastas existentes (integridade/ e verificador-integridade/): adiciona se ausente, preserva se já estiver na versão atual (SHA-256 válido) e substitui automaticamente se desatualizado (inclusive limpando atributo somente leitura).
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.4**.

---
## v2.8.5 (Estável / `main`) / Verificador 1.5.4

- **Tag**: [v2.8.5](https://github.com/iagobusca/chat-captura-IPED/releases/tag/v2.8.5) (Corte automatizado via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.8.5-verificador-1.5.4.zip](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.5/instalador-chat-captura-IPED-2.8.5-verificador-1.5.4.zip) (`94.531.413` bytes | SHA-256: `819FFDB06D0A7D66547F8E9E1C2D6DF8588E2DD2851E61702FCD63280F1A8619`)
- [integridade-verifier-1.5.4.exe](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.5/integridade-verifier-1.5.4.exe) (`77.656.154` bytes | SHA-256: `959424CFC4991148F23A4E41F167512C449128392618F3B070E3837E34FFF370`)
- [integridade-verifier-1.5.4.jar](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.5/integridade-verifier-1.5.4.jar) (`200.920` bytes | SHA-256: `8180566A0BEF7D70815FBCFFA77DE37B833B9BD4B2C87B185BB1B2A5792A41EF`)
- [iped-viewers-impl-4.3.1-2.8.5.jar](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.5/iped-viewers-impl-4.3.1-2.8.5.jar) (`1.554.621` bytes | SHA-256: `98668D3678D3338F98427C46637306BA9E5EDCC25C7877C2461DC28DF062F873`)

### Resumo
- Suporte a validação de historico-capturas.txt de versões anteriores no modo Integridade Público e em integridade.html: mantém exigência estrita para correspondência integral e valida por conteúdo criptográfico todas as mensagens (ARQUIVO|mensagem:*) e anexos (ARQUIVO|attachments/*) com detalhamento por captura, além de aceitar carregamento direto de arquivo .txt e hash de conjunto (SHA256|).
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.4**.

---
## v2.8.4 (Estável / `main`) / Verificador 1.5.3

- **Tag**: [v2.8.4](https://github.com/iagobusca/chat-captura-IPED/releases/tag/v2.8.4) (Corte automatizado via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.8.4-verificador-1.5.3.zip](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.4/instalador-chat-captura-IPED-2.8.4-verificador-1.5.3.zip) (`94.518.007` bytes | SHA-256: `9431BB088D216024C767967F894BC477A9FEA327CF01C18FCBE4E41DC6D0A099`)
- [integridade-verifier-1.5.3.exe](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.4/integridade-verifier-1.5.3.exe) (`77.649.498` bytes | SHA-256: `B2A3D1CDBE36673215EFF438C24CBC636D38A578AF5C74444726041BE42E0D0F`)
- [integridade-verifier-1.5.3.jar](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.4/integridade-verifier-1.5.3.jar) (`193.914` bytes | SHA-256: `39BE8D3C8E9F5389D21659D6A1E62435C246241578E3CAB9EEA2C22D03992456`)
- [iped-viewers-impl-4.3.1-2.8.4.jar](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.4/iped-viewers-impl-4.3.1-2.8.4.jar) (`1.554.607` bytes | SHA-256: `180617AFEEBC3FBC7FBA139100ECFD3C9944A91165DD2461122C45FDBAC1780A`)

### Resumo
- Removida a geração desnecessária de backups .bak temporários em atomicBatchStreaming do Verificador de Integridade (que causavam falha de acesso/bloqueio em Windows nos modos local, público e auditoria) e evitada a gravação antecipada de relatórios ao abrir Auditoria de Origem; adicionadas tentativas breves em bloqueios transitórios de arquivo.
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.3**.

---
## v2.8.3 (Estável / `main`) / Verificador 1.5.2

- **Tag**: [v2.8.3](https://github.com/iagobusca/chat-captura-IPED/releases/tag/v2.8.3) (Corte automatizado via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.8.3-verificador-1.5.2.zip](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.3/instalador-chat-captura-IPED-2.8.3-verificador-1.5.2.zip) (`94.517.624` bytes | SHA-256: `978F9F5BAD6D26FA532069A8F3C700D05555F3E500581F7907462B50CCB40DC2`)
- [integridade-verifier-1.5.2.exe](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.3/integridade-verifier-1.5.2.exe) (`77.648.986` bytes | SHA-256: `F3582CEFE0440B6DAA7DEA8E7F425A55C33DBC29EE2BE92DF875A2D18382B238`)
- [integridade-verifier-1.5.2.jar](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.3/integridade-verifier-1.5.2.jar) (`193.537` bytes | SHA-256: `22C207E89F56C8A35CAA327E7A491246739A81596172521E7B807EA167DDEB7F`)
- [iped-viewers-impl-4.3.1-2.8.3.jar](https://github.com/iagobusca/chat-captura-IPED/releases/download/v2.8.3/iped-viewers-impl-4.3.1-2.8.3.jar) (`1.554.616` bytes | SHA-256: `072999AF6249456B580F3EEAB5CE04D253201A8C9C39730AD2BFD085EA7FBEFA`)

### Resumo
- Start e Re-Extrair agora validam e propagam Pasta imagens blur, Origem Avilla e Caso IPED pai sem falhar em caminhos inválidos; Re-Extrair executa verificação seletiva e restaura/remove apenas arquivos específicos alterados, ausentes ou excedentes sem reescrever messages.json, manifest.json ou whatsapp.html íntegros.
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.2**.

---
## v2.8.2 (Estável / `main`) / Verificador 1.5.2

- **Tag**: [v2.8.2](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.8.2) (Corte automatizado via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.8.2-verificador-1.5.2.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.2/instalador-chat-captura-IPED-2.8.2-verificador-1.5.2.zip) (`94.506.571` bytes | SHA-256: `B7E2001C1841BFF37A740B6D9178618090C95BA5C6729E1B9EA5252E4BEE0D2F`)
- [integridade-verifier-1.5.2.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.2/integridade-verifier-1.5.2.exe) (`77.648.986` bytes | SHA-256: `F3582CEFE0440B6DAA7DEA8E7F425A55C33DBC29EE2BE92DF875A2D18382B238`)
- [integridade-verifier-1.5.2.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.2/integridade-verifier-1.5.2.jar) (`193.537` bytes | SHA-256: `22C207E89F56C8A35CAA327E7A491246739A81596172521E7B807EA167DDEB7F`)
- [iped-viewers-impl-4.3.1-2.8.2.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.2/iped-viewers-impl-4.3.1-2.8.2.jar) (`1.543.080` bytes | SHA-256: `357AD1A0E7A5DEE6E3A87234B7AA1796DB80F59DBD304BA8D2562E9A0FE28306`)

### Resumo
- Verificador 1.5.2: botao Auditoria de origem... exclusivo na tela inicial (removido da janela Resultado); Validar Auditoria passa a exibir pre-conferencia das pastas (Pasta de origem obrigatoria Hashs.txt/Hashs.avilla/report.pdf/msgstore.db + Pasta do caso opcional com dica de subpasta) habilitando Comecar Auditoria ou Fechar; janela de carregamento com progresso por etapa (1/9..9/9), tempo decorrido e Cancelar.
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.2**.

---
## v2.8.1 (Estável / `main`) / Verificador 1.5.1

- **Tag**: [v2.8.1](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.8.1) (Corte automatizado via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.8.1-verificador-1.5.1.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.1/instalador-chat-captura-IPED-2.8.1-verificador-1.5.1.zip) (`94.484.019` bytes | SHA-256: `0B7D4F1D4939847FE5909E40C63C37EEE131CD343C4CA7F2400F956F5264EB01`)
- [integridade-verifier-1.5.1.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.1/integridade-verifier-1.5.1.exe) (`77.638.746` bytes | SHA-256: `E03DEA3BFA298E125804AB22A51C113665FEAE58529248E0C3947C34EACB7C6F`)
- [integridade-verifier-1.5.1.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.1/integridade-verifier-1.5.1.jar) (`182.286` bytes | SHA-256: `9809DEC2088F50F88ED1691544CEF45B029783B04498F65F43ECC5634C06C7A3`)
- [iped-viewers-impl-4.3.1-2.8.1.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.1/iped-viewers-impl-4.3.1-2.8.1.jar) (`1.543.080` bytes | SHA-256: `240B2837DF894809668B7225E5B6D21BB774CB61AA2FA17FFD3FC869E8D3764F`)

### Resumo
- Verificador 1.5.1: botao Auditoria de origem... adicionado na tela inicial ao lado de Integridade Local/Publico com carregamento identico ao modo Publico, painel interativo (Pasta de origem, Pasta do caso opcional, Validar Auditoria com banner verde/vermelho, caixa de leitura/copia integral) e botao Exportar Auditoria para gerar Auditoria_relatorio.htm offline.
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.1**.

---
## v2.8.0 (Estável / `main`) / Verificador 1.5.0

- **Tag**: [v2.8.0](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.8.0) (Corte automatizado via `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.8.0-verificador-1.5.0.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.0/instalador-chat-captura-IPED-2.8.0-verificador-1.5.0.zip) (`94.472.170` bytes | SHA-256: `52ED8BD5D45F1E78D3CC95394C0D428006EC387F9DB41E10A2DF06FF6051FE3F`)
- [integridade-verifier-1.5.0.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.0/integridade-verifier-1.5.0.exe) (`77.632.090` bytes | SHA-256: `20F823BF5B22E92672DEB0D6DB6150733D1F1B9CD4313E6519ABDB5E4EB51524`)
- [integridade-verifier-1.5.0.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.0/integridade-verifier-1.5.0.jar) (`175.612` bytes | SHA-256: `194C1D9B6FE4AE615F882E74BE2B0ED662466F9E7AF2F887E5A8878A3242BA11`)
- [iped-viewers-impl-4.3.1-2.8.0.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.8.0/iped-viewers-impl-4.3.1-2.8.0.jar) (`1.543.075` bytes | SHA-256: `9BF31AC81A7FDD8ADA56123BDEB2F95D751B30ADA8E705B7B2BA1637FC926F94`)

### Resumo
- Auditoria de Origem forense: vinculacao opcional no Plugin (origin-binding.json, PDFBox report.pdf + Hashs.txt UTF-16LE, correlacao MD5/SHA256 com Lista de Arquivos.csv do caso IPED pai, verificacao N3 em copia temporaria do msgstore.db e registro honesto de lacunas pos-tar) e auditoria offline no Verificador 1.5.0 (--audit-origin / Auditoria de origem... com integridade retroativa preservada para capturas legadas).
- Compilação determinística dupla verificada (`REPRODUCIBLE_BUILD_VERIFIED=PASS`) e allowlist exclusiva sincronizada para Verificador **1.5.0**.

---
## v2.7.0 (Estável / `main`) / Verificador 1.4.5

- **Tag**: [v2.7.0](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/tag/v2.7.0) (Release estável compilada e publicada via `GiteaRunner` + `build-reproducible.ps1`)
- **Branch base**: `main`

### Downloads
- [instalador-chat-captura-IPED-2.7.0-verificador-1.4.5.zip](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.7.0/instalador-chat-captura-IPED-2.7.0-verificador-1.4.5.zip) (`94.409.121` bytes | SHA-256: `C9F70B8827C2259179B8C366A9184336C7BD8EF17376B0A8E0B9335149B7CC9E`)
- [integridade-verifier-1.4.5.exe](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.7.0/integridade-verifier-1.4.5.exe) (`77.616.218` bytes | SHA-256: `AF1C41C080A67B206870D17FF89362F959404C13A3F4D43E426BE840C1F240A3`)
- [integridade-verifier-1.4.5.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.7.0/integridade-verifier-1.4.5.jar) (`159.164` bytes | SHA-256: `E013F31E2EECB8517062854CED2DBCAACD5CFFED8CECB5E9DBE2B2F223E2588D`)
- [iped-viewers-impl-4.3.1-2.7.0.jar](http://127.0.0.1:3000/meuprojeto/chat-captura-IPED/releases/download/v2.7.0/iped-viewers-impl-4.3.1-2.7.0.jar) (`1.510.768` bytes | SHA-256: `BAFEB6350D170EB401E82292094E49BEA3CAFB9009F86F6B575DF585A90DAAA6`)

### Otimizado, Corrigido e Incluído
- **Build Reprodutível Auditável (`build-reproducible.ps1`)**: compilador multiplataforma (Windows, Linux e macOS) com timestamps UTC fixos (`1980-02-01T00:00:00Z`), ordenação lexicográfica de entradas (`ReproducibleZipPacker`), manifesto fixo, toolchain pinável (Temurin JDK `21.0.13+9` + OpenJFX `21.0.5` + IPED `4.3.1` + `.NET 10.0.15` determinístico) e prova dupla automática (`-VerifyReproducibility` -> `REPRODUCIBLE_BUILD_VERIFIED=PASS`).
- **Esteira e Publicação Direta no Gitea (`GiteaRunner`)**: workflow `.gitea/workflows/release.yml` recompila do código-fonte no runner local, comprova bit a bit que todos os SHA-256 coincidem com `VerifierIntegrity.java`, `instalar_captura_chat_zap.bat` e `CHECKSUMS.md`, executa a suíte completa de testes e publica a release `v2.7.0` com os 4 ativos verificados.
- **Verificador `1.4.5` & Plugin `2.7.0`**: mantém integralmente todo o endurecimento de segurança da `v2.6.9` / `1.4.4` (scanner HTML léxico F-01, `openFile` com `NOFOLLOW_LINKS`, `safeRegularFileSize`, `html.disable` Swing, limpeza determinística `dispose()` + `DISPOSE_ON_CLOSE`) com allowlist exclusiva em `VerifierIntegrity` e `instalar_captura_chat_zap.bat` sincronizada exclusivamente para os binários `1.4.5` (`E013F31E...` / `AF1C41C0...`), rejeitando `<= 1.4.4`.

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
