# Especificação técnica — paginação e seleção incremental

**Estado:** pronta para implementação  
**Escopo:** seletor do Verificador de Integridade e janela Re-Extrair do plugin Chat Captura IPED  
**Objetivo:** mostrar muitas capturas sem montar uma interface ou carregar o conteúdo de todas elas ao mesmo tempo.

## 0. Versões, compilação e entrega para teste

Esta mudança altera o verificador e o plugin. Após a implementação estar concluída e validada:

- Incrementar a versão beta do verificador e do plugin, mantendo os números sincronizados com seus fontes, manifests, documentação, changelogs e instalador. Para a base atual, usar o próximo patch beta: verificador `1.2.6` e plugin `2.3.3-beta`.
- Compilar primeiro o verificador JAR e EXE na nova versão.
- Calcular os SHA-256 dos dois binários resultantes e conferir que correspondem aos arquivos compilados.
- Atualizar no código do plugin os hashes permitidos para JAR e EXE. Remover os hashes correntes anteriores quando não forem mais aceitos pela política da versão.
- Compilar o plugin somente depois de atualizar os hashes.
- Recalcular o SHA-256 do JAR do plugin; atualizar README, changelog, `CHECKSUMS.md`, `versao_hash.md` e pacotes com esses valores e versões.
- Preparar cópias do plugin compilado e do verificador JAR/EXE na árvore de teste `C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\IPED_testes_chat_real`, nos locais de distribuição que o instalador/plugin usa.
- Não sobrescrever os binários de teste sem guardar cópia de segurança. Registrar os caminhos atualizados e os hashes para que o usuário possa validar a instalação e testar manualmente no IPED.
- Não publicar release nem considerar a alteração entregue antes de o usuário concluir o teste manual e informar o resultado.

**Ordem obrigatória de build:** implementar → incrementar versões → compilar verificador JAR/EXE → calcular hashes do verificador → atualizar hashes no plugin → compilar plugin → recalcular hash do plugin → atualizar documentação/pacotes → instalar cópias na árvore de teste → entregar ao usuário para validação.

## 1. Comportamento esperado

- A interface mostra até **100 capturas por página**.
- A descoberta começa em segundo plano. Enquanto ativa, o indicador informa `Procurando… N capturas encontradas`, atualizando `N` à medida que novos itens válidos são encontrados.
- Após o fim da busca, o indicador mostra `Exibindo A–B de N capturas` e informa que a busca terminou.
- `Anterior` fica desativado na página 1. `Próximo` fica desativado somente quando a busca terminou e não há itens para outra página. Durante a busca, `Próximo` fica ativo quando já há pelo menos um item disponível para a página seguinte.
- Trocar de página não perde marcações.
- Nenhum manifesto, JSON de integridade, anexo ou conteúdo de conversa é carregado para compor a lista. A listagem usa apenas caminho, nome e estado de elegibilidade.

## 2. Descoberta em duas fases

O seletor não pode determinar elegibilidade completa apenas por nomes: hoje o Re-Extrair precisa abrir e validar metadados em `loadRecaptureJobs`. Portanto, separar “encontrado” de “validado”; não prometer que todo metadado de controle nunca será lido durante a descoberta.

### Fase 1 — localizar

- Enumerar diretórios filhos e coletar somente `captureId`, caminho relativo e nome.
- No verificador, checar somente a presença dos marcadores de captura aceitos (`manifest.json`, `integridade.json`, `whatsapp.html`, `relatorio.htm` ou `texto.txt`); não abrir JSON nem calcular hashes.
- No plugin, listar diretórios candidatos sem chamar `loadRecaptureJobs` para todos. Não abrir HTML, coordenadas, anexos ou conteúdo de conversa.
- Publicar descritores em lotes e atualizar `Procurando… N encontrados` em tempo real.

### Fase 2 — validar metadados em lotes sob demanda

- Ao exibir uma página, validar seus candidatos em segundo plano, em sublotes de 25.
- No verificador, carregar metadados necessários para reconhecer inventário/estado; hashes e conteúdo de arquivos só são lidos após Confirmar.
- No plugin, chamar a lógica atual de `loadRecaptureJobs` para candidatos da página, preservando critérios, compatibilidade e mensagens. Guardar jobs somente para páginas visitadas recentemente; recarregar detalhes se necessário.
- Cada linha mostra `Validando…`, `Válida`, `Não elegível` ou `Erro de leitura`. Contagem de candidatos e de itens elegíveis são exibidas separadamente.
- Antes de Confirmar, terminar validação dos candidatos selecionados. Em `ALL_FUTURE`, completar a Fase 1 e validar os restantes em lotes, com progresso, antes de iniciar verificação/reextração.

O catálogo não carrega bytes de evidências, anexos ou conversas. A Fase 2 lê apenas metadados de controle necessários à elegibilidade; hashes e conteúdo integral pertencem ao pipeline após Confirmar.

## 3. Identidade e catálogo

Cada captura recebe um `captureId` estável definido como o caminho relativo normalizado à pasta raiz da busca, com separadores `/`. Comparar IDs sem diferenciar maiúsculas/minúsculas no Windows. Não usar posição, contador ou nome isolado como identidade.

O catálogo fornece os seguintes campos leves:

```text
CaptureDescriptor {
  captureId: String
  displayName: String
  eligible: Boolean
  eligibilityReason: Optional<String>
}
```

O contador mostrado ao usuário é a posição de descoberta e não faz parte do `captureId`.

O produtor de descoberta publica descritores em lotes. O consumidor guarda páginas compactas para preservar marcações, nunca bytes das capturas. Constantes comuns: `PAGE_SIZE = 100` e `VALIDATION_BATCH_SIZE = 25`.

## 4. Descoberta assíncrona

Implementar um produtor de descritores desacoplado da UI:

```text
CaptureDiscovery.start(root, candidatePolicy, listener) -> DiscoveryHandle
DiscoveryListener.onCandidates(List<CaptureDescriptor>)
CaptureValidator.validate(List<CaptureDescriptor>) -> List<ValidatedDescriptor>
DiscoveryListener.onFinished(totalCandidates, optionalErrors)
DiscoveryHandle.cancel()
```

- Executar varredura em executor de fundo; publicar atualizações na UI via `SwingUtilities.invokeLater`.
- Fechar `DirectoryStream`/`Files.list` em todos os caminhos.
- Verificar diretórios e arquivos de controle sem ler seu conteúdo; aplicar `NOFOLLOW_LINKS` onde a política exigir diretório físico.
- Ignorar as pastas que o verificador já exclui (`vinculado`, backups, lixo temporário e pastas de reextração incompleta).
- Aplicar elegibilidade na Fase 2; no plugin, preservar os critérios atuais de `loadRecaptureJobs`.
- Cancelamento interrompe a varredura e libera recursos. Fechar a janela cancela a descoberta.
- Erro de leitura em uma pasta não encerra a busca inteira: registrar a pasta como ignorada, continuar e apresentar uma contagem/aviso final.

O produtor de Fase 1 entrega até 100 descritores por publicação. A contagem atualiza a cada publicação. A página é reconstruída com seus descritores e a Fase 2 valida em sublotes de até 25.

## 4. Modelo de seleção

Estado por diálogo:

```text
SelectionState {
  mode: ALL_FUTURE | EXPLICIT
  selectedIds: Set<String>    // usado em EXPLICIT
}
```

### 4.1 `Marcar todos`

- Ao ativar, definir `mode = ALL_FUTURE` e limpar `selectedIds`.
- Todas as capturas elegíveis, existentes ou descobertas depois, aparecem marcadas.
- A UI não percorre todas as caixas para marcar uma a uma; cada página renderiza o estado a partir do modo.
- A seleção final em `ALL_FUTURE` é resolvida sobre o catálogo completo somente após a busca terminar.

### 4.2 Mudança manual

- Qualquer ação manual sobre uma captura desativa `ALL_FUTURE`.
- Construir `selectedIds` com as capturas elegíveis já encontradas que estavam marcadas antes da ação manual; aplicar a alteração acionada pelo usuário; definir `mode = EXPLICIT`.
- Depois disso, capturas descobertas futuramente aparecem desmarcadas. O usuário pode navegar até elas e marcá-las.
- Ao mudar de página, as caixas refletem `selectedIds`; nunca redefinir seleção a partir apenas dos itens visíveis.
- Em `EXPLICIT`, o botão em massa alterna para `ALL_FUTURE` ao escolher **Marcar todos**. Se a seleção explícita estiver vazia, **Marcar todos** entra em `ALL_FUTURE` diretamente.

Esse comportamento implementa a regra pedida: uma escolha manual encerra o “todos, inclusive os que ainda serão encontrados” e preserva a seleção pelos IDs.

## 5. Estado dos controles

| Estado | Anterior | Próximo | Confirmar |
|---|---|---|---|
| Busca ativa, primeira página | Desativado | Ativo se já existe item na página seguinte | Ativo se há pelo menos uma captura elegível selecionada ou `ALL_FUTURE` tem elegíveis |
| Busca ativa, página posterior | Ativo | Ativo se existe item na seguinte; caso contrário aguarda a busca ou desativa ao terminar | Mesma regra |
| Busca concluída, primeira página | Desativado | Ativo se `total > PAGE_SIZE` | Ativo se seleção não vazia |
| Busca concluída, última página | Ativo se página > 1 | Desativado | Ativo se seleção não vazia |
| Busca cancelada/erro fatal | Conforme páginas carregadas | Desativado | Confirmar somente itens descobertos e mostrar aviso de busca incompleta; padrão recomendado é cancelar confirmação e pedir nova tentativa |

Se o usuário pedir uma página ainda não descoberta, manter os itens atuais na tela, mostrar atividade e navegar assim que a página tiver conteúdo. Não deixar a ação sem resposta visual.

## 6. Fluxo do Verificador

Ponto atual de integração: `verificador-integridade/src/IntegrityVerifier.java`, métodos `discover`, `chooseCaptures` e `verify`.

1. Substituir `discover(Path)` que constrói uma `List<Capture>` integral por descoberta incremental de `CaptureDescriptor`.
2. Substituir `chooseCaptures(List<Capture>)` por diálogo paginado com `PAGE_SIZE = 100`, `SelectionState` e descoberta em segundo plano.
3. Depois de Confirmar e da descoberta terminar, resolver os IDs selecionados para `Capture` e executar a verificação. A verificação de conteúdo/hash nunca ocorre no produtor da lista nem na thread Swing.
4. Continuar mostrando progresso de verificação separado do contador de descoberta.
5. Não alterar a fórmula, a ordenação nem a codificação dos hashes existentes.

## 7. Fluxo do plugin Re-Extrair

Ponto atual de integração: `src/iped/viewers/HtmlLinkViewer.java`, métodos `showRecaptureDialog(...)` e `loadRecaptureJobs(...)`.

1. Adaptar `loadRecaptureJobs` para produzir descritores em lotes em executor de fundo, preservando filtros, elegibilidade, ordem e validação atuais.
2. Substituir a lista completa de checkboxes por uma página de até 100 capturas e os controles `Anterior`, `Próximo`, `Marcar todos` e Confirmar.
3. Guardar a seleção por `captureId`, não pelos checkboxes instanciados na página atual.
4. Após o fim da descoberta e confirmação, resolver a seleção para os `RecaptureJob` necessários e chamar o pipeline atual sem alterar a lógica de integridade, auditoria, rollback ou progresso por job.
5. A descoberta não deve abrir `whatsapp.html`, carregar coordenadas, preparar conversa WebView nem calcular hashes. Essas ações pertencem ao pipeline de reextração após a seleção.

## 8. Memória e limites

- A UI instancia no máximo 100 linhas/checkboxes por página, além dos controles fixos.
- Durante descoberta, manter descritores leves, não objetos `Capture` com resultados nem conteúdo de arquivo.
- Não criar a lista final de `Capture`/`RecaptureJob` até a confirmação.
- Ao verificar/reextrair, processar um item por vez como padrão; não iniciar trabalho concorrente por página.
- A seleção explícita usa somente IDs. `ALL_FUTURE` não cria uma cópia de todos os IDs marcados.
- Manter os limites existentes por captura; tratar limite excedido como erro claro, não como captura íntegra.
- Relatórios do verificador devem continuar com conteúdo idêntico. A paginação é apenas de interface e descoberta; para evitar pico no relatório, substituir builders integrais por escrita incremental sem mudar bytes/campos/ordenação.

## 9. Consistência e concorrência

- Todas as mudanças visuais e de estado Swing ocorrem na Event Dispatch Thread.
- Eventos do produtor podem chegar enquanto o usuário navega ou altera seleção; atualizar o contador e o catálogo sem sobrescrever `SelectionState`.
- Deduplicar pelo `captureId` antes de incrementar contagem.
- Antes de verificar/reextrair, validar que cada ID ainda resolve sob a raiz escolhida, que continua elegível e que não escapou da raiz.
- Captura removida ou invalidada após descoberta: ignorar apenas essa captura e informar seu estado; continuar as demais.
- Fechar diálogo ou cancelar invalida publicações atrasadas do produtor por token/generation ID.

## 10. Critérios de aceite

1. Pastas com 0, 1, 100, 101 e mais de 1.000 capturas exibem contagem e páginas corretas.
2. Durante a varredura, a contagem cresce e a janela continua interativa.
3. `Anterior`/`Próximo` respeitam primeira/última página e o estado de busca ativa.
4. `Marcar todos` seleciona também itens descobertos depois, até a busca terminar.
5. Marcar ou desmarcar manualmente desativa `ALL_FUTURE`, mantém os IDs marcados e deixa novos itens desmarcados.
6. Seleções sobrevivem a navegação ida/volta entre páginas.
7. Verificador e Re-Extrair usam a mesma regra de seleção e tamanho de página.
8. Nenhum conteúdo de captura ou hash é lido durante a descoberta/listagem.
9. Confirmar inicia o pipeline atual somente para as capturas selecionadas e mantém a semântica atual de integridade.
10. Cancelar ou fechar para a descoberta e não deixa thread ou janela de progresso órfã.
11. Hashes e bytes canônicos das capturas existentes permanecem idênticos.

## 11. Fora do escopo

- Alterar formato de `manifest.json`, `integridade.json` ou histórico público.
- Alterar algoritmo ou conteúdo canônico de hashes.
- Alterar critérios de elegibilidade do Re-Extrair.
- Executar hashes ou ler evidência enquanto a interface apenas pagina resultados.
