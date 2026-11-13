# Changelog — Chat Captura IPED

Mudanças relevantes do plugin.

## [Não Lançado] - Em desenvolvimento

## [2.6.1-beta] - 2026-11-13

### Segurança

- Anotações (`ChatAnnotationStore`): corrigida dupla decodificação que alterava o texto das notas ao recarregar.
- Verificador 1.3.6: detecta arquivos extras em capturas `html-message-v1`; rejeita links/junções NTFS; valida HTML por allowlist de tags/atributos (regex ciente de aspas); valida e escapa campos do histórico (`historico-capturas.txt`).
- OpenCV só é carregado de caminhos do IPED com SHA-256 conferido (`HtmlLinkViewer`).
- Nomes exportados passam por `sanitizeFileName` único (reservados do Windows, controles, `.`/`..`) e anexos são gravados com `CREATE_NEW`.
- Instalador: `IPED_ROOT` resolvido antes do uso, abort/rollback em falhas, caminhos passados por variável de ambiente ao PowerShell, SHA-256 esperado do plugin e do verificador, e cópia do verificador para `<IPED>\verificador-integridade\`.
- `VerifierIntegrity`: removidos caminhos absolutos de desenvolvimento; allowlist aceita somente o Verificador 1.3.6.

---

## [2.6.0-beta] - 2026-11-12

### Adicionado

- Verificador de Integridade **1.3.5**: novo botão **"Copiar histórico completo público"** e exibição do **SHA-256 do histórico completo público** no resumo, exportando linhas anonimizadas no formato `CAPTURA|*-YYYYMMDD-HHMMSS ...` para publicação externa com maior privacidade.
- Verificação dual no modo **Integridade Público** do Verificador 1.3.5: aceita e compara tanto o histórico completo original (`CAPTURA|<pasta>-YYYYMMDD-HHMMSS ...`) quanto o histórico completo público anonimizado (`CAPTURA|*-YYYYMMDD-HHMMSS ...`), bem como o hash SHA-256 de qualquer um dos dois formatos.
- Opção `--export-history` do Verificador 1.3.5 também emite `SHA-256_HISTORICO_PUBLICO=...`.
- Prevenção de travamento no `verificador-integridade/build-single-exe.ps1` com `MSBUILDDISABLENODEREUSE=1`, `DOTNET_CLI_DO_NOT_USE_MSBUILD_SERVER=1`, `-m:1 -nodeReuse:false` e `dotnet build-server shutdown`.

### Alterado

- `VerifierIntegrity.java` e `VerifierIntegrityHashTest.java` sincronizados com os binários oficiais do Verificador **1.3.5** (`31B0CE72...` e `75FB542B...`).
- Versão do plugin elevada para `2.6.0-beta` em `HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION`, `META-INF/MANIFEST.MF` (`Implementation-Version: 2.6.0-beta`) e `instalar_captura_chat_zap.bat`.

---

## [2.5.9-beta] - 2026-11-12

### Adicionado

- `CaptureBoundaryResolver`, `HtmlLinkViewer` e Verificador 1.3.4 agora aceitam qualquer ponto inicial/final válido no WhatsApp: mensagens numéricas (`^\d+$`), registros de chamada (`^\d+_call:[0-9a-fA-F]+$`), mensagens removidas/carved (`^-?\d+_\d+$`) e timestamps internos (`iped-capture-date-...`), normalizando automaticamente `data-iped-capture-id` e `domId`.
- Fat-JAR autocontido com classes `org/opencv/**` embutidas em `iped-viewers-impl-4.3.1.jar`, busca da DLL nativa `opencv_java4120.dll` na raiz do IPED / `plugins/` / `lib/` e `Implementation-Version: 2.5.9-beta` no `META-INF/MANIFEST.MF`.
- Tratamento tolerante de anexos em `html-message-v1` (pula anexos não suportados sem abortar a captura).
- Script `verificador-integridade/build-single-exe.ps1` atualizado com `--disable-build-servers -p:UseSharedCompilation=false` para evitar travamento de servidores MSBuild/Roslyn.

### Corrigido

- Falha ao re-extrair ou migrar capturas legadas no Re-Extrair quando o ponto inicial/final aponta para registro de chamada (`_call:<hex>`), timestamp interno da linha ou ID negativo/removido (`-?<id>_<id>`).
- `VerifierIntegrity.java` e `VerifierIntegrityHashTest.java` sincronizados com os binários do Verificador 1.3.4 recompilados (`B456074C...` e `20FAF8A5...`).
- Instalador atualizado para exibir `Versao do plugin: 2.5.9-beta`.

---

## [2.5.8-beta] - 2026-11-09

### Adicionado

- Censura visual por imagens de referência (blur) em capturas `html-message-v1` (captura nova, Re-Extrair e migração de legado), implementada em `CaptureCensorship` com CSS puro e atributos HTML (`class="capture-media-link capture-censored"` e `data-capture-censored="1"`), sem JavaScript adicional e mantendo total compatibilidade com o Verificador de Integridade 1.3.4.
- Sobrecarga em `SensitiveImageMatcher` para casamento direto sobre bytes em memória (`MatOfByte` / `Imgcodecs.imdecode` e hash exato MD5/SHA-256), com cache por SHA-256 em `runHtmlMessageCapture` cobrindo anexos do caso (`resources`) e miniaturas inline (`media`).
- Registro auditável `censorship` em `manifest.json` (com `presentationDigest`, `images`, `cssPixels`, `levelPercent`, `similarityPercent` e aviso explícito de que o blur CSS é supressão visual de apresentação, não tarja irreversível), mantendo `messageHash` e a fórmula de `captureRootHash` inalterados.

### Modificado

- A migração de capturas legadas para `html-message-v1` no Re-Extrair aceita pasta de imagens de referência e exibe aviso de confirmação quando executada sem pasta de referência.
- O Re-Extrair compara `presentationDigest` além de `isHtmlReconciliationUnchanged`, substituindo a captura quando a censura é ligada, alterada ou removida (via modo de cadeia limpa) e registrando `recapture_censorship_presentation_changed`.
- A janela **Integridade Público** do Verificador de Integridade 1.3.4 foi reorganizada para exibir apenas o campo de hash SHA-256 público e o campo único de `historico-capturas.txt` (removendo o campo duplicado de manifesto `html-message-v1` e o botão de cópia de hash calculado).
- No Re-Extrair, a opção "Limpar registros e criar como nova captura" agora apaga diretamente a pasta `integridade/` e o arquivo `integridade.html` da raiz quando capturas são substituídas, sem criar pastas `_backup_integridade_*`, deixando a raiz limpa como uma captura nova.

### Segurança

- Comportamento *fail-closed* para censura em `html-message-v1`: pasta de referência configurada porém sem imagens válidas ou sem OpenCV disponível (`CensorshipReferenceUnavailable`), erro no matcher, imagem casada sem elemento HTML correspondente ou tentativa de Re-Extrair sem pasta sobre captura previamente censurada sem o modo de cadeia limpa (`CensorshipWouldBeLost`) interrompem a operação sem promover captura sem censura.

## [2.5.7-beta] - 2026-11-09

### Corrigido

- O Re-Extrair arquiva a pasta `integridade/` da raiz quando alguma captura é realmente substituída. Os relatórios derivados de uma execução anterior deixavam de representar o conjunto atual e sugeriam uma cadeia que não existe mais; a pasta é renomeada para `_backup_integridade_<data>`, sem apagar evidência, e o próximo Verificador gera um retrato novo.
- O índice geral não marca mais `annotations.legacy` em capturas `html-message-v1`; o campo `version` do manifesto só identifica anotações legadas nos formatos antigos. Isso mantém o resultado de um Re-Extrair indistinguível de uma primeira captura.
- Somente capturas efetivamente promovidas contam para o arquivamento dos relatórios da raiz; intervalos ignorados por conteúdo inalterado não alteram a pasta `integridade/`.

## [2.5.6-beta] - 2026-11-09

### Corrigido

- A janela **Re-Extrair capturas** agora calcula largura e altura pela área útil do monitor onde o IPED está aberto, descontando a barra de tarefas. Em 1366×768, o diálogo fica com cerca de 879×638 px e cresce proporcionalmente em Full HD, 2K e 4K.
- O toggle **Limpar registros e criar como nova captura** agora fica disponível também para capturas novas em `html-message-v1`, sem depender do toggle de migração. Ao limpar uma captura nova, o Re-Extrair ignora o atalho de conteúdo inalterado e recaptura o intervalo.
- A validação da candidata limpa rejeita `migration.json`, `legacy-annotations.json`, `message-reconciliation.json` e `reextracao-auditoria.jsonl` em qualquer reset, seja de captura nova ou legada migrada.
- Capturas `html-message-v1` não são mais classificadas internamente como legadas pela ausência do campo `version` no manifesto; isso reabilita **Incluir Notas e Links** em capturas novas.

### Modificado

- O reset de capturas legadas continua exigindo a migração para HTML por mensagem; o reset de capturas novas não exige migração.
- A especificação e os testes de contrato foram atualizados para a nova matriz de decisão (`HTML_RESET`).

## [2.5.5-beta] - 2026-11-08

### Adicionado

- Re-Extrair pode migrar capturas legadas em frames para `html-message-v1` por meio do toggle opt-in **Migrar capturas legadas para HTML por mensagem**, disponível em todas as páginas da seleção.
- Toggle dependente **Limpar registros e criar como nova captura**, que inicia a nova captura em `capture_created` e descarta a cadeia, os registros de reconciliação, a auditoria e as anotações da captura anterior.
- Preservação das anotações legadas em `legacy-annotations.json` quando o histórico é mantido, com registro de `migration.legacyAnnotationsPreserved` no manifesto; as anotações não são vinculadas automaticamente às mensagens novas.
- Reuso de anexos legados no modo limpo somente após conferir SHA-256 e tamanho; MIME, nome, tipo e caminho são recalculados pela origem atual.
- Verificador de Integridade 1.3.4, que valida a cadeia local também em capturas `html-message-v1` e exige o vínculo de `migration.json` e `legacy-annotations.json` ao inventário.

### Modificado

- A primeira migração registra a proveniência da captura legada e não afirma que as mensagens dos frames eram iguais às da fonte atual; depois da migração, a comparação por hash de mensagens permite ignorar intervalos sem alteração.
- Capturas já em `html-message-v1` com `changed`, `missingInCurrentSource` e `newInInterval` iguais a zero não são substituídas; a candidata é descartada e a auditoria registra `recapture_skipped_content_unchanged`.
- A promoção usa pasta temporária irmã, renomeia a pasta antiga para uma pasta transacional e restaura o nome original se a promoção falhar.

### Segurança

- A migração é bloqueada antes de processar o primeiro item quando há pasta de imagens de referência para censura, pois o formato HTML ainda não aplica blur por similaridade.
- `migration.json` e `legacy-annotations.json` entram no inventário de `integridade.json`; o Verificador detecta alteração no registro de migração mesmo que o manifesto seja atualizado junto.
- Nenhum arquivo da pasta legada é alterado antes de a candidata passar na validação de manifesto, HTML, mensagens, anexos e inventário.

## [2.5.4-beta] - 2026-11-07

### Corrigido

- Fechar o preview de imagem, áudio, vídeo, documento ou arquivo restaura a posição anterior do chat; os controles “Fechar” e “×” usam a navegação nativa do fragmento para fechar o modal no WebView do IPED.
- O script fixo do HTML captura a rolagem ao abrir o preview e a restaura depois do evento `hashchange`, preservando a pausa de áudio e vídeo ao fechar.

## [2.5.3-beta] - 2026-11-07

### Corrigido

- O cabeçalho preservado do IPED agora normaliza o MIME de cada imagem embutida pela assinatura dos bytes. Isso corrige avatares PNG fornecidos como `data:image/jpg`, que eram rejeitados pelo Verificador como imagem embutida HTML inválida.
- Adicionado teste de regressão que confirma a rejeição do MIME divergente e a aceitação da imagem normalizada pelo Verificador 1.3.2.

## [2.5.2-beta] - 2026-11-07

### Corrigido

- O menu não permite marcar um separador de data como mensagem final; datas continuam válidas como início e são resolvidas para a primeira mensagem seguinte.
- Start valida IDs numéricos únicos e a ordem do intervalo antes de instalar verificadores ou iniciar o worker; o worker repete a validação para detectar mudanças no DOM.
- Falhas de validação não atualizam os índices da pasta de capturas. Capturas HTML e PNG são preparadas em staging e só promovidas quando o inventário está completo.
- Mensagens localizadas distinguem marcador ausente, ambíguo, fora de ordem e data sem mensagem seguinte.
- O instalador confere os bundles já compilados e atualiza os idiomas externos sem reescrever o JAR.

## [2.5.1-beta] - 2026-11-07

### Corrigido

- Captura HTML não falha ao validar o SVG do tema WhatsApp com o Xerces 2.12.2 do IPED 4.3.1; o parser rejeita DTD e referências externas usando recursos compatíveis com essa implementação.
- Pacote mantém `org.json` 20260719 também com o nome legado `json-20250517.jar`, evitando `NoClassDefFoundError` em classpaths fixos do IPED.
- Testes de contrato acompanham a assinatura atual do sanitizador SVG.

## [2.5.0-beta] - 2026-11-07

### Adicionado

- Apresentação autônoma de `html-message-v1` com tema WhatsApp preservado do IPED 4.3.1, limitada às mensagens selecionadas.
- Modal local para imagem, PDF, documentos e arquivos, com ações de baixar/abrir; áudio e vídeo usam controles sem reprodução automática.

### Corrigido

- Ao fechar o modal ou navegar para fora dele, a mídia é pausada e reiniciada.
- CSP, CSS, anexos, SVGs e preview PDF são validados pelo Verificador 1.3.1; somente o script fixo com hash autorizado é aceito.

## [2.4.1-beta] - 2026-11-07

### Corrigido

- Marcador de início colocado sobre uma data agora resolve para a primeira mensagem seguinte, registra a ID numérica no manifesto para o Re-Extrair e evita `start_anchor_missing_or_ambiguous` e capturas vazias.
- Seleção da origem do Verificador agora exige SHA-256 autorizado; cópias antigas em `iped/lib` não são propagadas para a pasta de captura.
- Substituição dos verificadores inválidos usa arquivo temporário, confere o hash e promove o arquivo somente após a validação.

## [2.4.0-beta] - 2026-11-07

### Adicionado

- Formato `html-message-v1` para novas capturas: HTML offline do intervalo, `messages.json`, chaves e hashes canônicos por mensagem e anexos locais identificados por SHA-256.
- Verificação específica do formato HTML por mensagem no Verificador de Integridade 1.3.0, incluindo ordem, IDs, caminhos confinados, hashes de anexos e hash-raiz.
- Extração de anexos que o HTML do caso referencia por `app.open('sha-256:...')`, incluindo imagem, áudio, vídeo, PDF, Office, arquivos compactados e itens genéricos `.bin` suportados.
- Reconciliação do Re-Extrair com registro por mensagem: inalterada, alterada, nova, ausente na fonte atual ou ambígua.

### Modificado

- Re-Extrair reconhece capturas HTML e localiza as âncoras pelos IDs DOM registrados antes de promover uma captura temporária.
- A impressão digital da origem usa os bytes do HTML fonte; anexos são resolvidos pelo hash SHA-256 do item do caso.
- A propriedade `-Diped.chat.capture.htmlMessageV1=false` desativa novas capturas no formato novo durante a beta. Capturas já em `html-message-v1` continuam usando Re-Extrair nesse formato.
- A censura por similaridade ainda não foi portada para HTML; a captura HTML é interrompida com aviso enquanto uma pasta de blur estiver ativa, evitando salvar mídia sem a censura solicitada.

### Segurança

- A sanitização remove conteúdo ativo e URLs externas; anexos são limitados, conferidos contra o hash do item IPED, verificados por assinatura/MIME e gravados dentro de `attachments/`.

## [2.3.6-beta] - 2026-11-06

### Otimizado

- Lista do Re-Extrair organizada verticalmente, com espaçamento compacto e apenas o nome da pasta.

### Corrigido

- Corrigida a falha que impedia a janela de seleção do Re-Extrair de abrir quando os descritores descartavam as âncoras.
- Atualizados os hashes aceitos para o Verificador 1.2.8; binários antigos 1.2.7 deixam de ser aceitos.

### Adicionado

- Plugin Chat Captura IPED 2.3.6-beta e pacote do instalador atualizado.
- Verificador de Integridade 1.2.8 em JAR e EXE; hashes publicados em `versao_hash.md`.

## [2.3.5-beta] - 2026-11-06

### Segurança

- Confinamento dos caminhos do `capturas-index.json` à raiz de saída; rejeita caminhos absolutos, segmentos ambíguos, links e entradas duplicadas sem comparação quadrática.
- O Re-Extrair carrega metadados no diálogo em segundo plano, limita cada página a 16 MiB e mantém apenas IDs selecionados; jobs são carregados individualmente durante o processamento.
- Inventário, histórico e metadados cumulativos passam a respeitar limites de tamanho antes de substituir os registros anteriores.
- Hashes permitidos sincronizados com o Verificador 1.2.8.

### Corrigido

- Verificador troca a extração de campos por regex pelo parser JSON com limite de profundidade, evitando `StackOverflowError` em caminhos longos.
- Limites globais para candidatos, itens e metadados selecionados; descoberta do verificador ocorre em segundo plano e pode ser cancelada.
- Relatórios acima do orçamento são recusados antes da montagem; os cinco relatórios são preparados e promovidos com rollback se alguma promoção falhar.
- HTML, JSON e texto usam escaping adequado; falhas de limite preservam os relatórios anteriores.

### Modificado

- Plugin continua gerando bytecode Java 11. O Verificador 1.2.8 mantém o JAR Java 8 ou superior e o EXE x64 autocontido.

## [2.3.4-beta] - 2026-11-06

### Modificado

- Atualiza os hashes aceitos para o Verificador 1.2.7, cujo JAR agora funciona com Java 8 (1.8) ou superior.
- O plugin permanece compilado para Java 11, conforme o runtime do IPED 4.3.1.

## [2.3.3-beta] - 2026-11-06

### Modificado

- Seletor do Verificador de Integridade e janela Re-Extrair agora apresentam até 100 capturas por página, com navegação anterior/próxima e contagem.
- A janela Re-Extrair valida detalhes de metadados sob demanda por página e em lotes de até 25 antes de executar os jobs selecionados.
- `Marcar todos` usa seleção global compacta; ao editar uma caixa, converte a seleção atual em IDs explícitos e novas capturas ficam desmarcadas.
- Verificador atualizado para 1.2.6; hashes aceitos pelo plugin sincronizados com os binários construídos.

## [2.3.2-beta] - 2026-11-06

### Segurança

- Atualizada a dependência `org.json` para 20260719, corrigindo o processamento de números JSON sem limite associado ao CVE-2026-59171.
- Verificador e plugin limitam arquivos JSON a 16 MiB e inventários a 25.000 arquivos; hashes canônicos são calculados em fluxo.
- Gravações de relatórios, inventários e auditoria rejeitam links simbólicos e usam arquivos temporários exclusivos.
- Instalador substitui a biblioteca JSON antiga por uma versão corrigida e mantém backups para reversão.

### Corrigido

- Leituras de manifestos e anotações limitadas para impedir consumo excessivo de memória.
- Hashes dos binários permitidos pelo plugin sincronizados com o Verificador 1.2.5.

## [2.3.1-beta] - 2026-11-06

### Adicionado

- Release beta separada com instalador ZIP, JAR compilado do plugin e código-fonte versionados.
- Verificador de Integridade 1.2.4 disponível como JAR e EXE; hashes publicados no README, CHECKSUMS.md e versao_hash.md.

### Corrigido

- Cópia de hashes e histórico executada em segundo plano, evitando travamento da janela.
- Plugin aceita somente os hashes SHA-256 dos binários oficiais 1.2.4.



## [2.3.0-beta] - 2026-11-06

### Adicionado

- Distribuição do JAR do plugin e do Verificador de Integridade em JAR/EXE como arquivos separados da release.
- Histórico de hashes do verificador por versão em `versao_hash.md`.
- Documentação consolidada da arquitetura, uso, artefatos gerados, integridade e validação do plugin.

### Corrigido

- Cópia de hashes e do histórico para a área de transferência executada em segundo plano, evitando travar a janela do verificador.
- Hashes reconhecidos pelo plugin sincronizados com os binários estáveis do verificador 1.2.4.

### Modificado

- README atualizado com os hashes SHA-256 atuais e instruções para consultar o histórico de versões.
- README organizado com seções de apresentação, instalação, uso e licença.
- Instalador ZIP recomposto com o JAR do plugin e os arquivos de instalação atuais.
