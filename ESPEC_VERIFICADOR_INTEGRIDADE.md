# Especificação: verificador independente de integridade

## Objetivo

Disponibilizar um verificador Java independente do IPED que possa conferir uma captura ou um conjunto de capturas, comparar os hashes recalculados com inventários locais e com códigos públicos informados, e gerar `integridade.html` para consulta no navegador.

## Componentes

- O plugin gera `integridade.json` dentro de cada captura. O documento lista os artefatos por caminho relativo, tamanho e SHA-256. O próprio inventário, `manifest.json` e logs mutáveis de auditoria ficam fora do inventário da captura. O formato declara algoritmo e versão.
- `hashes.txt` permanece como lista dos itens exportados. O verificador o compara com o inventário quando aplicável; ele não substitui o inventário completo.
- O projeto independente do verificador fica em `verificador-integridade/`, compila para Java 11 e não depende das classes internas do IPED.
- Sem argumento de linha de comando, o programa usa automaticamente a pasta onde está o JAR; não abre seletor do Explorer. Identifica as capturas ali, lista-as e permite marcar uma, várias ou todas. Argumento opcional de diretório fica disponível para automação.
- Ao terminar, grava `integridade.html` na raiz escolhida e `verificacao-integridade.json`, `verificacao-integridade.txt` e `hashes-capturas.txt` em `integridade/`. O HTML é substituído atomicamente; arquivos auxiliares ficam organizados e não são confundidos com capturas.
- A janela oferece **Abrir no navegador**, que usa o navegador padrão do sistema para abrir o HTML, e mostra os hashes calculados.

## Hashes e resultados

Para cada artefato, SHA-256 é calculado sobre os bytes do arquivo. Compatibilidade: para índices legados, o verificador reproduz o `folderHash` atual incluindo todos os arquivos regulares. No novo formato `integridade.json` v2, o hash do conteúdo da captura é calculado sobre arquivos regulares excluindo o próprio `integridade.json` e arquivos explicitamente mutáveis/listados como excluídos; a lista e a regra recebem versão. A mudança exige atualizar o índice e o verificador juntos. Os caminhos são ordenados em minúsculas; cada linha contém `caminho relativo|tamanho|sha256` seguido de LF; o SHA-256 do texto UTF-8 é o hash do conteúdo. Nunca misturar o hash legado com o hash v2 sem identificar o algoritmo. O hash geral da coleção reproduz `index.html`: capturas ordenadas pelo nome em minúsculas, com linhas `nome da pasta|folderHash|tamanho total em bytes|quantidade de arquivos\n`. Se o usuário selecionar somente algumas capturas, o relatório chama o valor de hash geral do conjunto selecionado.

O programa compara os arquivos existentes com `integridade.json` e classifica correspondentes, divergentes, ausentes, extras e não verificáveis. O programa Java não solicita códigos públicos: o HTML gerado oferece campos para comparar o hash do conjunto e hashes individuais com os valores calculados. A comparação pública é separada da consistência com o inventário local.

## Relatório HTML

O relatório registra data e hora ISO-8601 com fuso da verificação e da geração. Inclui hashes por captura e do conjunto, resultado das comparações públicas, resumo e lista filtrável por estado. O relatório é uma fotografia daquela execução; executar novamente substitui o relatório anterior somente após geração completa. O HTML não executa o programa e não é a autoridade de cálculo. A pasta `integridade/` fica na raiz e não é reconhecida como uma captura pelo índice ou pelo verificador; os arquivos ali guardados não alteram os hashes das pastas de captura.

## Compatibilidade e segurança

- Java 11 como alvo mínimo do código e do JAR executável.
- Nenhum arquivo da captura é alterado durante a verificação.
- Caminhos absolutos, `..` e links simbólicos que escapem da captura são rejeitados.
- Inventários antigos/ausentes são classificados como legado/não verificável contra inventário; ainda se pode calcular o hash atual e compará-lo com código público.
- Hashes locais não provam autoria. A conclusão pública significa apenas que o resultado recalculado coincide com o valor informado.
- O relatório informa claramente que arquivos modificados junto com seus inventários podem continuar consistentes localmente.

## Testes de aceitação

1. Captura intacta: todos os arquivos correspondem e o hash público correto confere.
2. Arquivo alterado, apagado e extra: cada caso é identificado individualmente.
3. Hash público incorreto: divergência indicada sem alterar arquivos.
4. Inventário ausente, inválido e legado: resultado não verificável, sem falso estado íntegro.
5. Várias capturas: seleção individual e total, hash de cada captura e hash geral determinístico.
6. Reexecução: timestamp atualizado e substituição íntegra do relatório; falha de gravação preserva o anterior.
7. Compatibilidade: compilação com `--release 11` e execução com Java 11.
8. Distribuição: cópia de ambos os arquivos para a raiz do conjunto em captura nova e reextração; confirmar os hashes oficiais da versão e a resposta a arquivo ausente ou divergente.
9. Concorrência e limpeza: duas execuções do EXE usam diretórios temporários exclusivos; resíduos reconhecidos só são removidos quando não houver outro verificador em execução.

## Fases

1. Especificar e testar a regra canônica.
2. Gerar o inventário do plugin sem autorreferência.
3. Implementar o verificador independente e os relatórios.
4. Integrar o atalho no índice, se suportado pelo fluxo do IPED.
5. Validar os testes de aceitação e documentar o pacote distribuído.
6. Integrar ao plugin a instalação e validação dos hashes dos dois verificadores em capturas novas e reextrações (pendente).

## Executáveis distribuídos

- `integridade-verifier.jar` é a versão Java 11; requer Java 11 ou superior instalado.
- `integridade-verifier.exe` é um lançador autoextraível autocontido para Windows x64. Ele contém o app-image e runtime Java 11, extrai uma cópia para uma pasta exclusiva `instance-<pid>-<id>` em `%TEMP%` em cada execução, valida os hashes dos arquivos extraídos e inicia o verificador passando a pasta do próprio EXE como raiz das capturas. Execuções simultâneas não compartilham `app` ou `runtime`. Ao terminar, cada execução remove sua própria pasta. Se não houver outro verificador aberto, uma execução nova também remove resíduos reconhecidos de execuções anteriores; diretórios ativos, bloqueados ou inacessíveis são preservados.
- `build-single-exe.ps1` recompila o JAR, o app-image Java 11 e o EXE autocontido. O empacotador .NET é publicado como single-file self-contained; nenhum Java nem runtime .NET externo é requerido no computador que executa o EXE.
## Versão publicada e hashes oficiais dos verificadores

Versão dos dois artefatos: **1.2.1**. Os valores abaixo foram calculados sobre os arquivos compilados finais com SHA-256 e conferidos novamente nas cópias instaladas ao lado do `index.html` de teste. O JAR e o EXE de teste tiveram os mesmos hashes dos pacotes de distribuição.

| Arquivo | Tamanho | SHA-256 |
|---|---:|---|
| `integridade-verifier.jar` | 96.466 bytes | `A5415A32F1F9423AB0A187675A38BF71DB640FD346F6846F55510F4856CE3345` |
| `integridade-verifier.exe` | 78.345.769 bytes | `FA04E2DB2A9EC35A6A53DC2713FF4DAC55B8253ECAAC32104F9612612196665F` |

Os arquivos de distribuição estão em `verificador-integridade/integridade-verifier.jar` e `verificador-integridade/dist/integridade-verifier.exe`. A cópia de teste está na raiz `IPED_testes_chat_real/teste_chat_extraido`, ao lado de `index.html`.

Os hashes identificam estes bytes exatos da versão 1.2.1, compilada com o histórico público incluindo cada linha do inventário de arquivos (tamanho e SHA-256 esperado e encontrado) e o resumo final compacto. Ao recompilar ou alterar qualquer dos artefatos, calcular novamente os SHA-256 e atualizar esta tabela, o arquivo de checksums da distribuição e os valores de referência incluídos no plugin. Não reutilizar esses valores para uma compilação diferente.

## Instalação dos verificadores na raiz da captura pelo plugin

Requisito para a integração: em toda captura nova e reextração, o plugin deve deixar na raiz do conjunto de capturas, ao lado de `index.html` e `relatorio.htm`:

- `integridade-verifier.jar` — permite a verificação com Java 11 ou superior;
- `integridade-verifier.exe` — permite a verificação no Windows x64 sem Java instalado.

O plugin deve guardar os SHA-256 de referência da versão incluída no pacote de instalação, copiar os dois artefatos e calcular novamente o hash dos arquivos copiados. Arquivo ausente deve ser instalado a partir do pacote do plugin. Se um arquivo existente divergir do hash oficial, informar qual arquivo divergiu e oferecer substituir pela cópia oficial, continuar ciente da divergência ou cancelar a operação. Nunca declarar o verificador modificado como oficial nem substituí-lo silenciosamente. Depois da cópia, confirmar que cada hash no disco corresponde ao valor esperado.

Os verificadores ficam na raiz do conjunto, fora das pastas individuais de captura. Portanto, eles não entram no inventário `integridade.json` de uma captura e não alteram o hash individual da captura. O índice deve ignorar esses arquivos como itens de captura.

A comparação local confirma igualdade com os hashes de referência embutidos naquela versão do plugin. Como os arquivos e seus valores de referência podem ser alterados juntos por quem modificar o próprio plugin, isso não equivale a uma assinatura criptográfica nem comprova autoria do pacote. Para uma conferência pública independente, comparar os hashes acima com esta especificação ou com uma lista de checksums publicada por um canal confiável.

## Estado da integração automática

Os artefatos 1.2.1 foram compilados e colocados manualmente na pasta de teste. Na revisão do código-fonte atual do plugin, não foram encontradas referências aos nomes `integridade-verifier.jar` ou `integridade-verifier.exe` nem rotina que copie e valide esses arquivos durante captura ou reextração. Assim, a instalação automática descrita acima permanece requisito pendente; esta especificação não afirma que ela já está implementada.
## Modos de verificação: registro local e referência pública

Ao iniciar, o verificador oferece os botões **Integridade Local** e **Integridade Público**. Ambos permitem escolher uma ou várias capturas. O histórico público em integridade/historico-capturas.txt usa o formato determinístico HISTORICO_PUBLICO_V1, ordena capturas pelo nome, inclui os hashes dos integridade.json e o inventário completo dos arquivos (caminho escapado, tamanho/hash esperado e encontrado, estado), e não inclui o horário da execução. Seu SHA-256 permanece igual enquanto o conteúdo e os registros selecionados forem os mesmos.

O verificador oferece dois modos distintos e identificados no resultado:

1. **Verificar contra o registro local**: ler o `integridade.json` existente, validar seu formato e a cadeia de eventos e, em memória, recalcular o inventário do conteúdo atual sem incluir o próprio `integridade.json`. Comparar cada arquivo com o inventário mais recente e o hash de conteúdo calculado com a captura registrada. Reportar arquivos íntegros, alterados, ausentes e extras, além de lacunas ou quebras no histórico. Este modo responde se o estado atual confere com o registro presente na pasta; não prova que ambos não foram substituídos juntos.
2. **Integridade Público**: permitir colar o SHA-256 do histórico completo ou colar o arquivo historico-capturas.txt obtido por canal confiável. O verificador recalcula o histórico público determinístico a partir dos registros e arquivos locais e compara com a referência colada; o estado local dos arquivos é exibido separadamente. O relatório público contém SHA-256 do arquivo integridade.json por captura, vinculando inventário e cadeia de eventos completos. Pode aceitar ainda um código combinado que vincule o hash atual do conteúdo e o hash final do histórico. O resultado deve dizer claramente qual referência foi usada e se veio de fora da pasta verificada.

O `integridade.json` v2 é um histórico estruturado. Ele contém `schemaVersion`, `captureId`, eventos em ordem, `previousRecordHash`, `contentRootHash`, inventário de caminhos/tamanhos/SHA-256 e `recordHash`. Cada evento registra timestamp declarado, ação (captura inicial, reextração ou migração), arquivos adicionados/alterados/removidos e hashes anterior/novo quando conhecidos. Os eventos são encadeados por SHA-256 sobre uma serialização canônica UTF-8 com campos e ordenação definidos. Um campo não pode ser ambiguamente concatenado; usar JSON canônico versionado ou comprimento explícito.

Não existe hash de si mesmo: `contentRootHash` exclui `integridade.json`; `recordHash` é calculado sobre o evento sem o próprio campo `recordHash`, incluindo o `previousRecordHash`; o hash público do registro é SHA-256 dos bytes canônicos do registro completo (ou seu último `recordHash`, se a estrutura e a serialização estiverem validadas). Código combinado recomendado: SHA-256 de uma codificação canônica versionada de `captureId`, algoritmo, `contentRootHash` atual e hash final do histórico.

Ao comparar um registro externo, a versão recebida não deve substituir nem ser gravada sobre a cópia local. O verificador mantém tudo somente em memória durante a conferência. Se o registro externo for mais antigo, a interface pode verificar que aquele ponto histórico está contido na cadeia atual, desde que os hashes encadeados e a identidade da captura coincidam; isso não prova conteúdo histórico que não foi preservado. Hashes de versões antigas permitem apontar que o conteúdo divergiu, mas não reconstruir arquivos apagados. Para restauração histórica, seria necessário arquivar os bytes dos arquivos, decisão fora do escopo do simples registro.

Estados de resultado devem distinguir: `Consistente com registro local`, `Consistente com referência pública`, `Diverge da referência`, `Registro inválido/quebrado`, `Registro legado/não verificável` e `Sem referência externa`. Não usar apenas um ícone verde sem indicar a referência da validação.

## Histórico no índice

Cada card pode oferecer **Histórico de registro**, abrindo uma página de consulta que lista eventos, timestamp, hash do conteúdo, hash do evento e arquivos adicionados, alterados ou removidos. O índice não deve recalcular nem declarar válido o histórico apenas ao renderizar o card; a validação pertence ao verificador. O hash atual da captura e o hash final do registro devem ser mostrados separadamente, com opção de copiar código público combinado.
