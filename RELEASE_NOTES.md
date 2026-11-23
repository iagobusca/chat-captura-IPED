# Release Notes — Chat Captura IPED

Histórico consolidado das releases do plugin e do **Verificador de Integridade**.
Para hashes detalhados e versões anteriores, consulte [CHECKSUMS.md](CHECKSUMS.md), [versao_hash.md](versao_hash.md) e [CHANGELOG.md](CHANGELOG.md).

> **Nota de publicação:** As releases públicas oficiais no GitHub iniciam na estável **`v2.6.8`** (`v2.6.8`, `v2.6.9`, `v2.7.0`). As releases `<= 2.6.8-beta` abaixo documentam o histórico completo de desenvolvimento e auditoria no Gitea local.

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
