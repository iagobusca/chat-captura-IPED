# Especificação técnica — correções de segurança, memória e estabilidade

**Estado:** correções aplicadas; builds e testes automatizados concluídos; validação manual integrada ao IPED pendente  
**Escopo:** plugin Chat Captura IPED, Verificador Java JAR e lançador Windows EXE  
**Objetivo:** impedir leitura fora da raiz selecionada, limitar o consumo acumulado de recursos e evitar congelamento/encerramento abrupto em pastas e capturas grandes, mantendo os hashes canônicos existentes.

## 1. Contexto e evidências

Esta especificação deriva da revisão estática dos seguintes pontos:

- `src/iped/viewers/HtmlLinkViewer.java`: leitura de `capturas-index.json` em `readCaptureFolders`, carregamento de jobs por página em `loadRecaptureJobs` e resolução de `folder` sem confinamento explícito à raiz selecionada.
- `src/iped/viewers/CaptureIntegrity.java`: inventário materializado em listas/JSON, limite de quantidade sem limite de comprimento de caminhos e histórico cumulativo.
- `verificador-integridade/src/IntegrityVerifier.java`: descoberta integral de capturas, resultados mantidos na memória e geração de HTML/TXT/JSON com builders completos.
- `verificador-integridade/launcher/Launcher.csproj` e `launcher/Program.cs`: EXE autocontido; o runtime .NET é incorporado no binário e precisa ser atualizado durante o publish.

As situações de consumo de heap são classificadas principalmente como **CWE-400 (Uncontrolled Resource Consumption)** e **CWE-770 (Allocation of Resources Without Limits or Throttling)**. O caminho vindo do índice é tratado como risco **CWE-22 (Path Traversal)**. Um teste posterior também reproduziu `StackOverflowError` no parser regex do manifesto com um caminho de 1.536 caracteres; esse caso é compatível com **CWE-674 (Uncontrolled Recursion)** e causa encerramento abrupto do processo antes da checagem declarada de 4.096 caracteres. Não foi identificado uso explícito de memória nativa `unsafe` no código revisto; os riscos observados são crescimento do heap, recursão do parser e pressão de recursos gerenciados.

O plugin usa `org.json` 20260719, cujo histórico upstream declara correção para **CVE-2026-59171**. A atualização do EXE deve considerar o runtime autocontido: a Microsoft publicou .NET 10.0.14 como atualização de segurança; publicar novamente com runtime mais novo no computador não atualiza um EXE autocontido já compilado. Confirmar versão e CVEs aplicáveis no momento do build.

## 2. Metas e invariantes

1. Nenhum caminho obtido de manifesto, índice ou metadado pode escapar da raiz de captura escolhida.
2. A UI mantém no máximo 100 linhas visíveis; a validação de metadados ocorre em segundo plano, em sublotes de até 25.
3. A busca atualiza a contagem sem bloquear a Event Dispatch Thread (EDT), pode ser cancelada e fecha seus recursos ao fechar a janela.
4. O consumo total de memória por página, captura e relatório tem limites explícitos; excedê-los produz erro controlado, sem OOM como mecanismo normal.
5. Relatórios são gravados incrementalmente em temporários e promovidos atomicamente. A ordem, codificação, campos e bytes canônicos usados nos hashes não mudam.
6. Capturas válidas dentro dos limites continuam produzindo os mesmos hashes e resultados de integridade.
7. `ALL_FUTURE` representa seleção global compacta. Alteração manual converte somente os IDs já descobertos para seleção explícita; novas descobertas ficam desmarcadas.
8. O EXE publicado inclui um runtime .NET atualizado e verificável; o JAR do verificador mantém a compatibilidade Java 8 declarada.

## 3. Correção de confinamento de caminhos — plugin (CWE-22)

### 3.1 Validação de `capturas-index.json`

Criar uma única função de resolução segura, usada por `readCaptureFolders` e `loadRecaptureJobs`:

1. Interpretar `folder` apenas como caminho relativo. Rejeitar caminho absoluto, drive/UNC, segmento `..`, caminho vazio e caracteres/separadores que tornem a identidade ambígua.
2. Normalizar `root.resolve(folder)` e exigir `resolved.startsWith(root.toAbsolutePath().normalize())` e `resolved != root`.
3. Exigir diretório e manifesto regular sem seguir links simbólicos/reparse points conforme as APIs suportadas pelo Java/IPED.
4. Ao abrir `manifest.json`, `whatsapp-coordinates.json` e `texto.txt`, repetir a validação imediatamente antes da abertura. Não confiar apenas na validação feita durante a descoberta.
5. Preservar o identificador relativo normalizado, com `/`, como `captureId`; comparar sem diferenciar caixa em Windows.
6. Índice inválido: ignorar apenas a entrada inválida, contar o erro e continuar; não fazer fallback silencioso para um caminho externo.

Adicionar testes de traversal com `..`, caminho absoluto, UNC, drive diferente, separadores mistos, link simbólico e troca do caminho entre descoberta e leitura. Nenhum arquivo fora da raiz pode ser aberto.

### 3.2 Deduplicação e custo de descoberta

Substituir `folders.contains(folder)` por `Set<String>` com comparação apropriada para Windows. Não executar regex/deduplicação quadrática sobre o índice. Ler e publicar candidatos em lotes; fechar o stream em todos os caminhos e continuar após erro isolado.

## 4. Descoberta e seleção paginadas — plugin e verificador

Implementar catálogo em duas fases:

### Fase A — descobrir descritores leves

- Enumerar caminhos/nomes e marcadores; não abrir inventários, coordenadas ou anexos.
- Executar em executor de fundo dedicado; publicar lotes de até 100 descritores na EDT.
- Exibir `Procurando… N encontrados`, estado final, erros ignorados e controles anterior/próximo coerentes com o estado de busca.
- Cancelar varredura quando o diálogo for cancelado/fechado; impedir callbacks antigos depois do fechamento.
- Deduplicar por `captureId`; manter descritores compactos e aplicar limite global configurável de candidatos. Ao atingir o limite, interromper com mensagem de busca incompleta, sem travar nem omitir itens silenciosamente.

### Fase B — validar metadados sob demanda

- Validar candidatos da página em lotes de até 25 fora da EDT.
- Manter no cache somente a página atual e uma quantidade pequena/configurável de páginas recentes; descartar objetos JSON/job ao sair do cache.
- Exibir por linha `Validando`, `Válida`, `Não elegível` ou `Erro de leitura`, com contagem de candidatos e elegíveis separadas.
- Em Confirmar, validar apenas os IDs selecionados, em lotes de até 25, mostrar progresso e revalidar confinamento/estado antes do pipeline.
- Não criar `List<Capture>`/`List<RecaptureJob>` integral em `ALL_FUTURE`; consumir selecionados em fluxo ou em lote limitado.

Tamanho de página: **100**. Tamanho máximo do sublote de metadados: **25**. Todas as operações Swing, inclusive atualização de contadores e caixas, ocorrem na EDT.

## 5. Limites acumulados e tratamento de metadados

Manter o limite individual de JSON atual (16 MiB), mas adicionar limites cumulativos:

- Tamanho máximo de metadados retidos em memória por página, considerando bytes de entrada e estimativa de expansão do parser; se o orçamento da página for atingido, pausar/continuar em lote menor ou rejeitar a página com mensagem controlada.
- Comprimento máximo de caminho relativo aplicado na criação e leitura do inventário; o plugin não deve gerar um inventário que o próprio verificador rejeite.
- Limite de tamanho total do `integridade.json`/`manifest.json` e do histórico cumulativo. Verificar antes de substituir arquivo: se exceder, preservar o arquivo anterior e explicar que a operação foi cancelada por limite.
- Limite de profundidade/complexidade JSON ou parser tolerante a estruturas profundas, onde a biblioteca permitir; entrada malformada deve falhar em uma captura, não derrubar o processo inteiro.
- Rejeitar tamanhos negativos, hash em formato inválido, nomes duplicados e contagens acima do limite antes de alocar coleções.

Os limites precisam estar centralizados, documentados e alinhados entre plugin e verificador. Não elevar os limites implicitamente para “resolver” incompatibilidade.

## 6. Inventário, histórico e relatórios em fluxo

### Plugin

- Evitar manter simultaneamente `List<Path>`, `JSONArray`, mapas duplicados e serialização integral do mesmo inventário.
- Processar artefatos ordenados por caminho de forma determinística; usar armazenamento temporário ordenável ou estruturas limitadas quando a ordenação for necessária.
- Construir e validar o registro em arquivo temporário; só substituir o registro anterior depois que inventário, tamanho e hashes forem concluídos.
- Aplicar orçamento ao histórico: não copiar indefinidamente eventos anteriores em memória. Definir política compatível de compactação/arquivo ou interromper operação com erro preservando histórico íntegro. Não descartar eventos silenciosamente.

### Verificador

- Não reter simultaneamente todos os objetos `Item`, capturas completas e múltiplas cópias de relatório.
- Escrever HTML, TXT e JSON para arquivos temporários com escaping próprio de cada formato (HTML, JSON e texto), em UTF-8.
- Manter a ordenação canônica e o conteúdo atualmente assinado/hashado. Validar equivalência byte a byte dos registros canônicos em capturas de referência antes de substituir a implementação.
- Promover cada relatório atomicamente após conclusão; em falha, limpar temporários e manter os relatórios anteriores.
- Limitar tamanho final/estimado dos relatórios e quantidade global de capturas/itens. Ao exceder limite, emitir estado claro e não marcar verificação parcial como completa.

## 7. Threads, cancelamento e estabilidade da interface

- Remover trabalho de disco, parsing JSON, hash e pesquisa de captura da EDT.
- Usar executor limitado (não criar thread ilimitada por clique/pesquisa), nomear threads e definir política de cancelamento/encerramento.
- Coalescer pesquisas repetidas e descartar resultados obsoletos usando identificador de geração.
- Tratar exceção por captura/job; apresentar resumo de falhas e continuar os itens independentes.
- Fechar `DirectoryStream`, streams de arquivo, `Process`, bloqueios e executores em todos os fluxos, inclusive cancelamento e erro.
- Em `OutOfMemoryError`, não tentar continuar como se a captura estivesse íntegra; reduzir caches, mostrar erro quando possível e encerrar a operação sem corromper os arquivos de origem.

## 8. Runtime e cadeia de build do EXE

- Manter `TargetFramework` em uma versão suportada LTS; atualizar o SDK/runtime do build para o patch de segurança mais recente disponível no dia da publicação (na revisão, .NET 10.0.14 era o patch de segurança publicado em 25/10/2026).
- Republicar com `SelfContained=true`; atualizar o pacote de publish, não apenas o runtime global instalado.
- Registrar versões exatas de SDK e runtime usados. Extrair/inspecionar o EXE publicado para verificar versão do runtime contido e executar o self-test do lançador.
- Confirmar por revisão de fluxo se o EXE chama APIs vulneráveis de Brotli. Mesmo que não chame, não distribuir runtime com CVEs corrigidos em patch posterior.
- Verificar hashes do payload extraído e do EXE final; não tratar o manifesto interno de hash como assinatura de autenticidade. Publicar SHA-256 do EXE por canal confiável.
- Não migrar o target do IPED ou o plugin: o requisito Java 11 do plugin/IPED permanece separado da compatibilidade Java 8 do JAR do verificador.

## 9. Versões e sequência de release local

Para a base 2.3.4-beta / 1.2.7, usar, após as correções e build aprovados, o próximo patch beta: plugin **2.3.5-beta** e verificador **1.2.8**.

Ordem obrigatória:

1. Implementar e revisar as correções do verificador e launcher.
2. Atualizar versão do verificador e compilar JAR Java 8 e EXE autocontido com runtime .NET atualizado.
3. Executar testes Java 8 e de self-test do EXE; verificar relatórios e hashes de referência.
4. Calcular SHA-256 dos novos binários.
5. Atualizar hashes permitidos no plugin e a versão do plugin.
6. Compilar plugin para o Java/IPED suportado e validar as entradas do JAR.
7. Atualizar README, documentação, changelog, CHECKSUMS, `versao_hash.md`, pacote instalador e arquivos versionados.
8. Instalar cópias na árvore de teste somente depois de criar backups; confirmar hash dos destinos.
9. Não publicar release antes de validar manualmente no IPED.

## 10. Plano de testes e critérios de aceite

### Casos obrigatórios

1. 0, 1, 100, 101, 1.000 e limite máximo de capturas: contagem/páginas corretas e UI continua respondendo.
2. Descoberta longa: contador atualiza em tempo real, cancelar interrompe rapidamente e não deixa thread/janela órfã.
3. Seleção: navegar páginas, marcar todos, desmarcar todos e alterar manualmente; IDs preservados e novos candidatos desmarcados após alteração manual.
4. Manifestos com 0, limite, limite + 1 byte; JSON profundamente aninhado; caminhos máximos e caminhos acima do máximo; tratamento controlado e sem OOM.
5. Índice adulterado com `..`, absoluto, UNC, drive diferente, barras mistas, link/reparse point e corrida de troca de diretório: nenhuma leitura fora da raiz.
6. 25.000 arquivos por captura e múltiplas capturas até o limite global: heap de teste limitado, conclusão ou erro controlado, sem crash.
7. Histórico cumulativo no limite e acima dele: preservação do histórico anterior e mensagem clara.
8. Gerar relatórios grandes: memória limitada, temporários limpos em falha, relatórios antigos preservados e saída final válida.
9. Verificador JAR executado com Java 8 e em runtime Java 11 do IPED; EXE executado sem Java instalado.
10. EXE: conferir versão runtime incorporada, payload SHA-256, execução paralela, cleanup e self-test.
11. Capturas de regressão: hashes de conteúdo, inventário e históricos canônicos idênticos antes/depois para entradas válidas dentro dos limites.
12. CVE de dependências: SBOM/inventário de dependências Java e runtime .NET, consultado contra advisories no dia do build; registrar resultado e versão corrigida.

### Critérios de aprovação

- Nenhum caso de traversal lê arquivo fora da raiz.
- Nenhum caso de carga dentro dos limites causa `OutOfMemoryError`, freeze persistente ou crash; exceder limites produz falha controlada.
- A EDT não realiza I/O ou parsing pesado.
- Relatórios temporários e backups permanecem consistentes em cancelamento/falha.
- Resultados canônicos e SHA-256 de referência não mudam para capturas válidas.
- JAR, EXE, plugin, documentação e hashes publicados correspondem aos mesmos artefatos compilados.

## 11. Fora de escopo

- Alterar algoritmos, formatos ou semântica dos hashes canônicos.
- Alterar critérios funcionais de elegibilidade do Re-Extrair ou a estratégia de recuperação de evidências.
- Declarar que o hash público é assinatura digital ou prova criptográfica de autoria.
- Atualizar todas as dependências do IPED host: a auditoria de dependências transientes do IPED é projeto separado.

## 12. Validação pré-implementação (2026-11-06)

Esta seção é o registro histórico dos testes feitos antes das correções. Os resultados não descrevem o estado atual do código; consulte a seção 13 para a validação pós-implementação.

Foi executado `verificador-integridade/prototype/test-security-spec.ps1`, que testa os algoritmos de referência sem modificar os fontes do plugin ou do verificador:

| Verificação de protótipo | Resultado |
|---|---|
| Confinamento de caminho para casos comuns (`..`, absoluto, UNC) | PASS |
| Deduplicação com conjunto e caixa indiferente | PASS |
| Tamanhos de página e mudança de seleção global para IDs | PASS |
| Lotes de 25 para 101 itens | PASS |
| Limite de 16 MiB e recusa acima do limite | PASS |
| Preservação do destino diante de falha antes da promoção | PASS |
| Bytes da serialização incremental versus saída canônica sintética | PASS |
| SDK/runtime atualizado | PASS — SDK 10.0.403 e runtime 10.0.14 aparecem em `dotnet --list-sdks`/`--list-runtimes` |

**Resultado do harness de protótipo: 8 PASS, 0 FAIL.** Depois, em uma cópia isolada, configurei NuGet.org e um cache temporário, restaurei os pacotes ausentes e concluí o publish autocontido com SDK 10.0.403/runtime 10.0.14. O `--self-test` do EXE isolado terminou com código 0. Artefato de teste: 78.362.180 bytes, SHA-256 `A82EBA261F624327893E8CD2DE3CA2A205AEA071FDF654DA7E29867BC66F529C`. Esse EXE foi gerado somente em `tmp/security-spec-exe-build`; não substituí o EXE do projeto nem atualizei hashes/versões do plugin.

A tentativa de remover o SDK `10.0.100` também não foi concluída: o sistema negou acesso a arquivos dentro de `C:\Program Files\dotnet\sdk\10.0.100`. A pasta permanece instalada; `10.0.403` segue sendo o SDK selecionado por `dotnet --version`.

Também passou `verificador-integridade/prototype/test-two-phase-discovery.ps1`: paginação 0/1/100/101/1.001, seleção global/manual, validação em lotes de 25 e duas capturas da amostra real.

Testes adicionais contra o **estado atual, antes da correção**:

- Prova de traversal com diretório temporário: `root.resolve("..\\outside")` resolveu para um diretório irmão contendo `manifest.json`, e a checagem atual de diretório/manifesto o aceitaria (`CURRENT_RESOLVE_ACCEPTS_ESCAPE=True`). Isso confirma o risco CWE-22 descrito na seção 3; nenhum arquivo do usuário foi acessado e o diretório temporário foi removido.
- Inspeção automatizada dos fontes: a validação de contenção no índice não existe (`PLUGIN_INDEX_ROOT_CHECK=False`); a deduplicação ainda usa `folders.contains` (`PLUGIN_QUADRATIC_DEDUP=True`); o verificador ainda chama descoberta integral (`VERIFIER_DISCOVERS_FULL_LIST=True`) e cria builders completos dos relatórios (`VERIFIER_REPORT_BUILDERS_FULL_MEMORY=True`). Estes são gates que devem falhar até a implementação.
- Publish isolado/self-test do EXE: PASS após restaurar pacotes NuGet temporariamente; código de saída do self-test `0`. O EXE de teste e seu hash foram registrados acima.
- JAR 1.2.7 com `--verify-only`: PASS em Java 8 e Java 11 sobre as duas capturas reais; hashes de conteúdo/histórico foram idênticos entre os dois runtimes.
- EXE isolado: três execuções simultâneas de `--self-test --self-test-hold-ms=700` terminaram com códigos `0,0,0`; não restaram novas pastas `instance-*` no diretório temporário. Isso valida extração/limpeza concorrente no caminho de self-test, não o fluxo de GUI/verificação.
- Runtime embutido: em outra cópia temporária adicionei somente uma opção diagnóstica de teste no launcher; o EXE publicou e informou `Environment.Version = 10.0.14` (exit code 0). A opção não foi adicionada ao projeto real.
- Limite real do verificador: em pasta temporária criei uma captura com 25.010 arquivos de evidência vazios mais `manifest.json` (25.011 regulares), rodei o JAR com Java 8 e `-Xmx256m`; encerrou com erro controlado de limite, sem OOM (`LIMIT_PLUS_ONE_CONTROLLED_PASS`). Isso valida o limite atual por captura, não o orçamento global novo nem a emissão de relatórios grandes.
- Limite real de metadados: um `manifest.json` sintético com 16 MiB + 1 byte foi rejeitado pelo leitor limitado antes do parse. A matriz de caminhos sintéticos encontrou `StackOverflowError` no Java 8 para comprimentos 1.536, 1.792, 2.048, 4.096 e 4.097; 1.280 não estourou a pilha e avançou até erro de arquivo ausente. Assim, a validação regex recursiva falha antes de aplicar o limite `MAX_RELATIVE_PATH_CHARS=4096`; o limite atual não protege contra esse encerramento. Os testes usaram `--verify-only`, `-Xmx128m` e diretórios temporários, que foram removidos.
- Geração real dos relatórios: um probe temporário chamou por reflexão o `writeReports` do JAR 1.2.7 com 25.000 itens sintéticos e `-Xmx96m`; a execução terminou com `OutOfMemoryError` (`REPORT_OOM`, exit 2), reproduzindo o risco de retenção/serialização integral. Foi executado somente em diretório temporário, sem alterar o JAR nem os fontes do produto. Esse resultado confirma a necessidade de escrita incremental e limites de relatório; não mede o limite seguro em heap de produção.


## 13. Validação pós-implementação (2026-11-06)

### Alterações aplicadas

- Plugin atualizado para **2.3.5-beta** e verificador para **1.2.8**.
- Plugin: confinamento e limites para caminhos/índices/metadados, deduplicação de candidatos, descoberta paginada em segundo plano e cancelável, seleção por identificador e validação sob demanda.
- Verificador: parser JSON limitado por bytes/profundidade, validação de caminho e symlink, descoberta limitada em segundo plano, limites agregados de itens/metadados e pré-validação do orçamento de relatórios. A implementação recusa relatórios acima do orçamento antes de construir as saídas completas; não é uma escrita streaming de relatórios.
- Promoção dos três relatórios por temporários com rollback em falha; escaping JSON de saída.
- JAR do verificador compilado para Java 8; EXE autocontido recompilado com runtime .NET 10.0.14; JAR do plugin compilado para bytecode Java 11.
- Instalador e cópias versionadas foram gerados localmente. Nenhum destino externo ao repositório foi atualizado.

### Testes pós-implementação executados

- `CaptureIntegrityTest`: PASS.
- `SecurityRegressionTest` em Java 8 com `-Xmx256m`: PASS (profundidade, caminho longo/limite, orçamento de relatório e preservação em falha).
- `IndexHashParityTest`: PASS; hashes canônicos e geração de relatório conferidos.
- `VerifierIntegrityHashTest` contra o JAR final: PASS; aceita somente os hashes 1.2.8 definidos.
- Amostra `teste_chat_extraido`: JAR `--verify-only` em Java 8 e Java 11: PASS; as duas capturas produziram hashes iguais nos runtimes.
- Captura acima do limite: 25.011 arquivos regulares com Java 8 e `-Xmx256m`: erro controlado de limite, sem OOM.
- Probe de 25.000 itens com heap de 96 MiB: orçamento recusado com exceção controlada, sem OOM.
- EXE final: `--self-test` PASS; três execuções concorrentes do modo self-test terminaram com `0,0,0`.
- Validação do JAR do plugin (`validate-plugin-jar.ps1`): PASS; bytecode major 55 (Java 11).
- `git diff --check`: executar na verificação final desta alteração.

### Limitações restantes

Não houve teste manual da interface Swing completa nem execução integrada dentro do IPED nesta sessão. O teste de symlink/reparse race em Windows, UX de cancelamento, matrizes de capturas nos limites máximos e comparação byte a byte dos três relatórios com todas as versões de referência não foram executados. O verificador ainda cria relatórios completos em memória após uma estimativa conservadora que limita a saída; a geração incremental especificada é trabalho futuro se esse orçamento não atender aos casos válidos reais. Assim, os riscos reproduzidos foram mitigados com limites e erros controlados nos cenários testados, mas a validação de release manual no IPED continua pendente.
