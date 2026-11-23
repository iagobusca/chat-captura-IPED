# Histórico de hashes do Verificador de Integridade

Os hashes abaixo são SHA-256 dos binários registrados. A versão 1.6.1 acompanha a release beta `v2.9.4-beta` (branch `beta`, com compilação determinística auditável via `build-reproducible.ps1`) e passa a ser a única aceita pelo plugin `2.9.4-beta` (versões `<= 1.6.0` são rejeitadas pela allowlist exclusiva). Compare o hash do arquivo antes de executá-lo.

> **Nota de publicação:** As releases públicas no GitHub iniciam na série estável `v2.6.8` / Verificador `1.4.3` (`1.4.3`, `1.4.4`, `1.4.5`). As linhas `<= 1.4.2` documentam o histórico anterior mantido para auditoria e verificação de rejeição em `VerifierIntegrityHashTest`.

## Binários com hashes registrados

| Versão | Arquivo | SHA-256 | Evidência |
|---|---|---|---|
| 1.6.1 (release v2.9.4-beta) | `integridade-verifier.jar` | `D1DD34970DB68DA0442106CCF40E887B60A6FABCC7E2BDAEE92E9F87E938EFC9` | Build determinístico (`build-reproducible.ps1` `--release 8`); `13.064.528` bytes; `SELF_TEST_OK 1.6.1` |
| 1.6.1 (release v2.9.4-beta) | `integridade-verifier.exe` | `B0ED015292457F5F3389755786EFCFE7961A77E18A3356C2CA6B0AFE7CFEC59B` | Build Windows x64 determinístico (`.NET 10.0.15`); `90.590.810` bytes; `SELF_TEST_OK 1.6.1` |
| 1.6.0 (release v2.9.0) | `integridade-verifier.jar` | `E5006E345C0894E0678BFE97A71FBD5399D1FEEA77D67A30DCCF90049A580745` | Build determinístico (`build-reproducible.ps1` `--release 8`); `13.062.572` bytes; `SELF_TEST_OK 1.6.0` |
| 1.6.0 (release v2.9.0) | `integridade-verifier.exe` | `1F14B7A10E9AEE2B94B699066478AAD237A424077C7D0A033005DBD2FEED4920` | Build Windows x64 determinístico (`.NET 10.0.15`); `90.589.274` bytes; `SELF_TEST_OK 1.6.0` |
| 1.5.12 (release v2.8.14-beta) | `integridade-verifier.jar` | `B84289B03C2EDA7C675FB29B72372677262417D809C991464CE26A219F6C7B3B` | Build determinístico (`build-reproducible.ps1` `--release 8`); `13.062.577` bytes; `SELF_TEST_OK 1.5.12` |
| 1.5.12 (release v2.8.14-beta) | `integridade-verifier.exe` | `BF3AAC82D5F35D2D7F033D05AED2B7CF539F5620806BDE53A97B0F184E7EF393` | Build Windows x64 determinístico (`.NET 10.0.15`); `90.589.276` bytes; `SELF_TEST_OK 1.5.12` |
| 1.5.11 (release v2.8.13-beta) | `integridade-verifier.jar` | `CC80FA62B2A8AE85AE15D9B6A829EB59E8BBB3E996D3E9A314F288B545C0D24B` | Build determinístico (`build-reproducible.ps1` `--release 8`); `13.061.220` bytes; `SELF_TEST_OK 1.5.11` |
| 1.5.11 (release v2.8.13-beta) | `integridade-verifier.exe` | `A7F21554A38941543D944563C5B91A1CCD1EA3DC9BE63CEC2623FAF5E2668ACF` | Build Windows x64 determinístico (`.NET 10.0.15`); `90.587.740` bytes; `SELF_TEST_OK 1.5.11` |
| 1.5.10 (release v2.8.12-beta) | `integridade-verifier.jar` | `09D7166AE5EF62C63E6888B392D12C67A6457861974B2677352FE4D65520DE30` | Build determinístico (`build-reproducible.ps1` `--release 8`); `13.058.749` bytes; `SELF_TEST_OK 1.5.10` |
| 1.5.10 (release v2.8.12-beta) | `integridade-verifier.exe` | `8C34C3E1E744BE108E5EE0D28010C053FF35166F5E7D119B1AE0E745E79082AC` | Build Windows x64 determinístico (`.NET 10.0.15`); `90.585.692` bytes; `SELF_TEST_OK 1.5.10` |
| 1.5.9 (release v2.8.11-beta) | `integridade-verifier.jar` | `39F076C777A31BE9831BF0218FE00EF2F3B169B28010E9BD9CE8D40EEA2C187A` | Build determinístico (`build-reproducible.ps1` `--release 8`); `13.052.705` bytes; `SELF_TEST_OK 1.5.9` |
| 1.5.9 (release v2.8.11-beta) | `integridade-verifier.exe` | `23386DC247DA905D268527CD58868390FDB33AAC3E89B86A178BA9B1CAEAB55C` | Build Windows x64 determinístico (`.NET 10.0.15`); `90.580.058` bytes; `SELF_TEST_OK 1.5.9` |
| 1.5.8 (release v2.8.10) | `integridade-verifier.jar` | `07EFAB49FE93DFAD1014C6E7DA50BDEADD5108BDAEF13AE267416BF3ACF41C6E` | Build determinístico (`build-reproducible.ps1` `--release 8`); `13.052.436` bytes; `SELF_TEST_OK 1.5.8` |
| 1.5.8 (release v2.8.10) | `integridade-verifier.exe` | `7E26F0ABFE20CEA7277B76E5781DE262A1525DA59EF17C211FC062398D118CC0` | Build Windows x64 determinístico (`.NET 10.0.15`); `90.580.058` bytes; `SELF_TEST_OK 1.5.8` |
| 1.5.7 (release v2.8.9) | `integridade-verifier.jar` | `A9170EFBF4F528AA0FD1FB5665F1E0F40C032023A7B78FE1BA94ACF5D0BEC6E1` | Build determinístico (`build-reproducible.ps1` `--release 8`); `215.152` bytes; `SELF_TEST_OK 1.5.7` |
| 1.5.7 (release v2.8.9) | `integridade-verifier.exe` | `81D704B1EF8EB2E121B73FF98C583F3595E998D3F00DE72EB51C5AEF4CE3F3B3` | Build Windows x64 determinístico (`.NET 10.0.15`); `77.771.354` bytes; `SELF_TEST_OK 1.5.7` |
| 1.5.6 (release v2.8.8) | `integridade-verifier.jar` | `A027A301AFFCF1D0A2310F6E1A495359AB36E08F3AA35147B19786C183CDA62A` | Build determinístico (`build-reproducible.ps1` `--release 8`); `210.223` bytes; `SELF_TEST_OK 1.5.6` |
| 1.5.6 (release v2.8.8) | `integridade-verifier.exe` | `5EF6B9AC2C6795A49A18C37029586B318FD09C3B71DA53486CF23722C9AAC917` | Build Windows x64 determinístico (`.NET 10.0.15`); `77.665.370` bytes; `SELF_TEST_OK 1.5.6` |
| 1.5.5 (release v2.8.7) | `integridade-verifier.jar` | `827B4580836D7CE0811DB1975E5971A610C0C946D9DBDBECD4CCEAFB275FBEED` | Build determinístico (`build-reproducible.ps1` `--release 8`); `202.850` bytes; `SELF_TEST_OK 1.5.5` |
| 1.5.5 (release v2.8.7) | `integridade-verifier.exe` | `639CE7B5A78D0C4DA1D55D28ACE317F342C93F7BFF13A28F5B33D45DB8BE39D1` | Build Windows x64 determinístico (`.NET 10.0.15`); `77.658.202` bytes; `SELF_TEST_OK 1.5.5` |
| 1.5.4 (release v2.8.5) | `integridade-verifier.jar` | `8180566A0BEF7D70815FBCFFA77DE37B833B9BD4B2C87B185BB1B2A5792A41EF` | Build determinístico (`build-reproducible.ps1` `--release 8`); `200.920` bytes; `SELF_TEST_OK 1.5.4` |
| 1.5.4 (release v2.8.5) | `integridade-verifier.exe` | `959424CFC4991148F23A4E41F167512C449128392618F3B070E3837E34FFF370` | Build Windows x64 determinístico (`.NET 10.0.15`); `77.656.154` bytes; `SELF_TEST_OK 1.5.4` |
| 1.5.3 (release v2.8.4) | `integridade-verifier.jar` | `39BE8D3C8E9F5389D21659D6A1E62435C246241578E3CAB9EEA2C22D03992456` | Build determinístico (`build-reproducible.ps1` `--release 8`); `193.914` bytes; `SELF_TEST_OK 1.5.3` |
| 1.5.3 (release v2.8.4) | `integridade-verifier.exe` | `B2A3D1CDBE36673215EFF438C24CBC636D38A578AF5C74444726041BE42E0D0F` | Build Windows x64 determinístico (`.NET 10.0.15`); `77.649.498` bytes; `SELF_TEST_OK 1.5.3` |
| 1.5.2 (release v2.8.2) | `integridade-verifier.jar` | `22C207E89F56C8A35CAA327E7A491246739A81596172521E7B807EA167DDEB7F` | Build determinístico (`build-reproducible.ps1` `--release 8`); `193.537` bytes; `SELF_TEST_OK 1.5.2` |
| 1.5.2 (release v2.8.2) | `integridade-verifier.exe` | `F3582CEFE0440B6DAA7DEA8E7F425A55C33DBC29EE2BE92DF875A2D18382B238` | Build Windows x64 determinístico (`.NET 10.0.15`); `77.648.986` bytes; `SELF_TEST_OK 1.5.2` |
| 1.5.1 (release v2.8.1) | `integridade-verifier.jar` | `9809DEC2088F50F88ED1691544CEF45B029783B04498F65F43ECC5634C06C7A3` | Build determinístico (`build-reproducible.ps1` `--release 8`); `182.286` bytes; `SELF_TEST_OK 1.5.1` |
| 1.5.1 (release v2.8.1) | `integridade-verifier.exe` | `E03DEA3BFA298E125804AB22A51C113665FEAE58529248E0C3947C34EACB7C6F` | Build Windows x64 determinístico (`.NET 10.0.15`); `77.638.746` bytes; `SELF_TEST_OK 1.5.1` |
| 1.5.0 (release v2.8.0) | `integridade-verifier.jar` | `194C1D9B6FE4AE615F882E74BE2B0ED662466F9E7AF2F887E5A8878A3242BA11` | Build determinístico (`build-reproducible.ps1` `--release 8`); `175.612` bytes; `SELF_TEST_OK 1.5.0` |
| 1.5.0 (release v2.8.0) | `integridade-verifier.exe` | `20F823BF5B22E92672DEB0D6DB6150733D1F1B9CD4313E6519ABDB5E4EB51524` | Build Windows x64 determinístico (`.NET 10.0.15`); `77.632.090` bytes; `SELF_TEST_OK 1.5.0` |
| 1.4.5 (release v2.7.0) | `integridade-verifier.jar` | `E013F31E2EECB8517062854CED2DBCAACD5CFFED8CECB5E9DBE2B2F223E2588D` | Build determinístico (`build-reproducible.ps1` com Temurin 21.0.13+9 `--release 8`, bytecode 52, timestamps UTC fixos `1980-02-01T00:00:00Z`); `159.164` bytes; `--self-test` código 0 (`SELF_TEST_OK 1.4.5`); mantém todas as proteções de `1.4.4` |
| 1.4.5 (release v2.7.0) | `integridade-verifier.exe` | `AF1C41C080A67B206870D17FF89362F959404C13A3F4D43E426BE840C1F240A3` | Build Windows x64 autocontido determinístico (`jlink`/`jpackage` Temurin 21.0.13+9 + `payload.zip` determinístico + `.NET 10.0.15` com `-p:Deterministic=true`); `--self-test` código 0 (`SELF_TEST_OK 1.4.5`); `77.616.218` bytes |
| 1.4.4 (release v2.6.9) | `integridade-verifier.jar` | `C37D52F2D12A9AB0E954744F84B3E47805D9F3BF8B460A864C70AC06AE4BF7FF` | Build com JDK 8; bytecode 52; 159.189 bytes; scanner HTML léxico por máquina de estados (`scanHtmlElements` / `parseHtmlTagStrict`), guarda `openFile` (`NOFOLLOW_LINKS` + `maxBytes`), allowlists HTML/MIME simétricas, `safeRegularFileSize`, `html.disable` e `MAX_SCAN_ENTRIES` |
| 1.4.4 (release v2.6.9) | `integridade-verifier.exe` | `A57CB7E8C15B256DDBDE99899157D8CC2B8AA8B3ABCD8F30D7CACE17F2962291` | Build Windows x64 autocontido (Java 11 + runtime `.NET 10.0.15` sem CVEs, bloqueio de reparse points e limites de descompressão no launcher); `--self-test` código 0 (`SELF_TEST_OK 1.4.4`); 77.874.266 bytes |
| 1.4.3 (release v2.6.8) | `integridade-verifier.jar` | `104F69AABCB86851C2DA905AD6E8428177BDEC1E6D3750580291A8ECD008B382` | Build com JDK 8; bytecode 52; 159.189 bytes; scanner HTML léxico por máquina de estados (`F-01` Fase 4), guarda `openFile` (`NOFOLLOW_LINKS`), allowlists HTML/MIME simétricas com o plugin, `safeRegularFileSize` sem symlink TOCTOU, `html.disable` e `MAX_SCAN_ENTRIES` |
| 1.4.3 (release v2.6.8) | `integridade-verifier.exe` | `17D8F5DCED38029B68849AF11A2358B38B31FD4A01BDAF4BFEA514FC27842E05` | Build Windows x64 autocontido (Java 11 + runtime `.NET 10.0.15` sem CVEs, bloqueio de reparse points e limites de descompressão no launcher); `--self-test` código 0; 77.874.266 bytes |
| 1.4.2 (release v2.6.7-beta) | `integridade-verifier.jar` | `08877EFF5F2FA0A1E2F64AD0072490F9400AAA26E51ACD8E18C4D2DEE5F59475` | Build com JDK 8; bytecode 52; 155.216 bytes; correções `F-01`..`F-10` |
| 1.4.2 (release v2.6.7-beta) | `integridade-verifier.exe` | `4859980DC532340427EEF2983B62EA4596BC6B8FA1D6F80462BB76CF03278105` | Build Windows x64 autocontido (`java.base,java.datatransfer,java.desktop,java.xml`); `--self-test` código 0; 77.868.100 bytes |
| 1.4.1 (release v2.6.6-beta) | `integridade-verifier.jar` | `CC2958812B0D56CF60B96641EE75F5776F463E0986503AC0FE15ABF82CE71345` | Build com JDK 8; bytecode 52; 153.719 bytes; bloqueio de extras em capturas legadas, junction na raiz, cancelamento limpo e CSP SHA-256/`&#39;`/JSON escape no HTML |
| 1.4.1 (release v2.6.6-beta) | `integridade-verifier.exe` | `A7283005AF86ED5DD0E47C07619DCD1F3A6F4618B10DE3AC208B7F2A1549A29C` | Build Windows x64 autocontido (`java.base,java.datatransfer,java.desktop,java.xml`); `--self-test` código 0; 77.864.004 bytes |
| 1.4.0 (release v2.6.5-beta) | `integridade-verifier.jar` | `9CA17D7BA2EC50808D333495716D277F4DA963BBBC946C3EF7F765079F48A6E5` | Build com JDK 8; bytecode 52; 152.916 bytes; carregamento Local/Público, resumo simplificado, HTML `OK`/`FALHA` e `alwaysOnTop`/EDT |
| 1.4.0 (release v2.6.5-beta) | `integridade-verifier.exe` | `11094FA3CCE812AF3421490FF1BC2F3B8100C9B5D7551DBBA7F05F78C8136809` | Build Windows x64 autocontido (`java.base,java.datatransfer,java.desktop,java.xml`); `--self-test` código 0; 77.862.980 bytes |
| 1.3.9 (release v2.6.4-beta) | `integridade-verifier.jar` | `043AFEC21D79B4DF3C8A3DDB42F49CF5CB9467CECD11397E724091DFA310E2A7` | Build com JDK 8; bytecode 52; 142.687 bytes; decodificação de entidades HTML em atributos (`&#39;` na CSP e links/fontes) |
| 1.3.9 (release v2.6.4-beta) | `integridade-verifier.exe` | `FF06525056E008A91AABF49D9B98B3E0CEE55CCED3846A89AA76D7B39BC6BC85` | Build Windows x64 autocontido (`java.base,java.datatransfer,java.desktop,java.xml`); `--self-test` código 0; 77.853.252 bytes |
| 1.3.8 (release v2.6.3-beta) | `integridade-verifier.jar` | `556B1A652183A343B168057029CCF10006361D8CB53526BA610E81A4D76532B6` | Build com JDK 8; bytecode 52; 142.154 bytes; hardening JSON malformado/HTML legado/CSS/SVG e verificação `canonical-v1` |
| 1.3.8 (release v2.6.3-beta) | `integridade-verifier.exe` | `B9D77A7F88F7D87D3697B855191427C3CE790EFA827830DEABE263E4E2962DD2` | Build Windows x64 autocontido (`java.base,java.datatransfer,java.desktop,java.xml`); `--self-test` código 0; 77.852.740 bytes |
| 1.3.7 (release v2.6.2-beta) | `integridade-verifier.jar` | `7A137AE3286231246AF335380C3E8716B17A6696552096474563169DB4F5F2EE` | Build com JDK 8; bytecode 52; 139.632 bytes; pastas auxiliares de recaptura ignoradas em `discover` e hardening fase 2 |
| 1.3.7 (release v2.6.2-beta) | `integridade-verifier.exe` | `D102E17D02F39E5D6E85249E9B9585BC26C3D005E7DBFF2296C22FB2E18E926A` | Build Windows x64 autocontido (`java.base,java.datatransfer,java.desktop,java.xml`); `--self-test` código 0; 77.850.180 bytes |
| 1.3.6 (release v2.6.1-beta) | `integridade-verifier.jar` | `F7990400A98445B8FA718626DA08145CD974866487650BD443E95427DD015FAC` | Build com JDK 8; bytecode 52; 137.188 bytes; correções de segurança (arquivos extras, junções NTFS, allowlist HTML, sanitização de histórico) |
| 1.3.6 (release v2.6.1-beta) | `integridade-verifier.exe` | `24E84B1B014D9677E238A94FD9EBFFB5CD2E8CF5CCA244AF8D47D1ED48C704D4` | Build Windows x64 autocontido; `--self-test` código 0; 77.843.012 bytes |
| 1.3.5 (release v2.6.0-beta) | `integridade-verifier.jar` | `31B0CE725708ED53AD9BE319FFE51439940C65BB46EF8593AB78376C07BF46B9` | Build com JDK 8; bytecode 52; 133.282 bytes; histórico completo público anonimizado (`CAPTURA\|*-YYYYMMDD-HHMMSS`) e verificação dual |
| 1.3.5 (release v2.6.0-beta) | `integridade-verifier.exe` | `75FB542BCB53C44C7B17553EEC761C16F17C670F8033DD3F901F80A0897AC42B` | Build Windows x64 autocontido; `--self-test` código 0; 77.839.428 bytes |
| 1.3.4 (rebuild v2.5.9-beta) | `integridade-verifier.jar` | `B456074C393949FE63DC33B404F539B569830CE92FC657FF43B622D9E03500DD` | Build com JDK 8; bytecode 52; 132.375 bytes; aceita registros de chamada `_call:<hex>`, removidos `-?<id>_<id>` e timestamp em linha |
| 1.3.4 (rebuild v2.5.9-beta) | `integridade-verifier.exe` | `20FAF8A5AD2690A909F09B6A60DFBFE12A271493DA3331595F8BB2A95EF2BCA0` | Build Windows x64 autocontido; `--self-test` código 0; 77.838.404 bytes |
| 1.3.4 (build inicial v2.5.8-beta) | `integridade-verifier.jar` | `241FD904C02F65E19EE3D663C24A9AA15B685E8A43DD7F545AD5D07133BC79DB` | Build com JDK 8; bytecode 52; 132.353 bytes |
| 1.3.4 (build inicial v2.5.8-beta) | `integridade-verifier.exe` | `E4137082FE2A025DD406891A82313B2094A6C15A2F6D2F0804E2BE342ED57C38` | Build Windows x64 autocontido; `--self-test` código 0; 77.838.404 bytes |
| 1.3.3 | `integridade-verifier.jar` | `22FBAA2A8D4EA32940B4555E95207A22982B7FCDD308020950A667853420D921` | Build com JDK 8; bytecode 52; 131.972 bytes |
| 1.3.3 | `integridade-verifier.exe` | `933F27A61AEDDAC5E4F1D8D7CA7AFC057AA2F04062A9267783FC56BB00DCE25B` | Build Windows x64 autocontido; `--self-test` código 0; 78.384.708 bytes |
| 1.3.2 | `integridade-verifier.jar` | `F6D21DC4DAAFBF0BB241B66790EE2FE137AF5DBD5609EF9CED8D1336D622A7A7` | Build com JDK 8; bytecode 52; 131.464 bytes |
| 1.3.2 | `integridade-verifier.exe` | `94FC180EA3D25DB9881F3D245FE1D2100CC21B6263F6163E129B5033B55D5908` | Build Windows x64 autocontido; `--self-test` código 0; 78.384.196 bytes |
| 1.3.1 | `integridade-verifier.jar` | `0C262CC3D2F8BB0FD6CF7FB3E4071CF19903D684396356F56F9D92DB869F8339` | Build com JDK 8; bytecode 52; 131.441 bytes |
| 1.3.1 | `integridade-verifier.exe` | `AE1E2E1E9D7E78BBCF8C551C2209F7F990C8AC37C77F751556BB29D698D719F5` | Build Windows x64 autocontido; `--self-test` código 0; 78.384.196 bytes |
| 1.3.0 | `integridade-verifier.jar` | `CB5B11C913763546084EC0745E5D95F059BC731D752183FE306AB8FBCBD29FC6` | Build com JDK 8; bytecode 52; 126.570 bytes |
| 1.3.0 | `integridade-verifier.exe` | `963B44556ECEA6A57D65E14A5EF74C1F842779FE05ACA068390F6511F295654C` | Build Windows x64 autocontido; `--self-test` código 0; 78.379.076 bytes |
| 1.2.8 | `integridade-verifier.jar` | `906705C1A69F41505B0A794AA93F7E87D606FD62E5895A8FD2D3A9E0AA9E4B96` | Build local com JDK 8; bytecode 52 |
| 1.2.8 | `integridade-verifier.exe` | `4F591C102058B0046814E9A5C9019E7483DB82A1E286BFF80DDC4CEA7E85A4EF` | Build local Windows x64 autocontido; runtime .NET 10.0.14 |
| 1.2.7 | `integridade-verifier.jar` | `D16D0BA8EDD722DE810DE5A7E5B458271E216961CAF0DAD9BEBDA9B0F715097D` | Build com JDK 8; bytecode 52 |
| 1.2.7 | `integridade-verifier.exe` | `AB85C3B2C55E2A727378BD5973ACF64CED2E16D1AC32198B11670DB48C8744A5` | EXE Windows x64 com runtime incluído |
| 1.2.6 | `integridade-verifier.jar` | `880F7ACB2332BB73DF24A59A290704A22F7AD9E39F7F17A9627739ED41EEC56F` | Build local da versão 1.2.6 para paginação |
| 1.2.6 | `integridade-verifier.exe` | `3EF8E9BEA0C5EC58B8522ED2C2C8E18CD0E10198F0EF1887EB9E250FC709D950` | Build local da versão 1.2.6 para paginação |
| 1.2.5 | `integridade-verifier.jar` | `5386859AE3127CB85BB666DBCD19C57836D0797C48A7CBF96957B5103E3AE9CB` | Build local da release v2.3.2-beta |
| 1.2.5 | `integridade-verifier.exe` | `DA7D172765E80E02101B540CA0212F33490897C946CFE4FEEC17731E365C027F` | Build local da release v2.3.2-beta |
| 1.2.4 | `integridade-verifier.jar` | `211D8AA0043CA587C80225572A14A6345B3CB2CB3E3CCFF6F2A955665EF8C01E` | Asset da release v2.3.1-beta |
| 1.2.4 | `integridade-verifier.exe` | `DFA25E34339D79D6A844B78BF7611B581CA478408394610E659CD6097BD8AAF9` | Asset da release v2.3.1-beta |
| 1.2.3 | `integridade-verifier.jar` | `61E4544D1723392C5DC918E771F69EBFAC9ACF136EF6BABA098EE1AF8A152072` | Asset publicado na release `v2.3.0-beta` |
| 1.2.3 | `integridade-verifier.exe` | `9439A1BD27D7C81EBFC334EC161096FD915E84EF347527EDC504129BA94A51E1` | Asset publicado na release `v2.3.0-beta` |

## Versões anteriores

Os arquivos preservados para as versões 1.2.1 e 1.2.2 são pacotes de código-fonte. Não encontrei hashes de JAR/EXE publicados para essas versões nos assets locais nem na página de releases do repositório; por isso não atribuo hashes de binários a elas.

| Versão | Arquivo preservado | SHA-256 |
|---|---|---|
| 1.2.1 | `src-integridade-verifier-1.2.1.zip` (código-fonte) | `03AD384642F54D2C197BB2EF26DA9D09F2532C58DEE1882A25723B8233870D14` |
| 1.2.2 | `src-integridade-verifier-1.2.2.zip` (código-fonte) | `8C5413C833093136DDAA2A080B8339FE464CCF19E3F4A7DCF98BAC9ED80529BA` |
| 1.2.3 | `src-integridade-verifier-1.2.3.zip` (código-fonte) | `83CD34A66C46C1ED0EF6D34C0E6A5B1E54EB3376B500D60F56F0668569D674EF` |

A versão atual é 1.4.2; o JAR foi compilado para Java 8. Os hashes das versões anteriores (já abandonadas pelo plugin 2.6.7-beta) são mantidos abaixo apenas para consulta histórica.
