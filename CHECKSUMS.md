# Checksums

## Build 2.7.0 (Estável / `main`) / Verificador 1.4.5 — Build Determinístico Auditável

| Arquivo | Tamanho (bytes) | SHA-256 | Descrição |
| --- | ---: | --- | --- |
| `instalador-chat-captura-IPED-2.7.0-verificador-1.4.5.zip` | 94409121 | `C9F70B8827C2259179B8C366A9184336C7BD8EF17376B0A8E0B9335149B7CC9E` | Pacote completo de instalação estável compilado via `build-reproducible.ps1` (timestamps UTC fixos `1980-02-01T00:00:00Z`, ordem lexicográfica; plugin `2.7.0`, Verificador `1.4.5` JAR+EXE com `.NET 10.0.15`, `json-20260719`, `validate-plugin-jar.ps1` e script `.bat`) |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.7.0.jar` | 1510768 | `BAFEB6350D170EB401E82292094E49BEA3CAFB9009F86F6B575DF585A90DAAA6` | Fat-JAR determinístico do plugin (`Implementation-Version: 2.7.0`, `--release 11`, Temurin JDK 21.0.13+9 + OpenJFX 21.0.5, `MultiViewer` + `org/opencv/**` embutidos); mesmo binário de `iped-viewers-impl-4.3.1.jar` |
| `verificador-integridade/integridade-verifier.jar` | 159164 | `E013F31E2EECB8517062854CED2DBCAACD5CFFED8CECB5E9DBE2B2F223E2588D` | Verificador `1.4.5` JAR determinístico (`--release 8`, bytecode 52, Temurin JDK 21.0.13+9, empacotado por `ReproducibleZipPacker`) |
| `verificador-integridade/integridade-verifier.exe` | 77616218 | `AF1C41C080A67B206870D17FF89362F959404C13A3F4D43E426BE840C1F240A3` | Executável Windows x64 determinístico do Verificador `1.4.5` (`jlink`/`jpackage` Temurin 21.0.13+9 + `payload.zip` determinístico + `.NET 10.0.15` com `-p:Deterministic=true -p:ContinuousIntegrationBuild=true`, `--self-test` OK) |

## Build 2.6.9 (Estável / `main`) / Verificador 1.4.4

| Arquivo | Tamanho (bytes) | SHA-256 | Observações |
|---|---|---|---|
| `instalador-chat-captura-IPED-2.6.9-verificador-1.4.4.zip` | 95520077 | `7AE66926EF857C35EF73B6192B54EAD7D2E8751E8D984D89CDD5DBAC144A6ABC` | Pacote completo de instalação estável (plugin `2.6.9`, Verificador `1.4.4` JAR+EXE com runtime `.NET 10.0.15`, `json-20260719`, `validate-plugin-jar.ps1`, sincronização segura de localização e script `.bat` sem duplicação de JARs em `lib\`) |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.9.jar` | 1513268 | `725CA7E02538A5B5EEBB043A29868E90515CBCAC7BFBA4CC518A7EEF55150B9C` | Fat-JAR do plugin (`Implementation-Version: 2.6.9`; compatível com API do IPED `4.3.1` oficial, defesa em profundidade Swing `html.disable`, limpeza `dispose()` + `DISPOSE_ON_CLOSE`, `MultiViewer` + `org/opencv/**` embutidos); mesmo binário de `iped-viewers-impl-4.3.1.jar` |
| `verificador-integridade/integridade-verifier.jar` | 159189 | `C37D52F2D12A9AB0E954744F84B3E47805D9F3BF8B460A864C70AC06AE4BF7FF` | Verificador `1.4.4` compilado em Java 8 (scanner HTML léxico por máquina de estados, guarda `openFile` com `NOFOLLOW_LINKS` + `maxBytes`, allowlists HTML/MIME simétricas, `safeRegularFileSize`, `html.disable`, `MAX_SCAN_ENTRIES` e sem tetos artificiais de relatório) |
| `verificador-integridade/integridade-verifier.exe` | 77874266 | `A57CB7E8C15B256DDBDE99899157D8CC2B8AA8B3ABCD8F30D7CACE17F2962291` | Executável Windows x64 autocontido do Verificador `1.4.4` (Java 11 + runtime `.NET 10.0.15` 0 CVEs, anti-reparse/zip-bomb no launcher, `--self-test` OK) |

## Build 2.6.8 (Estável / `main`) / Verificador 1.4.3

| Arquivo | Tamanho (bytes) | SHA-256 | Observações |
|---|---|---|---|
| `instalador-chat-captura-IPED-2.6.8-verificador-1.4.3.zip` | 94677720 | `FBD4657A55686A32252C5CF81BBD35ABD1E06C7A44639142080F21ED4BDF5AF7` | Pacote completo de instalação estável (plugin `2.6.8`, Verificador `1.4.3` JAR+EXE com runtime `.NET 10.0.15`, `json-20260719`, `validate-plugin-jar.ps1` e script `.bat` sem duplicação de JARs em `lib\`) |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 1513265 | `E817A1743BCB50DCF36026769C156E6ACC2796A9446E435DBFAFC501C2B9D058` | Fat-JAR do plugin (`Implementation-Version: 2.6.8`; compatível com API do IPED `4.3.1` oficial, `MultiViewer` + `org/opencv/**` embutidos) |
| `verificador-integridade/integridade-verifier.jar` | 159189 | `104F69AABCB86851C2DA905AD6E8428177BDEC1E6D3750580291A8ECD008B382` | Verificador `1.4.3` compilado em Java 8 (validação HTML léxica por máquina de estados, guardas `openFile` com `NOFOLLOW_LINKS`, allowlists HTML/MIME simétricas, `MAX_SCAN_ENTRIES` e sem tetos artificiais de relatório) |
| `verificador-integridade/integridade-verifier.exe` | 77874266 | `17D8F5DCED38029B68849AF11A2358B38B31FD4A01BDAF4BFEA514FC27842E05` | Executável Windows x64 autocontido do Verificador `1.4.3` (runtime `.NET 10.0.15` 0 CVEs, anti-reparse/zip-bomb no launcher, `--self-test` OK) |

## Build 2.6.8-beta / Verificador 1.4.3 (tolerância a anexos > 8 MiB e remoção do limite de tamanho de relatório; allowlist exclusiva 1.4.3)

| Arquivo | Tamanho (bytes) | SHA-256 | Observações |
|---|---|---|---|
| `instalador-chat-captura-IPED-2.6.8-beta-verificador-1.4.3.zip` | 96948771 | `5593E3EA0701C39806A976273A4C14750BBFF8C204DECD50B8C39DEBAFB9CE81` | Pacote completo de instalação (plugin `2.6.8-beta`, Verificador `1.4.3` JAR+EXE, dependências e script BAT) |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.8-beta.jar` | 1506195 | `8BD552C41AFD9A3D63A57F06A21FB0AD4A8ECA016FAD1F3A3CADABF3272CFFB3` | Fat-JAR do plugin (`Implementation-Version: 2.6.8-beta`); mesmo binário de `iped-viewers-impl-4.3.1.jar` |
| `verificador-integridade/integridade-verifier.jar` | 154638 | `8CB04812B4BD68C0DB9959DECEB63C9F7D8F8D99CF4BD5B4DC56A9148B305A1D` | Verificador `1.4.3` compilado em Java 8 (sem limite de tamanho de relatório e sem tetos cumulativos entre capturas) |
| `verificador-integridade/integridade-verifier.exe` | 77867588 | `723DD7507D44AB14E3FFA723D2838CFC0C3F8B77B53725919A2E1CBCBA145581` | Executável Windows x64 autocontido do Verificador `1.4.3` (`--self-test` OK) |

## Build 2.6.7-beta / Verificador 1.4.2 (correções de segurança e robustez `F-01..F-10`; allowlist exclusiva 1.4.2)

| Arquivo | Tamanho (bytes) | SHA-256 | Observações |
|---|---|---|---|
| `instalador-chat-captura-IPED-2.6.7-beta-verificador-1.4.2.zip` | 96949777 | `6996BC8F01D514B586C5C5B0680EC548FBDAF348B609178E614E2CA018A286DB` | Pacote completo de instalação (plugin `2.6.7-beta`, Verificador `1.4.2` JAR+EXE, dependências e script BAT) |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.7-beta.jar` | 1516022 | `8B7E1432992C6DD41C41E805A2B3A492AF9E13D689EA49063B78C6AB580F7FDB` | Fat-JAR do plugin (`Implementation-Version: 2.6.7-beta`); mesmo binário de `iped-viewers-impl-4.3.1.jar` |
| `verificador-integridade/integridade-verifier.jar` | 155216 | `08877EFF5F2FA0A1E2F64AD0072490F9400AAA26E51ACD8E18C4D2DEE5F59475` | Verificador `1.4.2` compilado em Java 8 (`F-01..F-05`, `F-07..F-09`) |
| `verificador-integridade/integridade-verifier.exe` | 77868100 | `4859980DC532340427EEF2983B62EA4596BC6B8FA1D6F80462BB76CF03278105` | Executável Windows x64 autocontido do Verificador `1.4.2` (`F-06`, `F-10`; `--self-test` OK) |

## Build 2.6.6-beta / Verificador 1.4.1 (correções de segurança e robustez `V-01..V-04` e `P-01..P-04`; allowlist exclusiva 1.4.1)

| Arquivo | Tamanho (bytes) | SHA-256 | Observações |
|---|---|---|---|
| `instalador-chat-captura-IPED-2.6.6-beta-verificador-1.4.1.zip` | 96101446 | `A3739FF8AF94A438272777DB0BDDF858A9553A653BB4E46FE73389F7E925B135` | Pacote completo de instalação (plugin `2.6.6-beta`, Verificador `1.4.1` JAR+EXE, dependências e script BAT) |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.6-beta.jar` | 1515669 | `C0025DB803739F3C9509F4E24DDAC23C1C4E0D77CC751A5DE890102B1968E7A4` | Fat-JAR do plugin (`Implementation-Version: 2.6.6-beta`); mesmo binário de `iped-viewers-impl-4.3.1.jar` |
| `verificador-integridade/integridade-verifier.jar` | 153719 | `CC2958812B0D56CF60B96641EE75F5776F463E0986503AC0FE15ABF82CE71345` | Verificador `1.4.1` compilado em Java 8 |
| `verificador-integridade/integridade-verifier.exe` | 77864004 | `A7283005AF86ED5DD0E47C07619DCD1F3A6F4618B10DE3AC208B7F2A1549A29C` | Executável Windows x64 autocontido do Verificador `1.4.1` |

## Build 2.6.5-beta / Verificador 1.4.0 (fluxo direto no modo Público, barras de progresso, `alwaysOnTop`/EDT e `OK`/`FALHA` no `integridade.html`; allowlist exclusiva 1.4.0)

| Arquivo | Tamanho (bytes) | SHA-256 | Observações |
|---|---|---|---|
| `instalador-chat-captura-IPED-2.6.5-beta-verificador-1.4.0.zip` | 96095058 | `237BF525B55DEE59AD9C7534E19D7C2B0F6583047C70CA9606034ED97CFF7BF7` | Pacote completo de instalação (plugin `2.6.5-beta`, Verificador `1.4.0` JAR+EXE, dependências e script BAT) |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.5-beta.jar` | 1513355 | `32044F09D1299D787061B76F2150DF0ED82590C5C01E9123377005DE8407170B` | Fat-JAR do plugin (`Implementation-Version: 2.6.5-beta`); mesmo binário de `iped-viewers-impl-4.3.1.jar` |
| `verificador-integridade/integridade-verifier.jar` | 152916 | `9CA17D7BA2EC50808D333495716D277F4DA963BBBC946C3EF7F765079F48A6E5` | Verificador `1.4.0` compilado em Java 8 (`release-assets/integridade-verifier-1.4.0.jar`) |
| `verificador-integridade/integridade-verifier.exe` | 77862980 | `11094FA3CCE812AF3421490FF1BC2F3B8100C9B5D7551DBBA7F05F78C8136809` | Executável Windows x64 autocontido do Verificador `1.4.0` (`release-assets/integridade-verifier-1.4.0.exe`) |

## Build 2.6.4-beta / Verificador 1.3.9 (compatibilidade CSP com entidades HTML em atributos; allowlist exclusiva 1.3.9)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.4-beta.jar` | 1.513.352 | `5C69E3548B937DE8FF8085F91C91D07B38E7F5657F415E41F516E1B55302B817` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 1.513.352 | `5C69E3548B937DE8FF8085F91C91D07B38E7F5657F415E41F516E1B55302B817` |
| `instalador-chat-captura-IPED-2.6.4-beta-verificador-1.3.9.zip` | 94.640.895 | `1EC278138D3FC4C47003CC9C24B5E731619B32BA806B10B173A4E8205CC03CC8` |
| `verificador-integridade/integridade-verifier.jar` | 142.687 | `043AFEC21D79B4DF3C8A3DDB42F49CF5CB9467CECD11397E724091DFA310E2A7` |
| `verificador-integridade/integridade-verifier.exe` | 77.853.252 | `FF06525056E008A91AABF49D9B98B3E0CEE55CCED3846A89AA76D7B39BC6BC85` |

## Build 2.6.3-beta / Verificador 1.3.8 (correções de segurança e integridade — Pacotes A–E; allowlist exclusiva 1.3.8)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.3-beta.jar` | 1.509.222 | `CED5CCFE4C5F1C8A15C52D32E891468B9836F620BB3E3FFBC47198FE41C3AC60` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 1.509.222 | `CED5CCFE4C5F1C8A15C52D32E891468B9836F620BB3E3FFBC47198FE41C3AC60` |
| `instalador-chat-captura-IPED-2.6.3-beta-verificador-1.3.8.zip` | 94.635.426 | `F17137E71F5972556A2953A771FB724BDECAB28BFBE63BD75F7DD73E3639444E` |
| `verificador-integridade/integridade-verifier.jar` | 142.154 | `556B1A652183A343B168057029CCF10006361D8CB53526BA610E81A4D76532B6` |
| `verificador-integridade/integridade-verifier.exe` | 77.852.740 | `B9D77A7F88F7D87D3697B855191427C3CE790EFA827830DEABE263E4E2962DD2` |

## Build 2.6.2-beta / Verificador 1.3.7 (correções de segurança fase 2 — P1–P11)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.2-beta.jar` | 1.496.622 | `5576E9E5CE0BCB6D3C65ECF560403CC0098F0D4A1F8FE18DBC6597238DB9C8AC` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 1.496.622 | `5576E9E5CE0BCB6D3C65ECF560403CC0098F0D4A1F8FE18DBC6597238DB9C8AC` |
| `instalador-chat-captura-IPED-2.6.2-beta-verificador-1.3.7.zip` | 95.471.318 | `7E8C342F285C55F418A4195B61584AB3F7354F64FEDE61C19CEBD75BB9AA1854` |
| `verificador-integridade/integridade-verifier.jar` | 139.632 | `7A137AE3286231246AF335380C3E8716B17A6696552096474563169DB4F5F2EE` |
| `verificador-integridade/integridade-verifier.exe` | 77.850.180 | `D102E17D02F39E5D6E85249E9B9585BC26C3D005E7DBFF2296C22FB2E18E926A` |

## Build 2.6.1-beta / Verificador 1.3.6 (correções de segurança T1–T10)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.1-beta.jar` | 1.490.429 | `406238754C5951AA8BA2EAC2E3E0E44D96C7C29F9AF6C4A83AC34F18F6AC9F4B` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 1.490.429 | `406238754C5951AA8BA2EAC2E3E0E44D96C7C29F9AF6C4A83AC34F18F6AC9F4B` |
| `instalador-chat-captura-IPED-2.6.1-beta-verificador-1.3.6.zip` | 94.604.780 | `9D6087BE80B9BE1A329C81FC4D7B8B10CB6FF10EE8098CF2ECEACA214336DE5C` |
| `verificador-integridade/integridade-verifier.jar` | 137.188 | `F7990400A98445B8FA718626DA08145CD974866487650BD443E95427DD015FAC` |
| `verificador-integridade/integridade-verifier.exe` | 77.843.012 | `24E84B1B014D9677E238A94FD9EBFFB5CD2E8CF5CCA244AF8D47D1ED48C704D4` |

## Build 2.6.0-beta / Verificador 1.3.5 (histórico completo público anonimizado e verificação dual)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.6.0-beta.jar` | 1.482.427 | `DFC47C2F050837A7A334E06615D731252E75FDF674F80CEF9AD5EFDD28F24936` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 1.482.427 | `DFC47C2F050837A7A334E06615D731252E75FDF674F80CEF9AD5EFDD28F24936` |
| `instalador-chat-captura-IPED-2.6.0-beta-verificador-1.3.5.zip` | 95.427.705 | `2F67E5EA2D7172D3097DF1872A084F6220D14C2AB4BCE7F276E355F7AC9884F4` |
| `verificador-integridade/integridade-verifier.jar` | 133.282 | `31B0CE725708ED53AD9BE319FFE51439940C65BB46EF8593AB78376C07BF46B9` |
| `verificador-integridade/integridade-verifier.exe` | 77.839.428 | `75FB542BCB53C44C7B17553EEC761C16F17C670F8033DD3F901F80A0897AC42B` |

## Build 2.5.9-beta / Verificador 1.3.4 (registros de chamada, carved/removidos, timestamp em linha e fat-JAR OpenCV)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.5.9-beta.jar` | 1.472.202 | `D881B345A9ACC4F54219867870959EA659FF7C91551458A2DA6192AEAAB35507` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 1.472.202 | `D881B345A9ACC4F54219867870959EA659FF7C91551458A2DA6192AEAAB35507` |
| `instalador-chat-captura-IPED-2.5.9-beta-verificador-1.3.4.zip` | 94.584.283 | `26014CE5951BE89E37CA2057B25DB359A64CECB65A9547A5CD192849FC4E7A10` |
| `verificador-integridade/integridade-verifier.jar` | 132.375 | `B456074C393949FE63DC33B404F539B569830CE92FC657FF43B622D9E03500DD` |
| `verificador-integridade/integridade-verifier.exe` | 77.838.404 | `20FAF8A5AD2690A909F09B6A60DFBFE12A271493DA3331595F8BB2A95EF2BCA0` |

## Build 2.5.8-beta / Verificador 1.3.4 (censura de imagens sensíveis por CSS em `html-message-v1`)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.5.8-beta.jar` | 646.622 | `2D5FB8EE5312E9E1F99707272E833FDCDCBD8994F82D97E3E1D95A7DF3C90EA6` |
| `instalador-chat-captura-IPED-2.5.8-beta-verificador-1.3.4.zip` | 72.933.991 | `49ACB11EE33689304072176712B0FAEC775E3CBA76693C3D49F194DCFAEF030F` |
| `verificador-integridade/integridade-verifier.jar` | 132.353 | `241FD904C02F65E19EE3D663C24A9AA15B685E8A43DD7F545AD5D07133BC79DB` |
| `verificador-integridade/integridade-verifier.exe` | 77.838.404 | `E4137082FE2A025DD406891A82313B2094A6C15A2F6D2F0804E2BE342ED57C38` |

## Build 2.5.7-beta / Verificador 1.3.4 (arquivamento dos relatórios da raiz e índice de anotações)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.5.7-beta.jar` | 640.743 | `150D1036134291F84E6FF6D90214271CA314EE4B0040BFF13947F0B2D881A135` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 640.743 | `150D1036134291F84E6FF6D90214271CA314EE4B0040BFF13947F0B2D881A135` |
| `instalador-chat-captura-IPED-2.5.7-beta-verificador-1.3.4.zip` | 72.930.196 | `43C57F37002A55BBFF417E5B01B1712DC3E41FD4091C2626FCA8DF6A06C27BA5` |
| `verificador-integridade/integridade-verifier.jar` | 133.702 | `034DC3369C7AC00C80D6F2034745E5B1242B2DD2055C9DD583B97C6DD041CB44` |
| `verificador-integridade/integridade-verifier.exe` | 77.839.428 | `FD136842AE223C3AE8E09E1C1670884249390D37CDB763E1D247CBC3462A4FDE` |

## Build 2.5.6-beta / Verificador 1.3.4 (reset em capturas novas e janela ajustável)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.5.6-beta.jar` | 634.994 | `9A34DB5F07B0AD279887C9411D1629D2793E03EA92D746F6113CF971B1221DD7` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 634.994 | `9A34DB5F07B0AD279887C9411D1629D2793E03EA92D746F6113CF971B1221DD7` |
| `instalador-chat-captura-IPED-2.5.6-beta-verificador-1.3.4.zip` | 72.929.269 | `5195CB02265A1BF1D07024C9262CCF9D7FA598951A5E09080E1DE8A26787E4BB` |
| `verificador-integridade/integridade-verifier.jar` | 133.702 | `034DC3369C7AC00C80D6F2034745E5B1242B2DD2055C9DD583B97C6DD041CB44` |
| `verificador-integridade/integridade-verifier.exe` | 77.839.428 | `FD136842AE223C3AE8E09E1C1670884249390D37CDB763E1D247CBC3462A4FDE` |

## Build 2.5.5-beta / Verificador 1.3.4 (migração de capturas legadas para HTML por mensagem)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.5.5-beta.jar` | 639.173 | `A2210944559C628A0893B200B89AFF4B4527FDB8A9A014488CF60E214E66A24F` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 639.173 | `A2210944559C628A0893B200B89AFF4B4527FDB8A9A014488CF60E214E66A24F` |
| `instalador-chat-captura-IPED-2.5.5-beta-verificador-1.3.4.zip` | 72.897.251 | `36A1FB08DF7F3F00BA8B760A92A2992EBDC96347EDDC4CF8923F0E725B6E40B8` |
| `verificador-integridade/integridade-verifier.jar` | 133.702 | `034DC3369C7AC00C80D6F2034745E5B1242B2DD2055C9DD583B97C6DD041CB44` |
| `verificador-integridade/integridade-verifier.exe` | 77.839.428 | `FD136842AE223C3AE8E09E1C1670884249390D37CDB763E1D247CBC3462A4FDE` |

## Build 2.5.4-beta / Verificador 1.3.3 (restaura a rolagem ao fechar previews)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.5.4-beta.jar` | 625.971 | `6F23B94B0CA0E054CC7838D41E10ABA519364ECAE27D49C30F6F0BCAD06657CE` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 625.971 | `6F23B94B0CA0E054CC7838D41E10ABA519364ECAE27D49C30F6F0BCAD06657CE` |
| `instalador-chat-captura-IPED-2.5.4-beta-verificador-1.3.3.zip` | 73.678.535 | `133DDB7DF5F843A56D25483A59DB6DFE933A8F205B0670C62DDBAFEEC0DFDE87` |
| `verificador-integridade/integridade-verifier.jar` | 131.972 | `22FBAA2A8D4EA32940B4555E95207A22982B7FCDD308020950A667853420D921` |
| `verificador-integridade/integridade-verifier.exe` | 78.384.708 | `933F27A61AEDDAC5E4F1D8D7CA7AFC057AA2F04062A9267783FC56BB00DCE25B` |

## Build 2.5.3-beta / Verificador 1.3.2 (compatibilidade de MIME da imagem embutida)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.5.3-beta.jar` | 626.302 | `B8E73D89C7F19AD4B77B0FF3DF6F7A39C53CF5AD4745DB731E279FD88D819AC9` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 626.302 | `B8E73D89C7F19AD4B77B0FF3DF6F7A39C53CF5AD4745DB731E279FD88D819AC9` |
| `instalador-chat-captura-IPED-2.5.3-beta-verificador-1.3.2.zip` | 73.677.332 | `44F8CC6EFD3E9BBF6794A46E4BC576ECB8D4F7E078DB4ACEA469A1D236545A99` |
| `verificador-integridade/integridade-verifier.jar` | 131.464 | `F6D21DC4DAAFBF0BB241B66790EE2FE137AF5DBD5609EF9CED8D1336D622A7A7` |
| `verificador-integridade/integridade-verifier.exe` | 78.384.196 | `94FC180EA3D25DB9881F3D245FE1D2100CC21B6263F6163E129B5033B55D5908` |

## Build 2.5.2-beta / Verificador 1.3.1 (correção dos marcadores de captura)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.5.2-beta.jar` | 629.590 | `884C22025AF90353CC8271CCBC84CD37EF771D1DDE47C094380B95C29E9E0CF1` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 629.590 | `884C22025AF90353CC8271CCBC84CD37EF771D1DDE47C094380B95C29E9E0CF1` |
| `instalador-chat-captura-IPED-2.5.2-beta-verificador-1.3.1.zip` | 73.676.554 | `5191A42B2260E4FF4EFEC56EDEF33C15C9A55C3C97B04C1CB96E51521CC77822` |
| `verificador-integridade/integridade-verifier.jar` | 131.441 | `0C262CC3D2F8BB0FD6CF7FB3E4071CF19903D684396356F56F9D92DB869F8339` |
| `verificador-integridade/integridade-verifier.exe` | 78.384.196 | `AE1E2E1E9D7E78BBCF8C551C2209F7F990C8AC37C77F751556BB29D698D719F5` |

## Build 2.5.1-beta / Verificador 1.3.1 (correção de captura)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.5.1-beta.jar` | 617.730 | `C3257814268EFCA487946F77B38A0F8C5D1A279E4CFCC604DA0D89A822B76292` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 617.730 | `C3257814268EFCA487946F77B38A0F8C5D1A279E4CFCC604DA0D89A822B76292` |
| `instalador-chat-captura-IPED-2.5.1-beta-verificador-1.3.1.zip` | 73.669.095 | `3088D0C67D495B946CD76272F7FD17AC851EC0416EFA304E2037A91E9D683821` |
| `verificador-integridade/integridade-verifier.jar` | 131.441 | `0C262CC3D2F8BB0FD6CF7FB3E4071CF19903D684396356F56F9D92DB869F8339` |
| `verificador-integridade/integridade-verifier.exe` | 78.384.196 | `AE1E2E1E9D7E78BBCF8C551C2209F7F990C8AC37C77F751556BB29D698D719F5` |

## Build 2.5.0-beta / Verificador 1.3.1 (pacote local para teste)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.5.0-beta.jar` | 622.142 | `E8248F56072FBC41B5B259F118605DB7DE357CB14BF583B4BE98446B5059F575` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 622.142 | `E8248F56072FBC41B5B259F118605DB7DE357CB14BF583B4BE98446B5059F575` |
| `instalador-chat-captura-IPED-2.5.0-beta-verificador-1.3.1.zip` | 73.668.564 | `ECA259CF8C27122FD9AC605425ECF4EFA77551091EAEE5570721623F79780B0E` |
| `verificador-integridade/integridade-verifier.jar` | 131.441 | `0C262CC3D2F8BB0FD6CF7FB3E4071CF19903D684396356F56F9D92DB869F8339` |
| `verificador-integridade/integridade-verifier.exe` | 78.384.196 | `AE1E2E1E9D7E78BBCF8C551C2209F7F990C8AC37C77F751556BB29D698D719F5` |

## Build 2.4.1-beta / Verificador 1.3.0 (pacote local para teste)

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.4.1-beta.jar` | 612.886 | `2BEADACF04C36ACA1E804B51B360D4336B4B1C36448FC4AC8D35420BF072B737` |
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar` | 612.886 | `2BEADACF04C36ACA1E804B51B360D4336B4B1C36448FC4AC8D35420BF072B737` |
| `instalador-chat-captura-IPED-2.4.1-beta-verificador-1.3.0.zip` | 73.652.975 | `B4311834AA52C6BE1BC672F97C62A08D1DDE71534B04D9C75279CCBC53DB7B75` |
| `verificador-integridade/integridade-verifier.jar` | 126.570 | `CB5B11C913763546084EC0745E5D95F059BC731D752183FE306AB8FBCBD29FC6` |
| `verificador-integridade/integridade-verifier.exe` | 78.379.076 | `963B44556ECEA6A57D65E14A5EF74C1F842779FE05ACA068390F6511F295654C` |

## Build 2.4.0-beta / Verificador 1.3.0

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `instalar_captura_chat_zap/iped-viewers-impl-4.3.1-2.4.0-beta.jar` | 615.587 | `35347DDB5D7A0E0A6C3E3E7EECC4B4F5A269736393DEE369D36ED51C13C7BA4C` |
| `instalador-chat-captura-IPED-2.4.0-beta-verificador-1.3.0.zip` | 73.651.577 | `6121D310C118BEB4ADB5E44CB8B572FA3A2BE64E7C00D88F43D6CC55C293F07A` |
| `verificador-integridade/integridade-verifier.jar` | 126.570 | `CB5B11C913763546084EC0745E5D95F059BC731D752183FE306AB8FBCBD29FC6` |
| `verificador-integridade/integridade-verifier.exe` | 78.379.076 | `963B44556ECEA6A57D65E14A5EF74C1F842779FE05ACA068390F6511F295654C` |

O JAR do verificador foi construído com JDK 8 e bytecode 52. O EXE x64 passou pelo `--self-test` (código de saída 0). O ZIP contém estes binários e o instalador.

Este é o pacote anterior à correção 2.4.1-beta; o pacote atual e seu JAR principal estão na seção acima.

## Build 2.3.6-beta / Verificador 1.2.8

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `iped-viewers-impl-4.3.1-2.3.6-beta.jar` | 602.221 | `832E2CF887357CC0074625BB3A595C33B7D9DB0D11BD9582D8DAA1C5C3A2C9F7` |
| `instalador-chat-captura-IPED-2.3.6-beta-verificador-1.2.8.zip` | 661.619 | `6434DC5F4954D7CAD2E476E14F67BDF59CCD4E3C9F3332FAA1578E797E887D08` |
| `integridade-verifier-1.2.8.jar` | 117.491 | `906705C1A69F41505B0A794AA93F7E87D606FD62E5895A8FD2D3A9E0AA9E4B96` |
| `integridade-verifier-1.2.8.exe` | 78.370.372 | `4F591C102058B0046814E9A5C9019E7483DB82A1E286BFF80DDC4CEA7E85A4EF` |

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `iped-viewers-impl-4.3.1-2.3.5-beta.jar` | 601.549 | `35C557E418F26D793CD8F529C0AE53D7DE30A36BEB2AB89E3D8451851738433A` |
| `instalador-chat-captura-IPED-2.3.5-beta-verificador-1.2.8.zip` | 661.055 | `E991DB752FDC48D1B7B39FD77CE0F520194B43378A7E8282EDA7E1169BD1E026` |
| `integridade-verifier-1.2.8.jar` | 117.491 | `906705C1A69F41505B0A794AA93F7E87D606FD62E5895A8FD2D3A9E0AA9E4B96` |
| `integridade-verifier-1.2.8.exe` | 78.370.372 | `4F591C102058B0046814E9A5C9019E7483DB82A1E286BFF80DDC4CEA7E85A4EF` |

## Build anterior 2.3.4-beta / Verificador 1.2.7

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `iped-viewers-impl-4.3.1-2.3.4-beta.jar` | 596.428 | `C387C199B5388E25B28014D016C9005050E61E8FC4BF0AD696B4E3E8DA4A1B1B` |
| `instalador-chat-captura-IPED-2.3.4-beta-verificador-1.2.7.zip` | 657.029 | `4B762BB322B030600D79F5AA562BF397D020011E46FEF782A38432696C7AA4B5` |
| `integridade-verifier-1.2.7.jar` | 111.858 | `D16D0BA8EDD722DE810DE5A7E5B458271E216961CAF0DAD9BEBDA9B0F715097D` |
| `integridade-verifier-1.2.7.exe` | 78.361.129 | `AB85C3B2C55E2A727378BD5973ACF64CED2E16D1AC32198B11670DB48C8744A5` |

## Historico

| Versao | SHA-256 | Data | Mudanca |
|---|---|---|---|
| 2.3.1-beta | `7DAEEF3C3E8C046806AAD96C66182EE2C26CC366F8D38009B533DAABEC54B7D8` | 2026-11-06 | Verificador 1.2.4 e nova beta do plugin |
| 2.3.0-beta | `FCA50E2E458F0A3F77C8C87081AF9A6A7A438F1AFF315E31EF904BE77632FE74` | 2026-10-30 | Re-Extrair incremental, assinaturas por frame e auditoria de restauracao |
| 2.0.3 | `F3D940E0235AD3B5EDBB984104168604B55B4E7E48BD7072FEB5931411755EA1` | 2026-10-29 | botao alternavel Marcar/Desmarcar todos no Re-Extrair |
| 2.0.2 | `60D1293FD45980977FB0FE11F8706995E076D2F714F0404BE34F43735CEC0C24` | 2026-10-29 | Messages recompilada com JDK 11 (--release 11) |
| 2.0.1 | `6B11AE405B17A47366A2086A2B1FC9E5C0AD2285952C8AB4C0497B7B50AEC1AD` | 2026-10-29 | retorno seguro de chaves ausentes; validacao do instalador |
| 2.0.0 | `4C8EDA288C27691F28F19BE205CE3EDCF61ABEC4CB51491133F3149937EB29A5` | 2026-10-09 | busca por texto tambem no `whatsapp.html` gerado |
| 2.0.0 | `41A9D7B0D935381C08D428A864D16B5009F049C5E405258DB5E3DB2C98A153AB` | 2026-10-08 | pesquisa por texto no chat, com scroll ate a mensagem; tambem na janela vinculada |
| 2.0.0 | `4C7B5C5816FCCB394012B17F6B7E7757CC43D28C52DACA9560576C56566A5B3D` | 2026-10-08 | anotacoes so na RAM; `anotacoes.json` e gravado apenas na captura |
| 2.0.0 | `AF595DED00CAC3B92D0580C0FDF85543282B595F17C9DC8ADF2432C34EBC3521` | 2026-10-08 | clique no icone abre janela com Notas/Chat Vinculado (ver/apagar); icone nunca entra na captura |
| 2.0.0 | `B55827E568FA3FCFCB2CC8B34393AFE065F470CD54418B316EB8438B01E7E8DD` | 2026-10-08 | icone de anotacao a esquerda + popup de opcoes; busca e toggle de cronologia no `index.html` |
| 2.0.0 | `502AAC4C45FAD63FA100859DECE769F7A4510092B2CD6E40CEEB585D06E38644` | 2026-10-08 | corrige SyntaxError no JS do `whatsapp.html` |
| 2.0.0 | `AA56CF8F65733855D34603E8D12E6322F1232DE726E9CE2FF1757391EC56FDC1` | 2026-10-08 | HTML carrega a captura vinculada; pasta `vinculado` ignorada no indice |
| 2.0.0 | `DE2A2D3BF772928CF410A92737CB18995BD0FDA5A7F18CA1A385205D0720888B` | 2026-10-08 | janela do chat vinculado sem Notas/Links |
| 2.0.0 | `33D8882A195C9D93E7A52634132709F95C2F940D33E5D83CCD4032BB753F109E` | 2026-10-08 | destino fixo `vinculado`; lista mostra nomes e participantes |
| 2.0.0 | `0D9E5F78AC381E4EB91EA5F75DD7C9138943347A44EB921456390EB8536C1D4E` | 2026-10-08 | corrige carga dos chats do caso e abertura da janela |
| 2.0.0 | `09F56B44CF8599FA6066A980B4219AF0520C8B2530EFF6FB9BC083E40743DB0E` | 2026-10-08 | vincula CHATS, janela de captura propria, dialogos 640x640 |
| 2.0.0 | `C42E4F050D1EF8368C4E6FE833E11AE33151A2AB9A944CAAFC9DBDBB4844E59B` | 2026-10-08 | notas/links com chats e janela de captura |
| 2.0.0 | `AB243B99E180EEAAE8476656CF86B7114CBD0FC03BFFE222B84B20629BEB699F` | 2026-10-08 | primeira build com notas/links (vinculava contatos) |
| 1.x | `727783D85FABB34AEFF65AEAC4902A58977D05209710DFFE842DF81D3F730EAC` | 2026-06-14 | captura por intervalo, re-extrair, censura por blur |


## Como verificar

```powershell
Get-FileHash -Algorithm SHA256 `
  '.\instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar'
```

E o instalado:

```powershell
Get-FileHash -Algorithm SHA256 `
  '<pasta-do-iped>\lib\iped-viewers-impl-4.3.1.jar'
```

Os dois hashes devem ser iguais.

## Arquivos da release 2.3.0-beta

| Arquivo | Tamanho | SHA-256 |
|---|---:|---|
| instalador-chat-captura-IPED-2.3.0-beta.zip | 564.071 bytes | AABB031676ED1EC781C20E383BBBF1736C462136934FC62DBE31B2050DBEB3EE |
| integridade-verifier.jar | 96.466 bytes | A5415A32F1F9423AB0A187675A38BF71DB640FD346F6846F55510F4856CE3345 |
| integridade-verifier.exe | 78.345.769 bytes | FA04E2DB2A9EC35A6A53DC2713FF4DAC55B8253ECAAC32104F9612612196665F |
| src-plugin-2.3.0-beta.zip | 118.713 bytes | 4C3AA51399ACACD6E5306A0AB202E0CD516E644A2ED06AF64A3FE94F1AB8B7B1 |
| src-integridade-verifier-1.2.1.zip | 21.537 bytes | 03AD384642F54D2C197BB2EF26DA9D09F2532C58DEE1882A25723B8233870D14 |

O ZIP do instalador é autocontido. O JAR e EXE do verificador e os dois pacotes de código-fonte são assets separados para download independente.

## Artefatos da nova release v2.3.2-beta

| Arquivo | Tamanho | SHA-256 |
|---|---:|---|
| instalador-chat-captura-IPED-2.3.1-beta-verificador-1.2.4.zip | 568.308 bytes | `8FA73D44CA8C9EFC1B9972916DD5AC17476BA293094C469AE1904E8F0AC16F7A` |
| iped-viewers-impl-4.3.1-2.3.1-beta.jar | 594.192 bytes | `7DAEEF3C3E8C046806AAD96C66182EE2C26CC366F8D38009B533DAABEC54B7D8` |
| integridade-verifier-1.2.4.jar | 103.941 bytes | `211D8AA0043CA587C80225572A14A6345B3CB2CB3E3CCFF6F2A955665EF8C01E` |
| integridade-verifier-1.2.4.exe | 77.806.121 bytes | `DFA25E34339D79D6A844B78BF7611B581CA478408394610E659CD6097BD8AAF9` |
| src-plugin-2.3.1-beta.zip | 214.051 bytes | `EA8C181F914AD4D36E3D5447CB8CDC5B66E3F5B9BD6F3C5622D6EAF7E37DDEB4` |
| src-integridade-verifier-1.2.4.zip | 24.830 bytes | `1BDBE0C4FDE88F148150690A34C7973C888295E35587A34C7A5ACD242A486B72` |

Os hashes das versões anteriores do verificador estão em [versao_hash.md](versao_hash.md).

| instalador-chat-captura-IPED-2.3.2-beta-verificador-1.2.5.zip | 656.213 bytes | 313A06540395539A6F946DA4BC650B5F54E45BA94D1BC3CDE6C9A5BDB7E25DA0 |
| iped-viewers-impl-4.3.1-2.3.2-beta.jar | 595.899 bytes | 99086435683240FBE54F40C678E6288D27970686A7C02BECD241952B8B949FBE |
| integridade-verifier-1.2.5.jar | 111.960 bytes | 5386859AE3127CB85BB666DBCD19C57836D0797C48A7CBF96957B5103E3AE9CB |
| integridade-verifier-1.2.5.exe | 78.361.129 bytes | DA7D172765E80E02101B540CA0212F33490897C946CFE4FEEC17731E365C027F |
| src-plugin-2.3.2-beta.zip | 113.469 bytes | CE3F67D847794AD3735B225B90C4AB4A1F4861F75E7D14B145E3EB587212ABDD |
| src-integridade-verifier-1.2.5.zip | 22.922 bytes | 537840B0C768B5D1EE260F9CDFEB24B75AC3AA9619A52F7A649EBD43E2B40A3E |
