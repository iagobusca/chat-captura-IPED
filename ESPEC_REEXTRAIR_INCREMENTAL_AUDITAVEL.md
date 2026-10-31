# Especificação: Re-Extrair incremental e auditável

**Estado:** implementação 2.3.0-beta aplicada; validação descrita na seção 12
**Projeto-base:** Chat Captura IPED 2.2.1-beta
**Compatibilidade pretendida:** Java 11 e formato legado de captura

## 1. Objetivo

Evoluir Re-Extrair para evitar trabalho quando a captura continua equivalente, atualizar somente artefatos que uma migração suportada consegue regenerar, restaurar arquivos apagados ou corrompidos a partir da origem e registrar evidências das decisões. A reextração deve continuar conservadora: se os dados necessários não estiverem disponíveis ou não forem confiáveis, executar captura completa.

A otimização nunca deve usar semelhança visual como prova de igualdade. SHA-256 confirma igualdade de bytes em relação a um hash de referência; não autentica por si só a origem desse hash nem demonstra quem alterou um arquivo.

## 2. Evidência atual no código

- HtmlLinkViewer.runRecaptureBatch processa cada trabalho selecionado e recaptureOne cria uma pasta temporária, executa runChatCaptureInternal e troca a pasta antiga após validar a captura.
- hashFile calcula MD5 e SHA-256 dos arquivos exportados. writeManifest registra exportedItems com caminho relativo, tamanho, hashes e hash do item de origem.
- buildWhatsAppCoordinatesJson salva frames e blocos com IDs, tipo, texto, hashes de mídia e coordenadas. writeWhatsAppHtml monta o visualizador a partir dos dados JSON.
- O manifesto contém pluginVersion e versão estrutural, mas o formato atual não mantém versões independentes dos geradores de cada artefato nem uma tabela de migrações executáveis.
- A captura lê texto visível do bloco. Ainda é necessário identificar no HTML do IPED um marcador confiável que separe texto original de transcrição de IA.

## 3. Princípios

1. Manter a saída compatível com Java 11 e não adicionar dependência externa para hashes.
2. Não declarar item igual com base somente em nome, caminho, tamanho, ID isolado ou similaridade visual.
3. Falta de evidência, manifesto legado/incompleto ou divergência não compreendida deve impedir o atalho e levar à reextração completa, salvo uma migração explicitamente segura.
4. Preservar anotações, arquivos desconhecidos e conteúdo legado ao atualizar artefatos conhecidos.
5. Fazer alterações em staging e validar antes da promoção; manter rollback até confirmar a substituição.
6. Registrar resultado e incerteza por arquivo/frame: igual, restaurado_igual, alterado, ausente_na_origem, assinatura_incompleta, migrado ou reextracao_completa.

## 4. Modelo de metadados

### 4.1 Identidade e versão dos artefatos

Adicionar ao manifesto campos compatíveis com leitores antigos (campos novos opcionais):

    {
      "pluginVersion": "2.3.0",
      "manifestSchemaVersion": 3,
      "artifactGenerators": {
        "whatsapp.html": 2,
        "whatsapp-coordinates.json": 2,
        "relatorio.htm": 1,
        "texto.txt": 1
      },
      "captureFingerprint": {
        "algorithm": "SHA-256",
        "complete": true,
        "value": "..."
      },
      "artifacts": [
        {"path": "whatsapp.html", "kind": "generated", "sha256": "..."},
        {"path": "screenshots/frame_0001.png", "kind": "frame", "sha256": "..."}
      ]
    }

pluginVersion identifica o código; manifestSchemaVersion identifica a estrutura do manifesto; artifactGenerators identifica a versão do gerador por arquivo. Não usar uma única versão do plugin como sinal suficiente para invalidar ou aceitar todos os artefatos.

O inventário deve usar caminhos relativos normalizados. Registrar hashes de todos os artefatos relevantes, incluindo os derivados, sem criar referência circular. Se o próprio inventário/auditoria precisar de integridade, excluir o arquivo autorreferente do inventário e documentar essa regra.

### 4.2 Assinatura semântica por mensagem e frame

Para cada mensagem do frame, registrar em ordem:

- ID da mensagem, tipo, posição no chat, texto original e data/hora disponível;
- hashes dos anexos associados à mensagem;
- para imagem, hash da imagem e legenda/texto original, quando houver;
- para áudio/vídeo, hash SHA-256 do arquivo original, se disponível;
- estado de disponibilidade de cada campo e versão do algoritmo da assinatura.

Transcrição de IA pode ser gravada em campo auxiliar explicitamente identificado, mas não participa da assinatura de identidade. Se a interface não permitir distinguir transcrição de texto original/legenda, marcar assinatura como incompleta e não permitir atalho automático para aquele frame.

A assinatura canônica deve ter codificação determinística (UTF-8, normalização de campos documentada, ordem definida e separação sem ambiguidade) antes de aplicar SHA-256. Incluir posição/ordem para que reordenação seja detectada. Separar conteúdo semântico de geometria/layout.

Para cada PNG, registrar também framePngSha256, dimensões e sequência. A assinatura semântica detecta mudanças de mensagem/mídia; o hash do PNG detecta mudanças nos bytes da imagem. Um pode mudar sem o outro.

## 5. Plano de decisão do Re-Extrair

Para cada captura:

1. Ler o manifesto e inventário esperados; validar JSON, versões conhecidas, caminhos seguros e hashes de arquivos presentes.
2. Localizar e abrir a origem do chat. Confirmar identidade por hash da origem quando disponível, além de âncoras e intervalo. Nome/título/path são apenas critérios auxiliares.
3. Calcular fingerprint de captura incluindo origem, âncoras/intervalo, versão de assinatura, configurações que alteram saída (blur, opções de captura), anotações e versões dos geradores relevantes.
4. Se fingerprint completo coincidir, inventário esperado estiver íntegro e não houver migração pendente, marcar captura como inalterada e não gerar novamente frames/anexos.
5. Se apenas geradores de alguns artefatos mudaram, consultar registro de migrações. Regenerar somente arquivos declarados e somente quando os insumos antigos contiverem os dados exigidos.
6. Se um anexo/frame esperado estiver ausente ou corrompido, recuperá-lo da origem quando possível, calcular hash novo e comparar com o hash de referência.
7. Se o hash restaurado coincidir, registrar restaurado_igual; se divergir, registrar alterado e não descrevê-lo como cópia idêntica. Se origem/hash não existir, registrar falha/estado não verificável e seguir a política segura de reextração.
8. Se assinatura, origem, migração ou integridade não puder ser confirmada, executar captura completa pela implementação atual.
9. Recalcular inventário, hashes e índices que dependam dos arquivos modificados; validar resultado staged e então promover com rollback disponível.

## 6. Migrações por versão

Manter um registro versionado no pacote do plugin (por exemplo, reextract-migrations.json) com declaração de origem/destino, arquivo afetado, gerador, pré-requisitos e se exige reextração. A declaração seleciona migrações; código revisado implementa cada ação. Não executar comandos ou código arbitrário carregado de uma captura.

Exemplo conceitual:

    {
      "id": "2.2.1-beta-to-2.3.0-html-v2",
      "fromArtifact": {"file": "whatsapp.html", "generator": 1},
      "toArtifact": {"file": "whatsapp.html", "generator": 2},
      "requires": ["whatsapp-coordinates.json", "screenshots/*"],
      "requiresFullRecapture": false
    }

Se uma alteração exigir novos dados não salvos (por exemplo, coordenadas ou texto original inexistentes), a migração declara reextração completa. Manifestos legados sem inventário ou versões compatíveis seguem o fluxo completo.

## 7. Restauração e auditoria

Usar `integridade.json` versionado como registro durável da captura, incorporando os eventos de Re-Extrair; se for necessária compatibilidade operacional, um JSONL pode ser staging, mas o estado final deve ser consolidado de forma atômica no registro. Cada evento por arquivo/frame registra timestamp declarado, caminho relativo, ação, motivo, hash esperado, hash calculado, hash da origem quando disponível, versão do plugin, regra/migração aplicada e resultado. O inventário atual e os eventos antigos devem ser preservados conforme política explícita de retenção. Nunca incluir conteúdo sensível de mensagens no log além do que já está no manifesto.

Cada evento referencia o hash do evento anterior; o hash do evento atual exclui seu próprio campo e usa serialização canônica versionada. `contentRootHash` exclui `integridade.json` para evitar autorreferência. Essa cadeia permite detectar inconsistências locais, mas não impede que alguém reescreva toda a cadeia junto com os arquivos. O verificador deve oferecer “Verificar contra o registro local” e “Comparar com hash/registro público”. Só a referência pública obtida de fora da pasta confirma que o registro local corresponde ao valor divulgado. Um hash público autentica igualdade com aquele valor, não a identidade do autor.

Não sobrescrever silenciosamente conteúdo divergente. A política deve preservar o arquivo antigo em staging/backup até validação e registrar decisão. Arquivo ausente também pode significar remoção intencional; restauração automática deve ser identificada claramente e configurável, sem apagar outros arquivos.

## 8. Compatibilidade e segurança de caminhos

- Capturas antigas, sem campos novos: continuam abrindo e são tratadas como não elegíveis ao fast path até migração validada.
- Recusa de caminhos absolutos, .., links simbólicos inesperados e destinos fora da pasta da captura.
- SHA-256 é a comparação de conteúdo; timestamps/tamanho ajudam diagnóstico, mas não substituem hash.
- Falha ao calcular hash, restaurar arquivo ou validar staging deve preservar a captura anterior e permitir rollback.
- Atualizar índice geral depois de mudanças porque o hash da pasta depende dos arquivos existentes. Versionar o algoritmo: o formato legado pode continuar incluindo todos os arquivos; a nova raiz de conteúdo exclui `integridade.json` e deve ser identificada no índice e no manifesto para não comparar valores de algoritmos diferentes.

## 9. Verificação e critérios de aceite

1. Captura idêntica, íntegra e com fingerprint completo é pulada; nenhum PNG/anexo é regenerado.
2. Mudança de texto, data, tipo, ordem, mídia, intervalo, blur ou anotações invalida a assinatura/fingerprint correspondente.
3. Alteração somente no gerador de whatsapp.html atualiza esse artefato quando os dados existentes bastam e preserva frames, anexos e anotações.
4. Migração que precise de dado ausente aciona reextração completa.
5. Arquivo apagado é detectado contra o inventário; restauração com SHA-256 igual é registrada como restaurado_igual.
6. Arquivo restaurado com hash divergente é marcado como alterado, sem falsa confirmação de identidade.
7. Transcrição de IA não altera a identidade de áudio/vídeo: esse texto é excluído da assinatura; sem SHA-256 da mídia original, a assinatura fica incompleta e não autoriza fast path. Para mensagens de texto, o capturador usa o texto do bloco sem mídia.
8. Arquivo corrompido, manifesto inválido, interrupção ou falha de escrita não destrói a captura anterior.
9. Capturas legadas continuam podendo ser reextraídas integralmente.
10. Testes de compatibilidade confirmam compilação Java 11 e leitura dos formatos antigos e novos.
11. O registro `integridade.json` preserva eventos de reextração com cadeia válida e inventário atual que exclui o próprio registro.
12. Verificação local encontra divergência entre arquivo atual e inventário, mas rotula o resultado como consistente somente com a referência local.
13. Referência pública correta confirma o hash externo do registro/código combinado; incorreta diverge sem alterar arquivos.
14. Reescrever o registro local inteiro pode passar na checagem local, mas deve divergir de um hash público previamente compartilhado.

## 10. Sequência recomendada de implementação

1. Corrigir e centralizar versões do plugin, manifesto e geradores de artefatos.
2. Expandir manifesto com inventário completo e hash SHA-256 de cada artefato; manter leitura legada.
3. Criar assinatura semântica de mensagens/frames e hash separado do PNG; resolver identificação de transcrição no DOM antes de habilitar atalho em áudio/vídeo.
4. Implementar comparação de inventário e auditoria; regenerar seletivamente o HTML derivado. Para frames/anexos ausentes, usar reextração completa enquanto não houver exportação unitária confiável do IPED.
5. Adicionar migrações declarativas com ações permitidas implementadas no código.
6. Implementar fast path de captura inalterada depois de validar fingerprint, inventário, geradores, configurações e hash da origem; localizar/abrir o chat continua necessário para confirmar a origem atual.
7. Consolidar captura inicial, reextração, migração e restauração em eventos de `integridade.json` com cadeia verificável, sem auto-inclusão no hash do conteúdo.
8. Integrar ao índice o botão Histórico e ao verificador os dois modos (registro local e referência pública), preservando resultado legado como não verificável.
7. Executar testes de regressão, interrupção/rollback, Java 11 e capturas legadas antes de distribuir uma nova versão.

## 11. Fora do escopo desta especificação

- Similaridade visual como prova de igualdade ou critério para ignorar frame.
- Garantia de autenticidade/timestamp confiável apenas com hashes locais.
- Reescrita do conteúdo original de evidência ou descarte de arquivos desconhecidos.

## 12. Implementação e validação da versão 2.3.0-beta

Implementado no projeto local:

- Classe `CaptureIntegrity` com SHA-256 de arquivos, assinaturas canônicas por mensagem/frame, inventário seguro, comparação de arquivos, validação de caminhos e log JSONL.
- Capturas novas gravam `manifestSchemaVersion` (campo `version`), fingerprint, versões dos geradores, inventário de artefatos, assinatura semântica por frame/mensagem e hash PNG separado.
- Re-Extrair ignora captura somente após confirmar origem/hash, intervalo/configuração, versões dos geradores e integridade de todos os arquivos esperados.
- Migração allowlisted do `whatsapp.html` a partir de coordenadas válidas, com promoção staged, rollback e registro de hashes.
- Arquivo ausente/alterado fora do HTML seletivamente regenerável cai para reextração completa; após a nova captura, compara o hash anterior com o recuperado e registra o resultado no log.
- Mensagens de áudio/vídeo não incluem texto/transcrição na assinatura; o hash da mídia original é necessário para marcar a assinatura como completa.
- Capturas antigas ou sem fingerprint/inventário completo continuam no fluxo de reextração completa.

Validação executada:

- Compilação de `HtmlLinkViewer` e `CaptureIntegrity` com JDK 26, opções de fonte/alvo 11, dependências locais do IPED.
- Inspeção com `javap` confirmou major version 55 nas duas classes.
- Testes automatizados com JDK 11 passaram para assinatura determinística, exclusão da transcrição auxiliar, mudanças de posição/ordem, mídia sem hash como incompleta, inventário íntegro/alterado/ausente, rejeição de traversal e auditoria fora do inventário autorreferente. O mesmo teste passou usando as classes dentro do JAR empacotado; o validador local confirmou as classes e bundles obrigatórios.
- JAR empacotado: SHA-256 `7DB0D24973E23F77689217FA6094C6D09AAD7A7838525DD693B951CD5CDC6846`.

Limite de validação: não executei o viewer em um caso IPED real nem reproduzi uma reextração por interface gráfica. A compilação valida integração das APIs; os testes focam os utilitários puros. A ausência de SHA da mídia deixa assinatura incompleta de propósito.

## 13. Histórico cumulativo e verificação externa

`integridade.json` passa a representar o inventário atual e o histórico cumulativo de eventos da captura. Captura inicial cria o primeiro evento; Re-Extrair compara o inventário anterior ao resultado em staging e acrescenta, na mesma promoção transacional, as alterações de arquivos. Se a captura for abortada ou a promoção falhar, não publicar evento como concluído. O evento registra arquivos adicionados, atualizados, removidos ou restaurados, hashes anterior/novo e resultado. Ausência do hash antigo deve ser registrada como desconhecida, sem inventar valor.

O cálculo `contentRootHash` não inclui `integridade.json`; a identidade do registro é calculada separadamente, sem auto-referência. A regra precisa de `schemaVersion`/`algorithmVersion`. O hash legado de pasta usado no índice atual inclui todos os arquivos e deve continuar marcado como legado. Uma nova versão do índice pode mostrar o hash do conteúdo e o hash do histórico em campos separados, além do código público combinado. Não comparar hashes produzidos por regras diferentes.

O card **Histórico de registro** abre a consulta dos eventos e apresenta timestamp, hash de conteúdo, hash do evento e arquivos adicionados/alterados/removidos. A página não é autoridade de validação: o verificador independente recalcula e valida.

O verificador oferece dois modos: **Verificar contra o registro local**, que confere conteúdo atual, inventário e cadeia local; e **Comparar com hash/registro público**, que compara a cópia local com uma referência externa colada ou selecionada. O segundo modo nunca substitui o registro da captura. Um hash público previamente compartilhado detecta a reescrita conjunta do conteúdo e do histórico em relação àquele ponto; sem referência externa, o resultado é apenas consistência local. O registro guarda hashes anteriores, não os bytes anteriores; histórico não permite restaurar arquivo sem cópia/arquivo versionado adicional.
