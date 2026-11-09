# Especificação — apresentação e visualização de anexos no `whatsapp.html`

**Estado:** aplicada ao plugin e ao Verificador; testes automatizados concluídos, validação manual da janela do IPED pendente.  
**Alvo:** plugin Chat Captura IPED para IPED 4.3.1 e capturas `html-message-v1`.  
**Escopo:** renderização do `whatsapp.html`, abertura/visualização de anexos e validação correspondente no Verificador de Integridade.  
**Regra de execução:** primeiro validar cada etapa com protótipo e testes isolados em `teste_antes_de_aplicar`; somente depois de todos os resultados aprovados aplicar a mudança em `src` e `verificador-integridade`.

## 1. Problema observado

O gerador atual de `html-message-v1`, em `src/iped/viewers/HtmlLinkViewer.java` (`runHtmlMessageCapture`), cria uma página simplificada com fundo branco, fonte Arial e mensagens genéricas. Durante a extração, remove `<style>`, atributos `style`, `id` e referências de estilo do DOM copiado. Com isso, a saída perde as cores, o fundo, a disposição e os balões usados pelo WhatsApp no IPED 4.3.1.

Para anexos, alguns links do fragmento são convertidos diretamente em links de download, e um cartão genérico adicional pode ser acrescentado abaixo da mensagem. Essa saída não recupera a visualização usada pelo `writeWhatsAppHtml` legado: imagem em visualizador, ações de baixar/abrir e controles de áudio/vídeo. O visualizador legado depende de screenshots e coordenadas e, portanto, não pode ser reutilizado sem adaptação.

O Verificador em `verificador-integridade/src/IntegrityVerifier.java` confere o hash de `whatsapp.html`, os anexos do manifesto e referências locais, além de rejeitar scripts, `iframe`, `object` e conteúdo remoto. A captura de referência atual foi executada com `--verify-only` e é rejeitada porque o verificador também bloqueia `<meta>`, embora o gerador atual inclua `<meta charset>` e uma política CSP. A correção deve aceitar apenas os metadados exatos de charset/CSP gerados pelo plugin e continuar rejeitando qualquer outro `<meta>`.

O WebView real do IPED 4.3.1 confirmou no HTML de exemplo: um `<style>` embutido com 88.129 caracteres, 17 URLs raster `data:image` e quatro URLs SVG de ícones de status (`div.unsent`, `.sent`, `.delivered`, `.viewed`), sem `@import` nem URL externa/relativa. A folha pode ser preservada após validar MIME, base64 e limites; os SVGs devem ser analisados como XML e rejeitados se tiverem script, `foreignObject`, atributos de evento ou referências externas. Como esses bytes ficam dentro de `whatsapp.html`, o hash do próprio HTML já os cobre. Usar `renderAssets` e incluí-los no hash-raiz somente se a implementação precisar extrair recursos de apresentação para arquivos separados.

## 2. Resultado esperado

O `whatsapp.html` de uma captura nova deve:

1. Manter a aparência de conversa do WhatsApp/IPED 4.3.1: fundo ou papel de parede, cabeçalho de conversa, alinhamento dos balões recebidos/enviados, cores, texto, remetente, horário e separadores de data/sistema quando presentes no intervalo.
2. Exibir exclusivamente as mensagens selecionadas e os recursos necessários a elas. Não carregar o HTML original em tempo de execução nem expor mensagens ou anexos não selecionados.
3. Abrir uma visualização local ao selecionar mídia/anexo, com ações visíveis de **Baixar**, **Abrir em nova aba** quando aplicável e **Fechar**.
4. Exibir áudio e vídeo em controles nativos do navegador, sem reprodução automática.
5. Funcionar sem rede, a partir de `file:///`, sem caminhos absolutos da máquina de origem.
6. Continuar verificável pelo Verificador sem alterar a leitura de capturas antigas baseadas em screenshots nem a interpretação dos hashes por mensagem.

## 3. Comportamento por tipo de conteúdo

### 3.1 Imagem

- Manter na mensagem a miniatura que já fazia parte do conteúdo capturado, quando houver.
- Ao ativá-la, abrir um painel modal HTML dentro do `whatsapp.html` e mostrar a imagem original em tamanho ajustável, limitada à área visível.
- Exibir **Baixar imagem**, **Abrir em nova aba** e **Fechar**. O link de abrir nova aba deve usar `rel="noopener noreferrer"`.
- Se a miniatura e o original forem arquivos diferentes, registrar e validar ambos sem duplicar cartões de ação na mensagem.

### 3.2 PDF

- Abrir um painel/modal de pré-visualização com o PDF local, quando o WebView/navegador suportar a visualização incorporada.
- Oferecer sempre **Baixar PDF**, **Abrir em nova aba** e **Fechar**. Se a visualização incorporada não estiver disponível, mostrar uma indicação clara e manter as ações de abrir/baixar funcionais.
- Um `iframe` só pode ser usado se estiver isolado por `sandbox` sem permissões (`sandbox=""`), apontar para um PDF listado no manifesto e passar pelas regras do Verificador. Nenhum `allow-scripts`, `allow-same-origin`, URL externa ou conteúdo HTML arbitrário é permitido.

### 3.3 DOC, DOCX e outros arquivos

- O painel mostra nome seguro, tipo MIME, tamanho e, quando disponível, SHA-256, com ações **Baixar**, **Abrir em nova aba** e **Fechar**.
- Não prometer renderização de formatos que o navegador não renderiza. Não converter documentos silenciosamente nem carregar visualizador remoto. A abertura em nova aba é uma tentativa do navegador; o download permanece disponível.
- Tipo MIME e extensão devem vir dos dados validados da captura, sem confiar apenas no nome fornecido pelo HTML.

### 3.4 Áudio e vídeo

- Abrir o painel/modal com `<audio controls preload="none">` ou `<video controls preload="none">`, usando somente o arquivo local validado.
- Não usar `autoplay`; fechar o painel deve pausar e zerar a posição do áudio/vídeo. O protótipo confirmou que CSS por fragmento não para a mídia. A saída usa somente um script fixo do plugin para interromper mídia ao fechar/navegar, autorizado por um único hash SHA-256 na CSP. O Verificador compara o conteúdo exato desse script e rejeita qualquer script adicional ou alterado.
- Fornecer **Baixar**, **Abrir em nova aba** e **Fechar**.

### 3.5 Arquivo sem pré-visualização

Exibir cartão compacto associado à mensagem com nome, tipo/tamanho e as ações disponíveis. Cada anexo deve aparecer uma única vez no contexto da mensagem, evitando o link original mais um segundo cartão duplicado.

## 4. Diretrizes de implementação

### 4.1 Preservar o tema sem copiar a conversa inteira

- Usar o DOM do IPED 4.3.1 como referência para gerar uma página autônoma com estrutura de conversa e estilos necessários.
- O protótipo deve comparar duas estratégias: regras CSS mínimas derivadas do contrato 4.3.1 ou estilos computados, filtrados por uma allowlist de propriedades visuais. Escolher a estratégia que passar nos testes visuais e de segurança.
- Não copiar o documento original inteiro, folhas de estilo sem filtragem, scripts, formulários, controles do IPED, mensagens fora do intervalo, dados de outros chats, IDs globais originais nem caminhos absolutos.
- Só recursos do tema realmente necessários (por exemplo, papel de parede) podem ser incluídos. Recursos visuais compartilhados ficam registrados como recursos da captura, separados dos anexos pertencentes a mensagens.
- CSS final não pode conter `@import`, `expression()`, URLs remotas/absolutas, handlers, referências `file:` ou funções que busquem conteúdo fora da captura. Dados `data:image` só são aceitos em base64 com MIME raster permitido ou SVG validado e dentro dos limites; o hash de `whatsapp.html` cobre esses dados embutidos. Se um recurso visual precisar ser extraído para arquivo, copiá-lo sob nome derivado do SHA-256 e registrá-lo em `renderAssets`.
- Preservar as classes semânticas necessárias, mas atribuir chaves de mensagem únicas da captura; não depender de IDs DOM originais para localizar modais ou mensagens na página final.

### 4.2 Interação sem conteúdo ativo não confiável

- Usar links de fragmento gerados pelo plugin e CSS controlado para abrir/fechar os painéis, junto dos controles nativos de mídia. O único JavaScript permitido é o script fixo de interrupção da mídia, com hash SHA-256 explícito na CSP; não incluir JavaScript copiado do IPED nem handlers `on*`.
- A CSP permite apenas o hash desse script fixo. O Verificador compara seu conteúdo byte a byte com a constante conhecida; nenhum atributo, script extra ou alteração de bytes é aceito.
- O HTML final não pode buscar rede (`connect-src 'none'`), usar formulários, navegar para `javascript:`, carregar fontes/scripts externos ou abrir conteúdo não inventariado.
- Para links externos a anexos, restringir os atributos à combinação aprovada (`target="_blank"`, `rel="noopener noreferrer"` e `download` quando aplicável). Links de modal devem ser fragmentos locais gerados, com alvo único existente.

### 4.3 Manifesto e Verificador

- Anexos continuam registrados por mensagem com caminho relativo, MIME, tamanho e SHA-256. Recursos compartilhados de apresentação extraídos para arquivos (se houver) devem ter campo próprio, por exemplo `renderAssets`, com caminho relativo, MIME, tamanho e hash. Recursos `data:image` validados e mantidos dentro do CSS pertencem ao hash do HTML, não à lista de anexos de mensagem.
- Se `renderAssets` for usado, o hash-raiz deve cobrir seus hashes, além dos componentes já protegidos. Os hashes por mensagem não devem mudar apenas porque o tema ou o layout foi alterado.
- Para capturas existentes sem `renderAssets` ou sem a versão de apresentação, manter exatamente o cálculo de hash atual. Não reescrever capturas antigas automaticamente.
- O Verificador deve aceitar apenas referências locais que correspondam aos caminhos e hashes do manifesto: `src`/`poster` de imagem ou mídia, `href` para anexo ou fragmento local e `iframe` somente no caso restrito de PDF definido na seção 3.2.
- Permitir somente `<meta charset="utf-8">` e uma `<meta http-equiv="Content-Security-Policy">` com a política estática e o hash do script fixo gerados pelo plugin. Rejeitar `http-equiv="refresh"`, qualquer `<meta>` adicional ou variação que amplie as fontes/origens permitidas.
- Validar que cada fragmento de modal tem um único destino; que cada recurso referenciado existe e está inventariado; que não há referências externas/absolutas, elementos ativos não autorizados, recursos extras ou anexos sem vínculo válido.
- Alteração em CSS, HTML, recurso visual, mensagem ou anexo deve ser detectada pelo hash apropriado. Falhas de parsing, MIME, caminhos e limites devem produzir erro controlado, nunca travar a interface.
- O verificador deve manter a política de limites já definida para HTML, metadados e anexos; novos recursos visuais contam nos limites de quantidade e bytes.

### 4.4 Compatibilidade e limites de escopo

- Aplicar a nova apresentação apenas a capturas `html-message-v1` geradas após a mudança.
- Preservar o fluxo legado de screenshot, seu visualizador, relatórios e verificação sem migração automática.
- Manter a seleção, identidade/hash de mensagem, Re-Extrair, anotações e reconciliação. Alterar apenas o gerador de apresentação e as regras do Verificador necessárias para validar a saída nova.
- O contrato é específico para IPED 4.3.1. A atualização futura do IPED exige novo contrato e nova validação, sem ampliar este escopo.

## 5. Plano obrigatório de testes antes da aplicação

### Regra de bloqueio

Antes de editar código de produção em `src/` ou `verificador-integridade/src/`, criar e executar um protótipo isolado em `teste_antes_de_aplicar/renderizacao_whatsapp/`. Cada etapa abaixo precisa demonstrar o resultado esperado. Se alguma falhar ou revelar requisito contraditório, ajustar esta especificação e o protótipo/teste afetado, executar novamente essa etapa e todas as etapas dependentes. **Não iniciar a implementação de produção até todos os testes prévios estarem aprovados.**

O protótipo não substitui os testes de produção. Depois de aplicar, todos os mesmos critérios deverão ser repetidos no código real e nos binários compilados.

### Etapa A — contrato visual do IPED 4.3.1

- Usar o HTML de origem conhecido `IPED_testes_chat_real/Exportados/arquivos/F/A/FAF3132301A706022E1C3EAD6AE0800B.html` e capturas anonimizadas de conversas da mesma versão, se disponíveis.
- Inventariar estrutura do cabeçalho, fundo, balões recebidos/enviados, texto, horário, remetente e separadores; medir estilos computados no IPED para uma viewport fixa.
- Gerar referência visual e validar, por screenshot e inspeção DOM, que o tema derivado preserva essas propriedades sem depender de caminhos de origem.
- **Aprovação:** cabeçalho e mensagens de ambos os sentidos são distinguíveis; cores, fundo, tipografia, espaçamento e horários correspondem ao IPED de referência; nenhum conteúdo fora da seleção aparece.

### Etapa B — seleção e HTML autônomo

- Criar casos de intervalo mínimo, intervalo com início/fim e intervalo com separadores.
- Validar conteúdo, ordem e contagem contra `messages.json`; confirmar ausência de mensagens não selecionadas, IDs originais duplicáveis, scripts, eventos e chamadas ao HTML original.
- Repetir geração para verificar determinismo, exceto campos explicitamente temporais.
- **Aprovação:** HTML offline exibe exatamente o intervalo e os recursos visuais necessários, sem revelar conteúdo alheio.

### Etapa C — anexos e ações

- Fixtures para miniatura + original, imagem única, PDF, DOC/DOCX, arquivo genérico, áudio e vídeo; incluir referência ausente, MIME falso e nome com caracteres HTML.
- Automatizar clique nas ações: abrir/fechar modal, baixar, abrir nova aba, carregar imagem/PDF quando suportado e reproduzir controles de áudio/vídeo sem autoplay.
- Confirmar que a mesma mensagem não recebe cartões/links duplicados e que fechamento não deixa áudio/vídeo tocando.
- **Aprovação:** cada tipo tem apresentação e fallback definidos na seção 3, sem erro silencioso ou chamada remota.

### Etapa D — segurança do HTML/CSS e do Verificador

- Tentar injetar scripts além do script fixo, alteração de qualquer byte dele, `on*`, `javascript:`, `file:`, URL remota, `@import`, `url()` externo, SVG com script/`foreignObject`/evento/referência externa, meta de refresh, CSP ampliada, fragmento inexistente/duplicado, iframe remoto, iframe com permissões de sandbox, link `target=_blank` sem `noopener`, MIME incorreto, path traversal e symlink.
- Testar adulteração de CSS, recurso visual, HTML, PDF e mídia após a geração.
- **Aprovação:** somente charset/CSP canônicos são permitidos; entradas inseguras são rejeitadas; referência permitida é confinada à captura, inventariada e hash-validada; nenhuma tentativa executa script, acessa a origem ou consulta rede.

### Etapa E — abertura offline no motor real

- Abrir o resultado por `file:///` no WebView utilizado pelo IPED 4.3.1 e em navegador de referência. Bloquear/monitorar rede e observar carregamento dos recursos.
- Testar navegação de fragmento, painel modal, foco/teclado básico, fechar, nova aba, download, imagem/PDF e controles de áudio/vídeo.
- **Aprovação:** fluxo principal funciona sem rede; limitações de PDF/formato são comunicadas e as ações alternativas funcionam. Se o WebView não suportar um recurso exigido, retornar à etapa pertinente, rever a solução na especificação e validar de novo antes da aplicação.

### Etapa F — compatibilidade e regressão

- Reproduzir como baseline a falha da captura de referência atual (meta bloqueado); depois verificar a página corrigida e capturas `html-message-v1` sem os novos campos de apresentação.
- Executar a suíte legada baseada em screenshots, Re-Extrair, reconciliação, anotações e limites de recursos.
- **Aprovação:** a captura nova passa no verificador; os formatos anteriores mantêm hashes/resultado e não são reescritos.

### Execução e evidência

Criar os testes isolados com nomes rastreáveis às etapas, por exemplo `test_visual_contract.py`, `test_selected_html.py`, `test_attachment_viewers.py`, `test_html_security.py`, `test_offline_webview.py` e `test_legacy_compatibility.py`, além de um `run_rendering_spec_tests.ps1` que encerre com código diferente de zero se qualquer teste falhar. Guardar no diretório de teste um resumo de cada execução, motor/versão, fixtures usadas, resultados e falhas corrigidas. Não registrar hashes de evidência ou caminhos pessoais desnecessários nos relatórios.

## 6. Aplicação após aprovação prévia

Quando todas as etapas A–F do protótipo estiverem aprovadas:

1. Aplicar no plugin um gerador separado para a apresentação HTML, com tema e componentes de visualização definidos nesta especificação.
2. Adaptar o Verificador para a lista de recursos visuais, modais locais e pré-visualização restrita de PDF, preservando a regra histórica para capturas anteriores.
3. Executar a suíte isolada e os testes existentes contra o código de produção; corrigir qualquer divergência e repetir os testes dependentes.
4. Compilar o Verificador JAR/EXE se seu código mudar; calcular SHA-256 dos binários finais; atualizar a allowlist no plugin; compilar o plugin depois da allowlist.
5. Executar novamente a suíte com os binários finais, confirmar hashes, inspeção do ZIP/JAR e abrir uma captura nova e uma captura legada no IPED 4.3.1.
6. Atualizar versão, documentação, checksums e histórico de hashes conforme a política vigente somente depois que as verificações finais passarem.

Se qualquer teste de produção falhar, não declarar a correção concluída nem substituir a instalação de teste por um pacote não validado.

## 7. Critérios finais de aceite

- A página nova preserva o estilo de conversa do IPED 4.3.1 e contém somente o intervalo capturado.
- Imagem abre pré-visualização; PDF usa pré-visualização segura quando suportada e sempre tem fallback; DOC/arquivo oferece metadados e abrir/baixar sem alegação falsa de renderização; áudio/vídeo têm controles sem autoplay.
- Todos os recursos são locais, limitados, inventariados e cobertos por hashes; o Verificador aceita o HTML legítimo e rejeita conteúdo adulterado ou externo.
- Nenhum anexo aparece em duplicidade na mensagem; fechar painel não deixa mídia tocando.
- Capturas legadas continuam verificáveis e inalteradas.
- As etapas A–F foram aprovadas primeiro no protótipo; os testes automatizados de produção e binários passaram. A validação interativa indicada na seção 8 ainda está pendente.

## 8. Registro da aplicação e validação

- A implementação do plugin foi aplicada em `src/iped/viewers/HtmlLinkViewer.java`; o Verificador 1.3.1 valida a apresentação correspondente e preserva o formato antigo.
- O protótipo isolado passou nas etapas A–F novamente em 2026-11-07, usando o JavaFX WebView distribuído com IPED 4.3.1 para a validação offline.
- A suíte passou: 6 testes das etapas A–F, 7 testes do protótipo de captura, 24 testes de aceitação, 6 testes end-to-end, 16 testes Java do verificador, 2 testes do plugin/verificadores e 4 verificações do contrato do renderizador/JAR compilado.
- O JAR Java 8 passou na suíte e verificou a captura existente `captura-chat-20261107-090152` como consistente; o EXE 1.3.1 passou em `--self-test` (código 0). A allowlist do plugin corresponde aos hashes registrados neste repositório.
- A compilação do plugin IPED 4.3.1, JAR Java 8 e EXE Windows x64 foi concluída.
- **Validação manual pendente:** não foi aberta uma captura recém-gerada pelo plugin nesta execução dentro da interface completa do IPED. A verificação do JAR/EXE e o teste WebView offline cobrem os componentes, mas não substituem esse teste de fluxo interativo.
