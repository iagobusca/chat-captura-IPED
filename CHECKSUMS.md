# Checksums

Arquivo principal do instalador:

```text
instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar
SHA-256: 7DAEEF3C3E8C046806AAD96C66182EE2C26CC366F8D38009B533DAABEC54B7D8
Versao do plugin: 2.3.1-beta
```

Este hash identifica o JAR distribuido. O instalador mescla bundles de idioma no JAR instalado, portanto os hashes do pacote e da instalacao podem diferir; valide as entradas/classes com `validate-plugin-jar.ps1`.

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
  'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\plugins\chat-captura IPED\instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar'
```

E o instalado:

```powershell
Get-FileHash -Algorithm SHA256 `
  'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\iped-4.3.1\lib\iped-viewers-impl-4.3.1.jar'
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

## Artefatos da nova release v2.3.1-beta

| Arquivo | Tamanho | SHA-256 |
|---|---:|---|
| instalador-chat-captura-IPED-2.3.1-beta-verificador-1.2.4.zip | 568.308 bytes | `8FA73D44CA8C9EFC1B9972916DD5AC17476BA293094C469AE1904E8F0AC16F7A` |
| iped-viewers-impl-4.3.1-2.3.1-beta.jar | 594.192 bytes | `7DAEEF3C3E8C046806AAD96C66182EE2C26CC366F8D38009B533DAABEC54B7D8` |
| integridade-verifier-1.2.4.jar | 103.941 bytes | `211D8AA0043CA587C80225572A14A6345B3CB2CB3E3CCFF6F2A955665EF8C01E` |
| integridade-verifier-1.2.4.exe | 77.806.121 bytes | `DFA25E34339D79D6A844B78BF7611B581CA478408394610E659CD6097BD8AAF9` |
| src-plugin-2.3.1-beta.zip | 216.721 bytes | `EA8C181F914AD4D36E3D5447CB8CDC5B66E3F5B9BD6F3C5622D6EAF7E37DDEB4` |
| src-integridade-verifier-1.2.4.zip | 24.830 bytes | `1BDBE0C4FDE88F148150690A34C7973C888295E35587A34C7A5ACD242A486B72` |

Os hashes das versões anteriores do verificador estão em [versao_hash.md](versao_hash.md).
