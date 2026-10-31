# Especificação consolidada — Chat Captura IPED

**Versão deste documento:** 1.0  
**Estado de referência:** plugin `2.3.0-beta`; verificador independente `1.2.1`  
**Base da revisão:** código, testes e documentação presentes no repositório em 2026-10-31.

Este documento substitui as especificações dispersas do projeto. Ele descreve o comportamento confirmado no código, registra as diferenças em relação às propostas antigas e separa o que ainda não está implementado. “Implementado” significa que há código correspondente; não significa que todos os cenários foram validados em uma sessão gráfica real do IPED.

## 1. Legenda de situação

- **Implementado:** comportamento presente no código distribuído.
- **Parcial:** existe parte da estrutura, mas falta o comportamento de ponta a ponta descrito.
- **Fora da versão 2.3.0-beta:** consta de proposta anterior, mas não foi entregue nesta versão. Não deve ser anunciado como recurso disponível.
- **Substituído:** uma decisão posterior mudou a forma de atender ao requisito antigo.
- **Decisão futura:** não há evidência suficiente de que o requisito foi cancelado pelo usuário. Mantê-lo aqui como item não entregue, sem tratá-lo como compromisso ativo.

## 2. Auditoria das especificações anteriores

| Documento anterior | Situação apurada | Decisão consolidada |
|---|---|---|
| `ESPECIFICACAO_NOTAS_LINKS.md` | Parcialmente aplicado e revisado durante a implementação. Notas, vínculos, preflight, exportação e leitura legada têm código. O modelo antigo fala em vincular pessoas/contatos, enquanto o código atual vincula **chats** (`LinkedChatRef`, `chatRefId`). | A especificação pessoa/contato foi **substituída** por vínculo entre chats. Campos antigos como `contactId` continuam apenas como compatibilidade de leitura. A versão atual deste documento define o comportamento vigente. |
| `ESPEC_ANOTAR_CHAT_SALVO.md` | Implementado com alterações de interface. Há seleção de chat salvo, carregamento das anotações e gravação explícita; o botão atual é `Salvar Trecho-Notas`. | Aplicado com o fluxo e rótulos atuais. A gravação não acontece a cada digitação: fica em memória até salvar explicitamente ou realizar captura. |
| `ESPEC_COMPATIBILIDADE_JAVA11.md` | Correção aplicada. Nesta revisão, as 256 classes do JAR do plugin 2.3.0-beta foram lidas diretamente e todas têm major version 55. | Mantido como requisito permanente de build. Os hashes antigos de builds de teste foram substituídos pelos valores atuais em `CHECKSUMS.md`. |
| `ESPEC_COORDENADAS_MIDIA_TRANSCRICAO.md` | **Parcial.** `mediaX/Y/Width/Height` e `textX/Y/Width/Height` são calculados e gravados nas coordenadas. O HTML ainda posiciona o botão `T` usando os limites gerais da mensagem; não consome os retângulos separados. | Não declarar posicionamento preciso dos botões como concluído. A parte de armazenamento está aplicada; a parte visual permanece incompleta, sem evidência de cancelamento explícito. |
| `ESPEC_REEXTRAIR_INCREMENTAL_AUDITAVEL.md` | O núcleo está implementado: fingerprint, assinatura por mensagem/frame, inventário, atalho para captura inalterada, regeneração permitida de `whatsapp.html`, recaptura integral para outras divergências e auditoria. O `integridade.json` mantém inventário e eventos encadeados. | Consolidado nas seções 6 e 7. Similaridade visual não autoriza pular atualização. O histórico registra hashes, não arquiva os bytes antigos. |
| `ESPEC_VERIFICADOR_INTEGRIDADE.md` | Verificador independente implementado como JAR e EXE, com modos local/público, seleção de capturas, hashes e relatórios. O fluxo de distribuição atual pede que a pessoa coloque o JAR ou EXE ao lado do índice. | O usuário pediu anteriormente que o plugin copie e valide automaticamente ambos os executáveis em cada raiz de captura, mas o código da 2.3.0-beta não entrega isso. É uma solicitação pendente, não uma funcionalidade implementada nem um pedido cancelado. O botão “Histórico de registro” no card do índice também não foi localizado no código atual. |

Não foi encontrada outra solicitação explicitamente cancelada pelo usuário nas especificações analisadas. Requisitos ausentes do código são chamados de “fora da versão”, “parciais” ou “decisão futura”, em vez de serem atribuídos como rejeitados pelo usuário.

### 2.1 Mudanças entre propostas antigas e o comportamento atual

| Proposta/assunção anterior | Estado atual |
|---|---|
| Vincular contatos/pessoas à mensagem e abrir conversa por identidade do contato. | Substituído por referência a chats e trechos de captura; a identidade pessoal não é inferida pelo vínculo. |
| Anotações só persistem quando se captura. | Ampliado: o fluxo de chat salvo tem ação explícita `Salvar Trecho-Notas`; as alterações não salvas continuam em RAM. |
| Fazer a página local calcular todos os hashes diretamente no JavaScript. | Substituído pelo verificador Java independente, que lê os arquivos; o HTML apresenta o resultado. |
| O plugin copiar automaticamente JAR/EXE para cada raiz capturada. | Não entregue na 2.3.0-beta; README orienta cópia manual. Não há registro explícito de cancelamento pelo usuário. |
| Guardar somente JSONL como registro de auditoria. | Evoluiu: `integridade.json` contém o inventário atual e cadeia cumulativa; JSONL continua como log operacional de Re-Extrair. |
| Hash local como prova de imutabilidade/autoria. | Corrigido: hash local verifica consistência com a referência local; hash público externo permite comparação com um checkpoint compartilhado, sem assinar autoria. |
| Versão 2.0.0 e hashes de builds de teste no documento antigo. | Substituídos por plugin 2.3.0-beta/verificador 1.2.1 e hashes oficiais registrados em `CHECKSUMS.md`. |

## 3. Compatibilidade e distribuição

- Plugin: IPED `4.3.1`, Java `11` como alvo mínimo. O JAR indicado em `CHECKSUMS.md` tem 256 classes, todas com major version `55` nesta revisão.
- Verificador JAR: Java `11` ou superior.
- Verificador EXE: Windows x64; contém os runtimes necessários e não exige Java instalado na máquina de execução.
- A release beta contém cinco downloads: ZIP do instalador, JAR e EXE do verificador, ZIP `src-plugin` e ZIP `src-integridade-verifier`.
- Tamanhos e SHA-256 dos artefatos publicados ficam em `CHECKSUMS.md`. Qualquer rebuild precisa recalcular e atualizar esses valores.
- O instalador distribui o JAR modificado, bundles de idioma e validador. Ele cria backup antes de substituir o JAR instalado.

### 3.1 Fluxo base do plugin

O analista marca o início e o fim do trecho, define a pasta de saída e inicia a captura. O plugin gera frames PNG, coordenadas/metadata, conteúdo HTML e relatórios. A opção de censura compara thumbnails com uma pasta de referência; os controles existentes incluem similaridade e intensidade do blur. No diálogo de `Re-Extrair`, o botão alterna entre `Marcar todos` e `Desmarcar todos` para as capturas selecionáveis.

O fluxo-base está documentado no README e no viewer. Os valores atuais dos seletores e o comportamento do IPED continuam sendo fonte da verdade do código; esta especificação não cria uma nova opção visual.

## 4. Anotações e vínculos entre chats

### 4.1 Modelo atual

O recurso é opcional. O analista pode adicionar nota de texto e vincular um ou mais chats à mensagem. Vínculos são referências a chats, não afirmações de identidade de uma pessoa. Dados antigos com campos de contato podem ser lidos por compatibilidade, mas novas gravações usam a referência de chat.

As anotações são armazenadas em `anotacoes.json`, organizadas por chat e chave de mensagem. A leitura de arquivo ausente não cria arquivo. Alterações ficam em memória até a ação de salvar ou a captura; a gravação usa arquivo temporário e substituição atômica. Em **Anotar chat salvo**, o analista escolhe uma captura existente, abre o chat de origem quando disponível, edita e usa **Salvar Trecho-Notas**. Depois de salvar, `Re-Extrair` é necessário para atualizar os HTMLs/imagens derivados da captura quando aplicável.

### 4.2 Interface e exportação

- As ações de anotação dependem da posição da mensagem em relação às marcas de início/fim e não se aplicam a separadores de data.
- O preflight informa anotações dentro/fora do intervalo; confirmar exclui do resultado as que estão fora, cancelar preserva os dados.
- A interface do viewer e `whatsapp.html` mostram notas e chats vinculados sem inserir os ícones na imagem PNG capturada.
- Um chat vinculado pode exibir seu trecho quando há captura. Um vínculo sem captura mostra indicação apropriada.
- `manifest.json`, `whatsapp-coordinates.json` e `texto.txt` recebem os dados de anotação conforme o fluxo atual; quando não há anotações, os campos opcionais podem ficar ausentes.
- `anotacoes.json` é dado de trabalho do analista, não evidência original; o manifesto identifica essa condição.

## 5. Coordenadas de mídia e texto

O capturador calcula coordenadas da mensagem e, quando detecta subelementos, coordenadas independentes para mídia e texto/transcrição. Os campos são serializados para os dados da captura.

**Lacuna atual:** a página gerada usa o retângulo da mensagem para o hotspot e posiciona o botão de transcrição no limite do bloco. Os campos `mediaX/Y/Width/Height` e `textX/Y/Width/Height` não são usados para posicionar esses controles. Assim, a gravação de coordenadas está implementada, mas o requisito de sobreposição precisa sobre os subelementos não está concluído.

Capturas antigas sem esses campos continuam legíveis porque o HTML usa a área geral do bloco. A compatibilidade deve ser preservada ao concluir a sobreposição.

## 6. Re-Extrair incremental e auditável

### 6.1 Identidade e decisão

A captura grava fingerprint de origem/intervalo/configuração, versão do plugin/geradores, assinaturas semânticas de mensagens e frames, hashes SHA-256 dos arquivos e inventário. Transcrição auxiliar de IA não participa da identidade de áudio/vídeo. Para mídia, o hash do arquivo original é necessário para uma assinatura completa; sem ele, não se deve afirmar igualdade confirmada.

O fluxo pode pular o trabalho somente quando origem e fingerprint conferem, o inventário está íntegro e não há mudança de gerador/formato que exija migração. Hashes exatos autorizam a decisão; similaridade visual não serve como prova de igualdade.

### 6.2 Reutilização, recaptura e auditoria

- `whatsapp.html` pode ser regenerado usando `whatsapp-coordinates.json` válido quando os dados existentes bastam.
- Para frame, anexo ou outros artefatos cuja regeneração seletiva não esteja implementada, divergência ou ausência leva à recaptura completa pelo fluxo atual.
- A recaptura usa staging e promoção com recuperação em caso de falha. Uma falha não deve publicar evento como concluído.
- `reextracao-auditoria.jsonl` registra operações de Re-Extrair e hashes esperados/observados. O `integridade.json` mantém o inventário atual e eventos com sequência, ação, mudanças, raiz de conteúdo e referência ao evento anterior.
- Um arquivo ausente pode ser recriado durante recaptura. Hash igual ao anterior confirma igualdade com o valor registrado; hash diferente indica conteúdo diferente. O histórico não mantém cópia dos bytes anteriores e não restaura conteúdo histórico por si só.
- Capturas legadas ou sem dados suficientes seguem recaptura completa; não são ignoradas por falta de metadados.

### 6.3 Limites do histórico

O histórico é uma cadeia de hashes, não uma assinatura digital. Quem altera os arquivos e todos os registros locais pode recalcular a cadeia. Um hash/registro público guardado fora da pasta permite comparar a cópia com aquele ponto de referência.

A implementação atual calcula `recordHash` sobre a representação textual do evento JSON e o verificador valida a mesma regra. Antes de tratar isso como formato interoperável entre implementações independentes, a serialização deve ser formalizada/canônica e coberta por vetores de teste compartilhados. O SHA-256 público do relatório determinístico do verificador é um formato separado (`HISTORICO_PUBLICO_V1`).

## 7. `integridade.json` e verificador independente

### 7.1 Registro da captura

Cada captura nova recebe `integridade.json` com versão de schema, algoritmo SHA-256, versão do plugin, inventário relativo de caminhos/tamanhos/hashes, raiz de conteúdo e eventos. O próprio `integridade.json`, `manifest.json` e o log JSONL ficam fora do inventário que ele descreve, evitando autorreferência e mudanças circulares. O verificador recalcula o conteúdo no disco e compara com o inventário.

### 7.2 Operação do verificador

O verificador funciona sem IPED. Localiza a raiz pela própria pasta ou por argumento de diretório, lista capturas encontradas e permite selecionar uma, várias ou todas. Ele calcula hashes atuais e aponta itens íntegros, divergentes, ausentes, extras ou não verificáveis, sem corrigir arquivos.

- **Integridade Local:** compara os arquivos com o registro local e valida a cadeia registrada. Isso confirma consistência com a referência local, não autenticidade do autor.
- **Integridade Público:** compara o hash do histórico completo ou o texto público fornecido com o resultado determinístico recalculado. A referência pública precisa vir de fora da pasta verificada para servir de checkpoint externo.
- Os resultados incluem hash por captura, hash do conjunto selecionado e histórico completo copiável. A interface deixa os botões de cópia junto aos hashes para caber na janela.
- O relatório `integridade.html` informa quando foi gerado, apresenta hashes e comparação pública e permite consultar/filtrar resultados. O HTML apresenta o resultado do programa; não substitui o verificador como autoridade de leitura e cálculo dos arquivos.
- Arquivos auxiliares ficam na pasta `integridade/`, ignorada como captura e no cálculo do índice. O EXE extrai runtimes para uma pasta temporária exclusiva por execução e limpa seus próprios resíduos sem remover arquivos de outra instância ativa.

### 7.3 Instalação na raiz das capturas

Na versão entregue, a pessoa baixa o JAR ou EXE e o coloca manualmente ao lado de `index.html` e `relatorio.htm`. O plugin **não** copia os verificadores automaticamente durante captura ou Re-Extrair. Essa integração não pode aparecer como funcionalidade concluída nesta versão.

## 8. Arquivos e formatos principais

| Arquivo | Função |
|---|---|
| `manifest.json` | Metadados da captura, origem, configuração, versões e referências do conteúdo. |
| `whatsapp-coordinates.json` | Frames, blocos, coordenadas, dados para HTML e assinaturas. |
| `anotacoes.json` | Notas e referências a chats, persistidas pelo analista. |
| `integridade.json` | Inventário SHA-256 e histórico cumulativo da captura. |
| `reextracao-auditoria.jsonl` | Eventos de execução/auditoria de Re-Extrair. |
| `hashes.txt` | Lista legada dos hashes exportados; não substitui o inventário completo. |
| `index.html`, `relatorio.htm` | Índice e relatório legados da coleção/capturas. |
| `integridade.html` | Relatório criado pelo verificador Java independente. |
| `integridade/historico-capturas.txt` | Registro público determinístico gerado pelo verificador. |

O hash de pasta legado do índice e a raiz de conteúdo do `integridade.json` usam regras diferentes. Sempre identificar algoritmo/versão e não comparar valores de esquemas diferentes como se fossem equivalentes.

## 9. Segurança e interpretação dos resultados

- SHA-256 identifica igualdade de bytes em relação a um valor conhecido; sozinho não identifica autor, hora, nem impede edição.
- Registro local consistente significa que os arquivos conferem com o registro presente na pasta. Conteúdo e registro podem ter sido substituídos juntos.
- Comparação pública significa que o valor recalculado confere com o código/registro fornecido. A confiança depende de obter essa referência por canal externo confiável.
- O verificador não deve alterar, restaurar ou “consertar” arquivos durante a checagem.
- Caminhos de inventário devem permanecer dentro da pasta de captura; caminhos absolutos, travessia (`..`) e links que escapem da raiz são recusados.
- Estados devem distinguir pelo menos: consistente localmente, confere com referência pública, divergente, arquivo ausente/extra, registro inválido/quebrado, legado/não verificável e sem referência externa.
- Não exibir selo verde sem dizer qual referência foi usada.

## 10. Matriz de aceite e estado conhecido

| Verificação | Evidência no projeto/release | Estado |
|---|---|---|
| Plugin e viewer em bytecode Java 11 | Versão distribuída e verificação de major version 55 documentada; JAR da versão identificado em `CHECKSUMS.md`. | Implementado; o histórico documenta validação do pacote. |
| Anotações e vínculo entre chats | `ChatAnnotationStore`, diálogos, seleção de chat salvo, exportação no HTML/manifesto. | Implementado com o modelo de chat atual; especificação antiga de contatos substituída. |
| Anotar chat salvo e salvar sem recaptura imediata | `SavedChatPickerDialog`, `annotateSavedChat()` e `saveAnnotations()`. | Implementado com botão atual `Salvar Trecho-Notas`. |
| Coordenadas específicas de mídia/texto | Campos calculados e gravados; não usados na posição do botão `T`. | Parcial. |
| Assinatura e inventário do plugin | `CaptureIntegrity`, inventário, fingerprint, hashes por frame/mensagem e auditoria. | Implementado no código; verificação de uso em GUI real não cobre todos os cenários. |
| Re-Extrair sem mudança | Comparação de fingerprint/inventário e registro de captura ignorada. | Implementado no código. |
| Regenerar somente `whatsapp.html` | Regeneração a partir das coordenadas salvas. | Implementado para esse artefato; demais divergências usam recaptura completa. |
| Histórico em `integridade.json` | Eventos, `previousRecordHash`, `recordHash`, alterações e inventário; verificador valida a cadeia. | Implementado localmente; não é assinatura e a serialização requer formalização para interoperabilidade. |
| Verificador JAR/EXE e modos Local/Público | Código, CHANGELOG, binários publicados e checksums. | Implementado na versão 1.2.1. |
| Comparação pública e cópia de histórico/hash | UI Java e relatório HTML gerado pelo programa. | Implementado; depende de referência obtida externamente para confiança externa. |
| Copiar JAR/EXE automaticamente pelo plugin | Não há rotina nos fontes do plugin. | Fora da versão entregue; fluxo atual é cópia manual. |
| Botão “Histórico de registro” no card do índice | Não localizado no HTML do índice gerado. | Não implementado na versão entregue. |
| Teste completo de Re-Extrair pela UI 2.3.0-beta | A especificação anterior registra compilação/testes utilitários e declara que não reproduziu toda a reextração em GUI real. | Não comprovado aqui; não alegar validação integral. |


### 10.1 Cenários de aceite que devem continuar cobertos

| Área | Cenário | Resultado esperado | Estado de validação registrado |
|---|---|---|---|
| Anotações | Captura sem nota/vínculo, toggle desligado ou toggle ligado sem itens | Captura segue sem annotations opcionais; leitura não cria arquivo de anotação vazio. | Comportamento documentado e implementado; regressão gráfica total não comprovada nesta revisão. |
| Anotações | Anotação fora do intervalo e ação de cancelar/confirmar | Cancelar preserva; confirmar exclui somente as fora e registra contagens. | Implementado no preflight; cenários devem continuar no roteiro. |
| Anotações | Nota com HTML, aspas e texto longo | Mostrar texto escapado sem executar marcação e persistir sem corromper o JSON. | O HTML usa escape; teste de interface longo não confirmado aqui. |
| Vínculos | Um chat, vários chats e chat sem captura | Um vínculo abre trecho quando existe; vários exibem seleção; vínculo sem captura informa ausência sem quebrar página. | Código presente; teste manual integral não reexecutado aqui. |
| Anotar chat salvo | Origem encontrada, origem indisponível, editar e salvar | Carregar captura existente; falhar de forma controlada sem origem; salvar anotações sem exigir recaptura imediata. | Classes e métodos presentes; fluxo de UI não reexecutado nesta revisão. |
| Legado | Captura sem schema/campos novos | Leitor mantém fallback e não afirma que campos inexistentes foram verificados. | Fallback presente no projeto; compatibilidade ampla depende de amostras legadas. |
| Coordenadas | Áudio/vídeo com transcrição e captura antiga sem retângulos | Overlay preciso usa sub-retângulos; dados antigos usam retângulo geral. | Primeiro requisito ainda parcial: retângulos são gravados, mas o HTML não os usa no posicionamento. |
| Re-Extrair | Fingerprint e arquivos intactos | Marcar captura inalterada somente depois de verificar origem, opções e hashes. | Caminho existe; execução em UI IPED real não comprovada nesta revisão. |
| Re-Extrair | Somente `whatsapp.html` inválido/ausente e coordenadas válidas | Regenerar HTML, validar resultado e auditar hash sem recapturar frames. | Caminho implementado em código; teste gráfico não reexecutado aqui. |
| Re-Extrair | Frame/anexo ausente ou alterado, captura antiga ou manifesto incompleto | Não presumir igualdade; executar recaptura completa e registrar resultado/hash anterior e recuperado quando disponível. | Caminho implementado; sem arquivo antigo em bytes, o histórico não restaura conteúdo antigo por si só. |
| Re-Extrair | Falha antes/durante promoção | Manter a captura anterior e não publicar evento como concluído. | Lógica staged/rollback presente; falha real não simulada nesta revisão. |
| Verificador | Captura íntegra, arquivo alterado, ausente, extra e registro inválido | Classificar cada caso sem modificar os arquivos. | Testes/utilitários e verificação de capturas foram registrados antes do release; não repetidos nesta revisão. |
| Verificador | Referência pública correta/incorreta, histórico colado, várias capturas | Comparar hash ou conteúdo completo e mostrar a referência usada; permitir copiar hashes/histórico. | UI/código presentes; canal de confiança continua externo. |
| Verificador | Reexecução e duas instâncias do EXE | Substituir relatório somente ao terminar; runtimes temporários não colidem nem apagam instância ativa. | Rotina e autoteste do EXE registrados no histórico de release; não reexecutados nesta revisão. |
| Distribuição | JAR/EXE/fontes e ZIP do instalador | Cinco assets publicados e checksums correspondentes em `CHECKSUMS.md`. | Conferido na release `v2.3.0-beta`. |
## 11. Itens futuros sem compromisso de implementação

Estes itens ficam registrados para evitar que propostas antigas sejam confundidas com recursos atuais. Não serão implementados automaticamente por constarem aqui:

1. Posicionar hotspots de áudio/vídeo e botão de transcrição usando os retângulos específicos já gravados.
2. Decidir se o índice precisa de um atalho **Histórico de registro** e qual página/fluxo deve abrir.
3. Implementar a cópia e validação automáticas do JAR/EXE pelo plugin, conforme solicitação anterior; a versão atual ainda orienta cópia manual.
4. Formalizar serialização canônica de `recordHash` com vetores de teste que possam ser reproduzidos fora da implementação Java atual.
5. Testar Re-Extrair 2.3.0-beta em interface IPED real para captura intacta, arquivo ausente/alterado, regeneração de HTML e falha/rollback.

## 12. Documentos e evidências mantidos

Esta especificação substitui os documentos `ESPECIFICACAO_NOTAS_LINKS.md`, `ESPEC_ANOTAR_CHAT_SALVO.md`, `ESPEC_COMPATIBILIDADE_JAVA11.md`, `ESPEC_COORDENADAS_MIDIA_TRANSCRICAO.md`, `ESPEC_REEXTRAIR_INCREMENTAL_AUDITAVEL.md` e `ESPEC_VERIFICADOR_INTEGRIDADE.md`. As implementações e testes continuam nos diretórios `src/`, `tests/` e `verificador-integridade/`; este documento é a única especificação consolidada, não substitui código, changelog ou checksums.