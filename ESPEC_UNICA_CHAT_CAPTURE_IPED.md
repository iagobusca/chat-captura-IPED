# Especificação consolidada — Chat Captura IPED

**Versão deste documento:** 2.1  
**Estado de referência:** plugin `2.6.0-beta`; Verificador de Integridade `1.3.5`  
**Base da revisão:** código, testes e documentação presentes no repositório em 2026-11-12.  
**Fontes consolidadas:** `SPEC_ajustes_plugin_verificador.md`, `SPEC_CAPTURA_HTML_POR_MENSAGEM.md`, `SPEC_CENSURA_IMAGENS_HTML_MESSAGE.md`, `SPEC_CORRECAO_MARCADORES_CAPTURA_HTML.md`, `SPEC_CORRECOES_SEGURANCA_PLUGIN_VERIFICADOR.md`, `SPEC_LIMPEZA_REGISTROS_RAIZ_REEXTRACAO.md`, `SPEC_MIGRACAO_REEXTRACAO_LEGADA_HTML.md`, `SPEC_ORGANIZACAO_INTEGRIDADE_PUBLICO.md`, `SPEC_PAGINACAO_SELECAO_INCREMENTAL.md`, `SPEC_RENDERIZACAO_WHATSAPP_HTML.md`, `SPEC_verificador_integridade_plugin.md`.

Este documento substitui as especificações dispersas do projeto. Ele descreve o comportamento confirmado no código, registra o que foi aplicado, o que não foi aplicado e o que foi removido (não se aplica mais). "Implementado" significa que há código correspondente; não significa que todos os cenários foram validados em uma sessão gráfica real do IPED.

## 1. Legenda de situação

- **Aplicado:** comportamento presente no código distribuído (plugin 2.6.0-beta / verificador 1.3.5).
- **Parcial:** existe parte da estrutura, mas falta o comportamento de ponta a ponta descrito.
- **Não aplicado:** consta de proposta anterior, mas não foi entregue nesta versão. Não deve ser anunciado como recurso disponível.
- **Removido / não se aplica mais:** uma decisão posterior mudou a forma de atender ao requisito antigo; o item não será implementado como proposto.

## 2. Auditoria das especificações anteriores

| Documento anterior | Situação apurada | Decisão consolidada |
|---|---|---|
| `SPEC_ajustes_plugin_verificador.md` | Parcialmente aplicado. O alerta do verificador virou informativo (`INFORMATION_MESSAGE`, `alertShown`), mas a conversão dos diálogos do verificador para `JFrame`/`JDialog` com ícone, botões "sempre no topo"/"minimizar" e pasta `imagem doc/` não foi implementada. | Manter o alerta informativo. A parte de janela/ícone do verificador permanece **não aplicada**. |
| `SPEC_CAPTURA_HTML_POR_MENSAGEM.md` | Aplicado. Formato `html-message-v1`, manifesto por mensagem, anexos por SHA-256, reconciliação, migração e verificação no verificador 1.3.0+. | Consolidado nas seções 4 e 6. |
| `SPEC_CENSURA_IMAGENS_HTML_MESSAGE.md` | Aplicado em 2.5.8-beta. Censura por CSS puro + atributos HTML em `html-message-v1` (captura nova, Re-Extrair e migração), com `CaptureCensorship`, cache por SHA-256 e bloco `censorship` no manifesto. | Consolidado na seção 5. |
| `SPEC_CORRECAO_MARCADORES_CAPTURA_HTML.md` | Aplicado em 2.5.2-beta. Validação de marcadores, resolução de separador de data, falha segura. | Consolidado na seção 4. |
| `SPEC_CORRECOES_SEGURANCA_PLUGIN_VERIFICADOR.md` | Aplicado. Confinamento de caminhos, limites de tamanho, escaping, parser JSON. | Consolidado na seção 7. |
| `SPEC_LIMPEZA_REGISTROS_RAIZ_REEXTRACAO.md` | Aplicado em 2.5.8-beta. `archiveStaleRootIntegrityReports` removido; `deleteRootIntegrityReportsIfExists` apaga `integridade/` e `integridade.html` no modo "Limpar registros"; `_backup_integridade_*` tratado como auxiliar. | Consolidado na seção 4. |
| `SPEC_MIGRACAO_REEXTRACAO_LEGADA_HTML.md` | Aplicado em 2.5.5/2.5.6-beta. Migração opt-in de capturas legadas para `html-message-v1`, toggle "Limpar registros", validação completa da candidata, preservação de anotações legadas. | Consolidado na seção 4. |
| `SPEC_ORGANIZACAO_INTEGRIDADE_PUBLICO.md` | Aplicado. Janela "Integridade Público" reorganizada: removidos campo de manifesto, botão "Copiar hash calculado" e botão "Comparar manifesto por mensagem"; layout em 2 blocos + "Continuar para o resumo". | Consolidado na seção 6. |
| `SPEC_PAGINACAO_SELECAO_INCREMENTAL.md` | Aplicado em 2.3.3-beta. Até 100 capturas por página, navegação anterior/próximo, validação sob demanda. | Consolidado na seção 6. |
| `SPEC_RENDERIZACAO_WHATSAPP_HTML.md` | Aplicado. Apresentação autônoma de `html-message-v1` com tema WhatsApp do IPED 4.3.1, modal local para anexos, controles de mídia. | Consolidado na seção 5. |
| `SPEC_verificador_integridade_plugin.md` | Aplicado. `VerifierIntegrity` valida SHA-256 do verificador contra allowlist, alerta uma vez por sessão, substitui binários inválidos. | Consolidado na seção 6. |

## 3. Itens não aplicados / removidos (não se aplica mais)

| Item (origem) | Situação | Motivo |
|---|---|---|
| Conversão dos diálogos do verificador para `JFrame`/`JDialog` com ícone, botões "sempre no topo"/"minimizar" e pasta `imagem doc/` (`SPEC_ajustes_plugin_verificador.md`, seção 2-3) | **Não aplicado** | O verificador permanece com diálogos baseados em `JOptionPane`/`JFrame` padrão com barra do SO. A proposta de botões customizados não foi implementada. |
| `getRangeMediaHashes()` (MD5) para censura no formato novo (`SPEC_CENSURA_IMAGENS_HTML_MESSAGE.md`, correção 1) | **Removido / não se aplica mais** | O formato `html-message-v1` usa SHA-256; a censura casa sobre bytes dos anexos diretamente, com cache por SHA-256. |
| `matchesSensitiveImageFromPreviousCapture` (MD5 de bloco do legado) no novo caminho (`SPEC_CENSURA_IMAGENS_HTML_MESSAGE.md`, correção 6) | **Removido / não se aplica mais** | Não é usada no caminho `html-message-v1`; anexos originais são resolvidos do caso e casados diretamente. |
| Regra CSS `.capture-modal .capture-preview-image{filter:none}` (`SPEC_CENSURA_IMAGENS_HTML_MESSAGE.md`, correção 7) | **Removido / não se aplica mais** | O modal não é descendente de `.capture-censored`, portanto a regra é desnecessária. |
| Vinculação de contatos/pessoas à mensagem (`ESPECIFICACAO_NOTAS_LINKS` antiga) | **Substituído** | Substituído por referência a chats (`LinkedChatRef`, `chatRefId`). Campos antigos como `contactId` permanecem apenas como compatibilidade de leitura. |
| Cálculo de hashes no JavaScript da página local | **Substituído** | Substituído pelo verificador Java independente. |
| Cópia automática de JAR/EXE do verificador para cada raiz capturada (proposta antiga) | **Substituído** | Implementado na 2.3.1-beta com validação de SHA-256 e cópia somente de versões aceitas. |
| Guardar somente JSONL como registro de auditoria | **Substituído** | Evoluiu para `integridade.json` (inventário + cadeia cumulativa); JSONL continua como log operacional do Re-Extrair. |
| Hash local como prova de imutabilidade/autoria | **Corrigido** | Hash local verifica consistência com a referência local; hash público externo permite comparação com checkpoint compartilhado, sem assinar autoria. |
| Versão 2.0.0 e hashes de builds de teste no documento antigo | **Substituído** | Substituídos por plugin 2.6.0-beta / verificador 1.3.5 e hashes oficiais em `CHECKSUMS.md`. |
| `censoredItemHashes` / `sensitiveMediaHashes` (inexistente no código) | **Não aplicado** | Nunca existiu; a censura usa cache por SHA-256 dentro de `runHtmlMessageCapture`. |

## 4. Captura HTML por mensagem (formato `html-message-v1`)

### 4.1 Formato e contrato

- Novas capturas usam `captureFormat: "html-message-v1"` no `manifest.json`.
- Conteúdo: `whatsapp.html`, `messages.json`, `manifest.json`, diretório `attachments/` quando o recurso não estiver embutido.
- Intervalo inclusivo entre as mensagens marcadas; mensagens auxiliares/interface não entram no inventário.
- Leitura polimórfica por `captureFormat`; capturas antigas (screenshots) permanecem verificáveis e não são convertidas automaticamente.
- Alvo fixo: IPED 4.3.1. Uma atualização de IPED exige revisão explícita do contrato e novos testes.

### 4.2 Marcadores e validação (2.5.2-beta)

- Separador de data como início é resolvido para a primeira mensagem seguinte; a ID resolvida vai ao manifesto como âncora persistente.
- Validação de IDs numéricos únicos e ordem do intervalo antes de instalar verificadores ou iniciar o worker; o worker repete a validação.
- Falhas de validação não atualizam os índices da pasta de capturas; HTML/PNG são preparados em staging e promovidos apenas quando o inventário está completo.
- Mensagens localizadas distinguem marcador ausente, ambíguo, fora de ordem e data sem mensagem seguinte.

### 4.3 Migração de capturas legadas (2.5.5/2.5.6-beta)

- Toggle opt-in "Migrar capturas legadas para HTML por mensagem" no Re-Extrair.
- Toggle dependente "Limpar registros e criar como nova captura": inicia nova captura em `capture_created`, descarta cadeia, reconciliação, auditoria e anotações anteriores.
- Preservação das anotações legadas em `legacy-annotations.json` quando o histórico é mantido, com registro de `migration.legacyAnnotationsPreserved` no manifesto.
- Reuso de anexos legados no modo limpo somente após conferir SHA-256 e tamanho; MIME, nome, tipo e caminho recalculados pela origem atual.
- Capturas já em `html-message-v1` com `changed`/`missingInCurrentSource`/`newInInterval` iguais a zero não são substituídas; auditoria registra `recapture_skipped_content_unchanged`.
- Promoção usa pasta temporária irmã, renomeia a pasta antiga para transacional e restaura o nome original se a promoção falhar.
- `migration.json` e `legacy-annotations.json` entram no inventário de `integridade.json`.

### 4.4 Limpeza da raiz no modo "Limpar registros" (2.5.8-beta)

- `archiveStaleRootIntegrityReports` removido; `deleteRootIntegrityReportsIfExists` apaga `integridade/` e `integridade.html` da raiz quando `resetLegacyRecords && replaced > 0`.
- `_backup_integridade_*` tratado como pasta auxiliar em `isRecaptureAuxiliaryFolder` e removido por `cleanupRecaptureAuxiliaryFolders`.
- A raiz fica no estado de uma captura recém-criada, sem vestígio de relatórios anteriores.

### 4.5 Censura por imagens de referência (2.5.8-beta)

- Censura visual por CSS puro + atributos HTML (`class="capture-media-link capture-censored"` e `data-capture-censored="1"`) em `html-message-v1` (captura nova, Re-Extrair e migração de legado), sem JavaScript adicional.
- `CaptureCensorship` com CSS na segunda `<style>` somente quando houver imagem censurada; miniatura e original casados por SHA-256 (cache positivo/negativo).
- `SensitiveImageMatcher` com sobrecarga para bytes em memória (`MatOfByte` / `Imgcodecs.imdecode` e hash exato MD5/SHA-256).
- Bloco `censorship` no `manifest.json` (`presentationDigest`, `images`, `cssPixels`, `levelPercent`, `similarityPercent`, `notice`).
- `messageHash` e fórmula de `captureRootHash` inalterados.
- Fail-closed: pasta configurada mas inutilizável, erro no matcher, imagem casada sem elemento HTML correspondente ou tentativa de Re-Extrair sem pasta sobre captura previamente censurada sem modo de cadeia limpa interrompem a operação.
- Re-Extrair compara `presentationDigest` além de `isHtmlReconciliationUnchanged`, substituindo a captura quando a censura é ligada/alterada/removida e registrando `recapture_censorship_presentation_changed`.
- Aviso: blur CSS é supressão visual de apresentação, não tarja irreversível; bytes originais permanecem em `attachments/`.

## 5. Apresentação e visualização de anexos (`whatsapp.html`)

- Apresentação autônoma de `html-message-v1` com tema WhatsApp preservado do IPED 4.3.1, limitada às mensagens selecionadas.
- Modal local para imagem, PDF, documentos e arquivos, com ações baixar/abrir; áudio/vídeo usam controles sem reprodução automática.
- Ao fechar o modal ou navegar para fora dele, a mídia é pausada e reiniciada (2.5.4-beta).
- CSP, CSS, anexos, SVGs e preview PDF validados pelo verificador; somente o script fixo com hash autorizado é aceito.
- Normalização do MIME de imagens embutidas no cabeçalho pela assinatura dos bytes (2.5.3-beta), corrigindo PNGs declarados como `image/jpg`.

## 6. Verificador de Integridade

### 6.1 Versão e compatibilidade

- Verificador 1.3.5: JAR Java 8+; EXE Windows x64 autocontido.
- Exportação pública anonimizada ("Copiar histórico completo público") no formato `CAPTURA|*-YYYYMMDD-HHMMSS` com cálculo próprio de **SHA-256 do histórico completo público**, e verificação dual no modo **Integridade Público** aceitando histórico completo original (`CAPTURA|<pasta>`), histórico anonimizado (`CAPTURA|*`) e hashes de ambos.
- Valida a cadeia local de integridade também em capturas `html-message-v1`, confere o vínculo de `migration.json` e `legacy-annotations.json` ao inventário e aceita registros de chamada (`_call:<hex>`) e carved/removidos (`-?<id>_<id>`).
- Mantém validação do script fixo de preview e o par anterior exato de script e CSP da 1.3.2; aceita a combinação legada `data:image/jpg` com assinatura PNG do IPED 4.3.1.
- Tipos MIME/assinaturas reconhecidos: imagens, áudio, vídeo, PDF, ZIP/OOXML, Office legado, RAR, 7z, texto/CSV e conteúdo genérico `.bin`.

### 6.2 Janela "Integridade Público" (2.5.8-beta)

- Removidos: campo "Manifesto JSON público de uma captura html-message-v1", botão "Comparar manifesto por mensagem", área `manifestResult`, botão "Copiar hash calculado" e texto "ou copie o hash calculado".
- Layout em 2 blocos: (1) Hash SHA-256 público, (2) Histórico completo `historico-capturas.txt`, mais botão "Continuar para o resumo".
- `comparePublicMessageManifest` (método estático package-private) mantido por compatibilidade com testes.

### 6.3 Paginação e seleção incremental (2.3.3-beta)

- Até 100 capturas por página no seletor do verificador e na janela Re-Extrair, com navegação anterior/próximo e contagem.
- Validação de detalhes de metadados sob demanda por página e em lotes de até 25 antes de executar os jobs selecionados.
- "Marcar todos" usa seleção global compacta; ao editar uma caixa, converte a seleção atual em IDs explícitos e novas capturas ficam desmarcadas.

### 6.4 Verificação no plugin (`VerifierIntegrity`)

- O plugin procura os binários em pastas conhecidas, verifica os hashes aceitos (allowlist) e copia os arquivos oficiais para a saída da captura.
- Se não estiverem disponíveis, o usuário pode copiá-los ao lado do índice.
- Alerta informativo (não bloqueante) uma vez por sessão em Start e Re-Extrair, com tom informativo (`INFORMATION_MESSAGE`).
- Hashes permitidos sincronizados com o verificador 1.3.5 (JAR `31B0CE72...`, EXE `75FB542B...`).

## 7. Segurança, memória e estabilidade

- Confinamento dos caminhos do `capturas-index.json` à raiz de saída; rejeita caminhos absolutos, segmentos ambíguos, links e entradas duplicadas sem comparação quadrática.
- Re-Extrair carrega metadados no diálogo em segundo plano, limita cada página a 16 MiB e mantém apenas IDs selecionados; jobs carregados individualmente durante o processamento.
- Inventário, histórico e metadados cumulativos respeitam limites de tamanho antes de substituir os registros anteriores.
- Limites globais para candidatos, itens e metadados selecionados; descoberta do verificador ocorre em segundo plano e pode ser cancelada.
- Relatórios acima do orçamento recusados antes da montagem; os cinco relatórios preparados e promovidos com rollback se alguma promoção falhar.
- HTML, JSON e texto usam escaping adequado; falhas de limite preservam os relatórios anteriores.
- Sanitização remove conteúdo ativo e URLs externas; anexos limitados, conferidos contra o hash do item IPED, verificados por assinatura/MIME e gravados dentro de `attachments/`.
- Dependência `org.json` 20260719 (CVE-2026-59171); limites de 16 MiB em JSON e 25.000 arquivos em inventários; hashes canônicos em fluxo.
- Leituras de manifestos e anotações limitadas para impedir consumo excessivo de memória.
- Plugin continua gerando bytecode Java 11; verificador JAR Java 8+.

## 8. Idiomas e instalador

- Bundles padrão em inglês e `pt_BR` dentro do JAR; instalador valida o JAR antes de substituir, cria backup, inclui bundles e mescla no diretório externo `iped/localization` apenas as chaves ausentes.
- Falha na instalação de idiomas tenta restaurar o JAR anterior.
- Instalador distribui o JAR modificado, bundles de idioma e validador.

## 9. Compatibilidade e distribuição

- Plugin: IPED 4.3.1, Java 11 como alvo mínimo. Verificador JAR: Java 8+. Verificador EXE: Windows x64, runtimes incluídos.
- Release beta contém: ZIP do instalador, JAR e EXE do verificador, ZIP `src-plugin` e ZIP `src-integridade-verifier`.
- Tamanhos e SHA-256 dos artefatos publicados em `CHECKSUMS.md`. Qualquer rebuild precisa recalcular e atualizar esses valores.
- O instalador distribui o JAR modificado, bundles de idioma e validador; cria backup antes de substituir o JAR instalado.
- Capturas novas usam `html-message-v1`; capturas legadas em screenshots permanecem compatíveis de leitura.

## 10. Notas sobre validação

- A maior parte das funcionalidades foi validada por testes automatizados de contrato (`teste_antes_de_aplicar/run_all_tests.ps1`), que passam na versão atual.
- A validação manual dentro do IPED 4.3.1 (interface gráfica) depende de execução em ambiente real e permanece pendente para alguns fluxos (captura, Re-Extrair, migração, censura).
- "Implementado" não significa que todos os cenários foram validados em uma sessão gráfica real do IPED.