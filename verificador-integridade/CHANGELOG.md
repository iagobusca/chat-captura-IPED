# Changelog

Mudanças relevantes do Verificador de Integridade.

## [Não Lançado] - Em desenvolvimento

## [1.5.7] - 2026-11-18

### Corte Automatizado (v2.8.9)
- Auditoria N3 (v2.8.9 / Verificador 1.5.7): corrigidos os 5 falsos positivos de divergenciasN3 em capturas autenticas do IPED (seletor CSS .from em div.incoming.from que igualava sender ao texto da bolha, extracao canonica do HTML da bolha decodificando emojis <img class=eHEX> para codepoints Unicode, remocao balanceada de citacoes quoteFrom/quoteTo e rotulo Encaminhada, suporte a message_type do SQLite para midias/audios/figurinhas/chamadas com text_data NULL e revalidacao SQLite N3 independente no Verificador standalone com jlink java.sql/java.logging e descoberta dinamica de sqlite-jdbc), preservando todas as garantias fail-closed da v2.8.8. (`build-reproducible.ps1`; JAR `CC528C44...` / EXE `52D3CC60...`).
## [1.5.6] - 2026-11-18

### Corte Automatizado (v2.8.8)
- Auditoria de seguranca e solidez forense N3: match canonico NFC + SHA-256 (sem substring/curinga/bypass apos data ISO), rechecagem SQLite independente em copia temporaria (sem mutacao -wal/-shm na origem), exigencia de SHA-256 (MD5 somente informativo), fail-closed sem Hashs.txt/ancoras e limites nos walks. (`build-reproducible.ps1`; JAR `A027A301...` / EXE `5EF6B9AC...`).
## [1.5.5] - 2026-11-17

### Corte Automatizado (v2.8.7)
- Verificador 1.5.5 nunca cria arquivos *.bak (e remove eventuais sobras .integridade.html.*.bak de versões anteriores), compara o integridade.html existente com o resultado da verificação para preservar o arquivo se idêntico ou editá-lo/substituí-lo se diferente, e limpa o atributo somente leitura antes de atualizar relatórios. (`build-reproducible.ps1`; JAR `827B4580...` / EXE `639CE7B5...`).
## [1.5.4] - 2026-11-17

### Corte Automatizado (v2.8.5)
- Suporte a validação de historico-capturas.txt de versões anteriores no modo Integridade Público e em integridade.html: mantém exigência estrita para correspondência integral e valida por conteúdo criptográfico todas as mensagens (ARQUIVO|mensagem:*) e anexos (ARQUIVO|attachments/*) com detalhamento por captura, além de aceitar carregamento direto de arquivo .txt e hash de conjunto (SHA256|). (`build-reproducible.ps1`; JAR `8180566A...` / EXE `959424CF...`).
## [1.5.3] - 2026-11-17

### Corte Automatizado (v2.8.4)
- Removida a geração desnecessária de backups .bak temporários em atomicBatchStreaming do Verificador de Integridade (que causavam falha de acesso/bloqueio em Windows nos modos local, público e auditoria) e evitada a gravação antecipada de relatórios ao abrir Auditoria de Origem; adicionadas tentativas breves em bloqueios transitórios de arquivo. (`build-reproducible.ps1`; JAR `39BE8D3C...` / EXE `B2A3D1CD...`).
## [1.5.2] - 2026-11-17

### Corte Automatizado (v2.8.2)
- Verificador 1.5.2: botao Auditoria de origem... exclusivo na tela inicial (removido da janela Resultado); Validar Auditoria passa a exibir pre-conferencia das pastas (Pasta de origem obrigatoria Hashs.txt/Hashs.avilla/report.pdf/msgstore.db + Pasta do caso opcional com dica de subpasta) habilitando Comecar Auditoria ou Fechar; janela de carregamento com progresso por etapa (1/9..9/9), tempo decorrido e Cancelar. (`build-reproducible.ps1`; JAR `22C207E8...` / EXE `F3582CEF...`).
## [1.5.1] - 2026-11-17

### Corte Automatizado (v2.8.1)
- Verificador 1.5.1: botao Auditoria de origem... adicionado na tela inicial ao lado de Integridade Local/Publico com carregamento identico ao modo Publico, painel interativo (Pasta de origem, Pasta do caso opcional, Validar Auditoria com banner verde/vermelho, caixa de leitura/copia integral) e botao Exportar Auditoria para gerar Auditoria_relatorio.htm offline. (`build-reproducible.ps1`; JAR `9809DEC2...` / EXE `E03DEA3B...`).
## [1.5.0] - 2026-11-17

### Corte Automatizado (v2.8.0)
- Auditoria de Origem forense: vinculacao opcional no Plugin (origin-binding.json, PDFBox report.pdf + Hashs.txt UTF-16LE, correlacao MD5/SHA256 com Lista de Arquivos.csv do caso IPED pai, verificacao N3 em copia temporaria do msgstore.db e registro honesto de lacunas pos-tar) e auditoria offline no Verificador 1.5.0 (--audit-origin / Auditoria de origem... com integridade retroativa preservada para capturas legadas). (`build-reproducible.ps1`; JAR `194C1D9B...` / EXE `20F823BF...`).
## [1.4.5] - 2026-11-16

### Build Reprodutível Auditável (`v2.7.0`)
- Compilação e empacotamento determinísticos por [`../build-reproducible.ps1`](../build-reproducible.ps1) (`ReproducibleZipPacker` com timestamps UTC `1980-02-01T00:00:00Z` e ordem lexicográfica fixa; Temurin JDK `21.0.13+9` `--release 8` para `integridade-verifier.jar`; `jlink`/`jpackage` + `payload.zip` determinístico + `.NET 10.0.15` com `-p:Deterministic=true -p:ContinuousIntegrationBuild=true` no launcher Windows x64), com validação bit a bit dupla no `GiteaRunner`.

## [1.4.4] - 2026-11-16

### Segurança e Corrigido

- Substituição das regexes de varredura HTML (`HTML_TAG_PATTERN`, `HTML_META_TAG_PATTERN`, `HTML_SCRIPT_ELEMENT_PATTERN`, `HTML_STYLE_ELEMENT_PATTERN`) por scanner léxico determinístico por máquina de estados (`scanHtmlElements` / `parseHtmlTagStrict`), bloqueando bypasses com aspas/apóstrofos isolados em atributos sem aspas (`F-01` Fase 4).
- Endurecimento de `openFile` com `LinkOption.NOFOLLOW_LINKS`, verificação de arquivo regular e limite máximo de leitura (`maxBytes`), além de remoção de código morto vulnerável (`isAllowedPath` não utilizado).
- Sincronização de `safeRegularFileSize` sem janela de corrida TOCTOU de symlinks, allowlists HTML/MIME simétricas com o plugin, `html.disable` em todos os rótulos/caixas Swing, `MAX_SCAN_ENTRIES` e build Windows x64 com `.NET 10.0.15` (0 CVEs) e proteção anti-reparse/zip-bomb no launcher.

## [1.4.3] - 2026-11-14

### Corrigido e Melhorado

- Remoção do limite artificial de tamanho estimado de relatório (`enforceReportBudget` / `4.194.304` caracteres), do limite de `16 MiB` na gravação de relatórios (`atomic` / `atomicBatch`) e dos tetos cumulativos entre capturas em `verifyAllBounded` e `--verify-only`, permitindo verificar pastas com grandes conjuntos de capturas ou chats completos sem erro de orçamento.

## [1.4.2] - 2026-11-14

### Segurança e Corrigido

- `F-01`: `verifyHistory` passa a rejeitar inventário `artifacts` vazio ou com caminhos duplicados (case-insensitive) e confirma que `contentRootHash` do último evento corresponde ao `contentRootHash` atual; capturas `html-message-v1` com `integridade.json` exigem inventário completo dos arquivos esperados.
- `F-02`: `verifyMessageCapture` falha de forma fail-closed ao detectar divergência de `captureRootHash` no manifesto; `--verify-only` valida igualdade exata de status locais consistentes.
- `F-03`: `writeReports` e os contadores HTML passam a considerar íntegros todos os estados saudáveis verificados em capturas `html-message-v1` e reconciliação (`isHealthyVerifierItemState`).
- `F-04`/`F-05`: aplica `html.disable` nos `JLabel`/`JCheckBox` da GUI e rejeita comentários HTML, `<![CDATA[` e instruções `<?` em `validateRenderedCaptureHtml` (versões de apresentação `0` e `2`).
- `F-07`/`F-08`: `comparePublicMessageManifest` normaliza chaves em minúsculas e verifica correspondência entre `domId`, `key` e `messageHash`; capturas `html-message-v1` rejeitam `integridade.json` com `schemaVersion < 2`.
- `F-09`/`F-06`/`F-10`: `verifierIcon()` carrega somente recursos do classpath empacotado (ignora diretório de trabalho); launcher EXE protege limpeza temporária com tolerância de 2 minutos sem lock, exclusão segura contra junctions/reparse points e autoteste ZIP com entrada de 0 bytes no `--self-test`.

## [1.4.1] - 2026-11-13

### Segurança e Corrigido

- Endurecimento na verificação de capturas legadas (não `html-message-v1`): arquivos extras fora do conjunto esperado passam a reprovar a captura tanto na verificação padrão quanto em `--verify-only` (fail-closed).
- Bloqueio de diretório raiz caso seja NTFS junction ou link simbólico na CLI, na GUI e em `discover()` (`LinkOption.NOFOLLOW_LINKS` / `isLinkOrReparse`).
- Relatório `integridade.html` protegido com `<meta http-equiv="Content-Security-Policy">` baseado em hash SHA-256 (`sha256Base64`), listeners DOM (`addEventListener`) no lugar de `onclick=` inline, escape de apóstrofo (`&#39;`) e serialização segura.
- Cancelamento da busca inicial (`CancellationException`) passa a encerrar o processo silenciosamente com código `0`.

## [1.4.0] - 2026-11-13

### Melhorado e Corrigido

- Fluxo GUI reorganizado (`Opção A`): no modo **Integridade Local**, exibe janela de carregamento (`SwingWorker` + `JProgressBar`) ao clicar em **Confirmar** em **Selecione as capturas**; no modo **Integridade Público**, remove **Selecione as capturas** e executa carregamento único automático de todas as capturas antes de abrir direto **Integridade Público** (`Continuar para o resumo` mantido para abrir **Resultado**).
- Janela **Resultado** simplificada com remoção de **Hash geral do conjunto**, **SHA-256 do histórico completo** e botão **Copiar lista de hashes**.
- `integridade.html` reformulado: exibe **Hash geral Público**, aceita ambos os SHA-256 no campo de comparação superior, substitui a lista `captura|hash` por colagem de histórico completo (`historico-capturas.txt` normal ou anonimizado `CAPTURA|*-YYYYMMDD-HHMMSS`), remove o campo **Comparar hash desta captura** e exibe **OK** (verde) ou **FALHA** (vermelho) por captura de forma offline sem `crypto.subtle`.
- Correção global de `alwaysOnTop`, ícone, `toFront()` e execução na Swing EDT para **Selecione as capturas**, avisos, erros do `main` e `MessageBox` topmost no launcher C#.

## [1.3.7] - 2026-11-13

### Segurança e Compatibilidade

- `discover()` passa a ignorar pastas auxiliares de recaptura do plugin (`_backup_chat_capture_*`, `_backup_integridade_*`, `vinculado`, `.iped-reextract-trash*`, `.__capture_staging_*`, `.__old_*`, `.__reextract_tmp_*`, `.__reextract_failed_*`, `.__migration_html_backup*`).
- Endurecimento fase 2 na validação/sanitização de relatórios, manifestos e histórico (`--verify-only` mantendo compatibilidade total com capturas existentes).
- `build-single-exe.ps1`: `jlink` enxugado para os módulos `java.base,java.datatransfer,java.desktop,java.xml`.

## [1.3.6] - 2026-11-13

### Segurança

- Arquivos extras em capturas `html-message-v1` agora reprovam a verificação.
- Links simbólicos e junções NTFS/reparse points abaixo da raiz são rejeitados (`toRealPath` confinado).
- Validação do HTML por allowlist de tags e atributos, com regex ciente de aspas; `srcset`, `ping` e `background` deixam de passar.
- `historico-capturas.txt` e a lista pública de hashes escapam campos vindos do disco e validam SHA-256.

## [1.3.5] - 2026-11-12

### Adicionado

- Botão **Copiar histórico completo público** e campo **SHA-256 do histórico completo público** no diálogo de resumo final, exportando linhas no formato anonimizado `CAPTURA|*-YYYYMMDD-HHMMSS ...` (ou `CAPTURA|*` quando sem sufixo de timestamp) para ocultar nomes de pastas em publicações externas.
- Comparação dual no modo **Integridade Público**: aceita tanto o histórico completo original (`CAPTURA|<pasta>`) quanto o histórico completo público anonimizado (`CAPTURA|*-YYYYMMDD-HHMMSS`), além do hash SHA-256 de qualquer um dos dois formatos.
- Saída CLI `--export-history` passa a incluir `SHA-256_HISTORICO_PUBLICO=...`.
- Build EXE (`build-single-exe.ps1`) reforçado com `MSBUILDDISABLENODEREUSE=1`, `DOTNET_CLI_DO_NOT_USE_MSBUILD_SERVER=1`, `-m:1 -nodeReuse:false` e `dotnet build-server shutdown`.

### Herdado da 1.3.4 (rebuild v2.5.9-beta)

- Regras de validação de `domId`/`id` nos quatro pontos de checagem JSON/DOM aceitam registros de chamada (`^\d+_call:[0-9a-fA-F]+$`), carved/removidos (`^-?\d+_\d+$`) e timestamps internos.

## [1.3.4] - 2026-11-08

### Adicionado

- Validação da cadeia local de `integridade.json` também para capturas `html-message-v1`, que antes retornavam da verificação por mensagem sem ler o histórico.
- Verificação da proveniência de capturas migradas: `migration.json` precisa corresponder ao objeto `migration` do manifesto, e `legacy-annotations.json` precisa corresponder ao estado declarado em `legacyAnnotationsPreserved`.
- Conferência de que `migration.json` e as anotações preservadas estão vinculados ao inventário da cadeia local, com tamanho e SHA-256 iguais.

### Corrigido

- Uma alteração em `migration.json` é detectada mesmo quando o manifesto e o campo `migrationSha256` são atualizados junto, pois a referência é o inventário encadeado.
- Uma captura sem `migration` que contenha `migration.json` ou `legacy-annotations.json`, ou uma captura migrada sem a cadeia local, é rejeitada com erro explícito.

## [1.3.3] - 2026-11-07

### Corrigido

- O validador reconhece o novo script fixo de preview com restauração da rolagem e exige sua CSP SHA-256 correspondente.
- Capturas anteriores continuam aceitas somente com o par exato de script e CSP da versão 1.3.2.

## [1.3.2] - 2026-11-07

### Corrigido

- Capturas existentes com avatar PNG embutido declarado pelo IPED 4.3.1 como `data:image/jpg` agora passam na verificação quando a assinatura dos bytes confirma PNG. MIME desconhecido, SVG e conteúdo incompatível continuam rejeitados.
- Adicionado teste de regressão para o caso legado e para MIME/formato incompatíveis.

## [1.3.1] - 2026-11-07

### Corrigido

- Validação da apresentação HTML WhatsApp 2: CSS de tema local, CSP canônica e script fixo de parada de áudio/vídeo conferidos byte a byte.
- Preview de PDF permitido somente em iframe com `sandbox=""` e recurso inventariado.
- Validação de mídia sem autoplay, referências locais, fragmentos de modal e MIME dos recursos. Capturas legadas mantêm o caminho de verificação anterior.

## [1.3.0] - 2026-11-07

### Adicionado

- Verificação de `html-message-v1` por mensagem e por anexo, com hash canônico, hash-raiz e inventário de anexos locais.
- Comparação de manifesto público por chave e hash de mensagem, com estados distintos para mensagem alterada, ausente, nova ou ambígua.

### Segurança

- Rejeita IDs/chaves inválidos ou duplicados, metadados de anexo inseguros, caminhos fora da captura, links simbólicos, HTML ativo e dependências externas no novo formato.
- Confere referências locais do HTML com a lista do manifesto e valida assinaturas de imagem, áudio, vídeo, PDF, ZIP/OOXML, Office legado, RAR, 7z e texto/CSV; arquivos desconhecidos podem ser anexados somente como `.bin` com MIME `application/octet-stream`.

## [1.2.8] - 2026-11-06

### Segurança

- Substituído o parser regex do inventário por leitura JSON limitada a 16 MiB e profundidade 64; caminhos acima de 4.096 caracteres são rejeitados antes de resolução. Corrige o `StackOverflowError` reproduzido em caminhos de 1.536 caracteres.
- Validação de tipo/tamanho/hash, rejeição de caminhos e entradas duplicadas e limite global de 25.000 itens/32 MiB de metadados selecionados.
- Limite de 10.000 capturas descobertas e orçamento de 4 Mi caracteres estimados para relatórios; lotes acima do limite falham antes da escrita em vez de esgotar o heap.
- Os cinco relatórios são preparados em temporários, promovidos após a preparação completa e restaurados se uma promoção falhar.

### Corrigido

- Descoberta interativa em worker cancelável com contagem de capturas, mantendo a interface responsiva durante a enumeração.
- Corrigidos escaping JSON de nomes/caminhos e tratamento de falhas de verificação por limite.
- Atualizado o launcher EXE para .NET 10.0.14; o JAR continua compilado para Java 8.

## [1.2.7] - 2026-11-06

- JAR compilado com JDK 8, compatível com Java 8 (1.8) ou superior.
- Mantém o EXE Windows com runtime incluído e a interface paginada da versão 1.2.6.

## [1.2.6] - 2026-11-06

- Lista capturas em páginas de até 100 itens, com navegação e seleção persistente por identificador.
- `Marcar todas` mantém estado global compacto; edição manual preserva os IDs já selecionados.

## [1.2.5] - 2026-11-06

### Segurança

- Atualizada a biblioteca `org.json` embutida para 20260719, corrigindo o processamento custoso de números JSON longos (CVE-2026-59171, CWE-400/CWE-770).
- Entradas JSON limitadas a 16 MiB e capturas limitadas a 25.010 arquivos para encerrar entradas excessivas com erro controlado.
- Hash canônico calculado em fluxo para evitar acumular a concatenação completa na memória.
- Relatórios gravados com temporários exclusivos; diretórios com links simbólicos são rejeitados.

## [1.2.4] - 2026-11-06

### Corrigido

- Cópia de hashes e histórico para a área de transferência executada fora da thread da interface; as mensagens de confirmação ficam associadas à janela ativa.
- README atualizado com o tutorial visual, destaques dos botões e distinção entre as imagens do tutorial e o ícone do aplicativo.

## [1.2.3] - 2026-11-05

### Removido

- Botões "Sempre no topo: ON/OFF" e "Minimizar" das janelas principais.

### Modificado

- As janelas continuam fixas sobre as demais (always-on-top), sem botão para alternar esse comportamento.
- A minimização passa a usar apenas o botão nativo da barra de título do Windows.

## [1.2.2] - 2026-11-05

### Adicionado

- Botões "Sempre no topo: ON/OFF" e "Minimizar" em todos os diálogos principais.

### Modificado

- Diálogos principais convertidos para janelas `JFrame` nativas, com entrada na barra de tarefas; o botão "Minimizar" usa o estado nativo e o clique na barra de tarefas restaura a janela.
- Ícone do verificador embutido no JAR, garantindo a identificação das janelas mesmo quando o aplicativo é extraído para uma pasta temporária.
- Janela "Selecione as capturas" com tamanho adaptativo ao monitor (altura máxima = 65% da tela) e scroll vertical quando houver mais itens do que a área visível.
- Tamanho mínimo da janela definido em 640x640.
- Cada execução usa uma pasta temporária exclusiva (`instance-{PID}-{GUID}`); restos antigos só são apagados quando não há outra instância ativa.
- O JVM encerra em todos os caminhos de fechamento, permitindo que o lançador aguarde o término e remova a pasta temporária.
- O aviso "Nenhuma captura" também encerra o processo quando fechado pelo botão X.

### Corrigido

- Empacotamento de todas as classes internas no JAR, evitando `NoClassDefFoundError` ao abrir os diálogos.

## [1.2.1] - 2026-10-31

### Modificado

- Resumo final em blocos estreitos, com cada botão Copiar antes do hash e quebra de linha nos valores longos.
- Janela Integridade Público mais compacta.

## [1.2.0] - 2026-10-31

### Adicionado

- Tela inicial com opções explícitas **Integridade Local** e **Integridade Público**.
- Comparação pública aceita o hash SHA-256 do registro estável ou o texto completo historico-capturas.txt.
- Registro público versionado, determinístico, com inventário completo de caminhos/tamanhos/hashes esperado e encontrado, vinculado ao hash do integridade.json de cada captura; não inclui o horário volátil da execução.

### Corrigido

- O hash do histórico compartilhado não muda só por executar o verificador em outro horário.

## [1.1.0] - 2026-10-31

### Adicionado

- Pastas temporárias exclusivas por execução do EXE.
- Limpeza de restos conhecidos quando nenhuma outra instância do verificador está aberta.
- Validação do pacote extraído contra o manifesto SHA-256 embutido.

### Modificado

- O EXE permite execuções simultâneas sem reutilizar a mesma pasta `app`/`runtime`.
- Versão sincronizada entre o projeto .NET, o JAR e o empacotamento do EXE.
