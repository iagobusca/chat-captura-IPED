# Especificação técnica — captura HTML auditável por mensagem

**Estado:** implementação aplicada no plugin/verificador 2.4.1-beta/1.3.0; 53 testes automatizados aprovados; teste manual final no IPED 4.3.1 ainda pendente antes de distribuição pública.  
**Alvo fixo:** IPED 4.3.1 e o formato HTML WhatsApp observado nessa versão  
**Escopo:** plugin Chat Captura IPED, Re-Extrair e Verificador de Integridade  
**Objetivo:** substituir novas capturas baseadas em screenshots/coordenadas por uma cópia HTML autocontida do intervalo marcado, com inventário e auditoria de mensagens e anexos.

## 1. Premissas e observações do exemplo

- O plugin suporta especificamente IPED 4.3.1. Não precisa detectar automaticamente versões futuras. Ao migrar o IPED, o contrato HTML será reanalisado e o plugin atualizado para a versão escolhida.
- O exemplo inspecionado é `IPED_testes_chat_real/Exportados/arquivos/F/A/FAF3132301A706022E1C3EAD6AE0800B.html`, com 169.396 bytes.
- Nesse arquivo há elementos de mensagem com IDs numéricos, por exemplo `243188`, `243189` e `243190`; também aparecem elementos com classes como `linha`, `bbr`, `outgoing to` e `systemmessage`. O HTML contém imagens embutidas como `data:image/...;base64,...` e elementos de interface que não são mensagens.
- O contrato deve distinguir mensagens de elementos auxiliares (topbar, modal, campos ocultos e controles). Não tratar todo elemento com `id` como mensagem.
- Se o usuário marcar o início sobre um separador de data, o marcador temporário da interface é resolvido para a primeira mensagem numérica seguinte; essa ID resolvida vai ao manifesto como âncora persistente do Re-Extrair, e o separador continua fora da contagem e da saída.
- Antes desta implementação, o plugin selecionava início/fim por ID DOM, mas materializava `screenshots/frame_*.png` e `whatsapp-coordinates.json`. O formato novo preserva o leitor legado e muda a saída de novas capturas.

Os exemplos de IDs/classes são evidência apenas para este HTML e IPED 4.3.1; a implementação deve validá-los com outras conversas exportadas pela mesma instalação 4.3.1 antes de fixar seletores.

## 2. Decisões de formato

1. **Formato novo de captura:** `captureFormat: "html-message-v1"` no `manifest.json`. Capturas existentes sem esse campo continuam no formato de screenshots e permanecem verificáveis.
2. **Conteúdo capturado:** `whatsapp.html` independente, `messages.json` (manifesto por mensagem), `manifest.json` (metadados da captura) e diretório `attachments/` apenas quando um recurso selecionado não estiver embutido no HTML.
3. **Seleção:** intervalo inclusivo entre as mensagens marcadas. Mensagens auxiliares e elementos de interface não entram no intervalo nem no inventário.
4. **Mensagem:** a cópia preserva a estrutura visual e conteúdo necessário para consulta, mas remove handlers/scripts ativos e referências remotas. Recursos visuais permitidos são locais/embutidos e validados.
5. **Compatibilidade:** a leitura é polimórfica por `captureFormat`; não converter nem reescrever automaticamente capturas antigas.
6. **Alvo fixo:** seletores, regras de extração e testes descrevem o IPED 4.3.1. Uma atualização de IPED exige uma revisão explícita do contrato e novos testes antes de declarar suporte.

## 3. Contrato DOM do IPED 4.3.1

Criar `Iped431MessageExtractor`, testável sem abrir o caso inteiro, com estas etapas:

1. Enumerar os candidatos DOM em ordem documental.
2. Selecionar apenas nós identificados como mensagens pelo contrato validado (IDs da conversa e estrutura/classe de mensagem); excluir topbar, datas/separadores, modais, inputs e controles. Separadores de data podem ser mantidos como contexto visual, mas não contam como mensagens auditadas.
3. Extrair de cada nó: `domId`, ordem, timestamp textual e normalizado quando disponível, remetente, direção, tipo de conteúdo, texto, metadados visíveis e relações com anexos.
4. Capturar o fragmento DOM necessário e sanitizá-lo com allowlist de tags/atributos. Remover `script`, handlers `on*`, formulários, URLs `javascript:`, conteúdo remoto e IDs globais duplicáveis. Preservar apenas estilos necessários e seguros.
5. Resolver attachments pela relação efetiva do HTML (src/href ou elementos auxiliares); suportar `data:` somente após validar MIME, tamanho e decodificação. Recursos externos devem ser copiados para `attachments/` sob nome derivado do hash, nunca deixar URL absoluta da evidência no HTML final.

Se um tipo de anexo não puder ser extraído de forma inequívoca, registrar `unsupported`/`unresolved` e interromper ou marcar a captura como parcial conforme a política de integridade; nunca declarar captura completa omitindo-o silenciosamente.

## 4. Identidade, canonicalização e hashes

O `domId` do IPED é identificador local, não uma identidade criptográfica nem garantia isolada de unicidade. A identidade determinística da mensagem é uma tupla versionada:

```text
conversationKey | domId | timestamp | sender | direction | type | duplicateOrdinal
```

- `sourceFingerprint`: SHA-256 do HTML fonte exato, mais identificador/hash da origem de evidência quando fornecido pelo IPED. É proveniência da versão observada e fica no manifesto/evento de reconciliação, **fora da chave estável da mensagem**, pois o HTML inteiro muda quando mensagens novas são adicionadas. Não inserir caminho absoluto local como identidade pública.
- `conversationKey`: hash de campos estáveis de conversa/origem disponíveis no HTML ou na origem IPED; não usar apenas título exibido.
- `duplicateOrdinal`: posição relativa entre ocorrências com mesma chave natural, para não colapsar mensagens repetidas.
- `messageHash`: SHA-256 de JSON canônico UTF-8 com campos extraídos em ordem fixa, texto normalizado por regra documentada, metadados relevantes e lista ordenada de anexos (`mime`, bytes, SHA-256). Não incluir coordenadas/pixels.
- `captureRootHash`: hash determinístico do manifesto canônico ordenado por `order`, incluindo hashes das mensagens, anexos e versão do contrato.

Guardar também os bytes/hash originais de cada anexo. A canonicalização é usada para comparar conteúdo; não substitui a preservação dos bytes originais. IDs duplicados, chave natural ambígua ou campo essencial indisponível devem aparecer no relatório e podem tornar a comparação inconclusiva.

## 5. Estrutura dos arquivos

### `manifest.json`

Campos mínimos: `schemaVersion`, `captureFormat`, `pluginVersion`, `ipedVersion` (4.3.1), data UTC, identidade da origem, `sourceFingerprint`, `conversationKey`, anchors inicial/final, contagem total e auditável, estado (`complete`, `partial`, `failed`), `messagesFile`, `captureRootHash` e limites aplicados.

### `messages.json`

Registro em ordem documental por mensagem contendo: identidade, `domId`, ordem, data/hora original e normalizada, remetente, direção, tipo, texto extraído ou hash/indicação de conteúdo, `messageHash`, status de extração e lista de anexos com caminho relativo, tamanho, MIME, SHA-256 e status.

### `whatsapp.html`

HTML offline que apresenta apenas o intervalo selecionado, na ordem de origem, com estilos empacotados e links locais seguros. Cada nó renderizado recebe um `data-capture-message-key` único, não reutiliza IDs globais do documento original e pode ser relacionado a `messages.json`.

## 6. Fluxo de captura

1. Validar a origem carregada e registrar versão/identidade do caso e hash do HTML fonte.
2. Resolver IDs inicial/final no DOM e verificar ordem, unicidade e tipo elegível.
3. Extrair a lista de mensagens inclusiva, em worker fora da EDT; impor limites de quantidade, bytes, profundidade DOM, anexos, tamanho por mensagem e total.
4. Extrair e validar recursos ligados às mensagens; copiar somente os necessários ao intervalo.
5. Produzir saídas em diretório temporário, gerar hashes e validar referências locais/contagens.
6. Abrir/verificar o HTML gerado com parser e testes de conteúdo; só então promover atomicamente para a pasta final. Em erro, manter a captura anterior e limpar temporários.
7. Gravar estado completo somente se todas as mensagens/anexos obrigatórios foram processados; falha parcial deve ser explícita.

## 7. Re-Extrair e reconciliação

1. Reabrir a conversa de origem e exigir correspondência da origem/conversa conforme o manifesto. O `sourceFingerprint` atual pode diferir do fingerprint anterior quando a conversa recebeu novas mensagens; registrar ambos, sem usá-lo sozinho para rejeitar a reconciliação.
2. Buscar anchors usando, em ordem: `domId` confirmado; tupla de contexto (timestamp, remetente, direção, tipo, hash/conteúdo); posição próxima apenas como evidência auxiliar. Não usar texto sozinho como chave.
3. Exigir um único par de âncoras e ordem inicial ≤ final. Se zero ou múltiplas correspondências: parar e solicitar remarcação manual.
4. Extrair novamente o intervalo completo para comparação. Classificar registros: `unchanged`, `changed`, `missing_in_current_source`, `new_in_interval`, `duplicate_or_ambiguous`, `unsupported`.
5. Atualizar a captura preservando histórico/auditoria anterior em append-only, com hash da versão anterior e eventos de reconciliação. Não declarar que uma mensagem foi apagada por alguém; registrar apenas que não foi encontrada na fonte atual.
6. Promover os arquivos novos em transação. Em falha, manter HTML, manifesto e anexos antigos íntegros.

Mensagens recebidas depois da primeira captura, dentro dos anchors, entram como `new_in_interval`; mensagens fora do intervalo ficam fora. Mudança da própria origem ou exportação com cobertura diferente deve ser marcada e pode impedir comparação conclusiva.

## 8. Verificador de Integridade

- Detectar `captureFormat` e executar verificação específica; manter o verificador de arquivos atual para formato legado.
- Validar esquema, tamanho/profundidade JSON, limites agregados, IDs/chaves duplicadas, ordem, confinamento de caminhos, symlinks e hashes de todos os anexos.
- Recalcular `messageHash` e `captureRootHash`; apresentar resultado por mensagem e por anexo, além do resultado geral.
- Aceitar manifesto de referência pública correspondente ao mesmo formato e versão; comparar contagens e listar diferenças sem confundir “ausente nesta fonte” com autoria/causa da remoção.
- Exportar relatórios HTML/TXT/JSON com escape apropriado, limites de memória e arquivos temporários/rollback.
- Explicar que SHA-256 comprova igualdade em relação a uma referência confiável, não autoria. Para autenticação independente, definir assinatura digital do manifesto (chave do responsável) ou publicar o hash por canal autenticado.

## 9. Migração e instalação

- Manter leitor das capturas antigas, relatórios e hashes atuais.
- Não converter capturas antigas automaticamente: screenshots continuam sendo evidência visual válida e não podem ser reconstruídas como HTML original.
- Nova captura usa `html-message-v1`; o verificador identifica formato pelo campo explícito, não por tentativa heurística.
- Durante a beta, `-Diped.chat.capture.htmlMessageV1=false` desativa novas capturas HTML; capturas já identificadas como `html-message-v1` continuam usando o fluxo HTML no Re-Extrair.
- A allowlist inclui imagens, áudio, vídeo, PDF, ZIP/OOXML, Office legado, RAR, 7z, texto/CSV e arquivos desconhecidos como `.bin`. Limites do plugin: 8 MiB por anexo, 64 MiB agregados, 25.000 mensagens e 16 MiB para o HTML fonte.
- A censura por similaridade não foi portada para `html-message-v1`; se ativa, o plugin falha com aviso em vez de salvar conteúdo sem a censura. O fluxo PNG legado continua selecionável pela propriedade JVM.
- Atualizar versão beta do plugin/verificador conforme política de release quando a implementação for concluída. Atualizar README, docs, `CHECKSUMS.md`, `versao_hash.md`, instalador e hashes permitidos em ordem: compilar/verificar binários do verificador → calcular hashes → atualizar allowlist → compilar plugin → criar instalador/release.

## 10. Testes e critérios de aceite

### Contrato HTML 4.3.1

- Fixtures reais anonimizadas para texto, mensagens repetidas, datas, mensagens do sistema, direção, imagem inline/base64, áudio, vídeo, documento, arquivo e referência quebrada.
- Extrator exclui topbar, modais, inputs, controles e separadores da contagem de mensagens.
- Confirmar unicidade/duplicidade dos IDs no mesmo chat e entre chats diferentes; preservar duas mensagens idênticas distintas.
- Repetir extração do mesmo HTML e obter manifesto/hash idêntico, exceto campos explicitamente temporais.

### Captura/Re-Extrair

- Intervalos 1–1, início/fim, conversa longa e mensagens consecutivas com mesma hora/texto.
- Saída contém exatamente mensagens selecionadas e anexos alcançáveis, sem arquivos fora do intervalo.
- Alterar texto/anexo, inserir mensagem dentro/fora dos anchors, remover anchor, duplicar ID e trocar origem: classificação correta ou falha controlada.
- Inserir mensagem antes/depois do intervalo altera o fingerprint da fonte, mas não pode mudar as chaves estáveis nem os hashes de conteúdo das mensagens antigas capturadas.
- Injetar path traversal, symlink, URL remota, `javascript:`, HTML profundo, `data:` excessivo e MIME falso: nenhuma leitura fora da raiz/execução ativa; erro controlado e captura antiga preservada.
- Cancelar durante extração; não deixar threads, arquivos temporários ou relatórios parciais.

### Verificador e compatibilidade

- Validar manifestos íntegros, alterados e malformados; conferir cada estado por mensagem/anexo.
- Comparar hash público/manifesto assinado e rejeitar referência de versão/esquema incompatível.
- Rodar os testes de formato legado e confirmar que capturas baseadas em screenshots continuam verificáveis sem alteração de resultados.
- Testar limites de memória, quantidade, bytes e anexos com heap restrito; sem OOM como controle normal.

**Aceite:** nenhuma captura é marcada como completa com mensagem/anexo omitido; HTML final abre offline e não executa conteúdo ativo; re-extração ambígua pede intervenção; diffs por mensagem são reproduzíveis; formato legado permanece compatível.

### Matriz do protótipo isolado (`teste_antes_de_aplicar`)

Os scripts `test_html_message_spec.py` e `test_pipeline_acceptance.py` exercitam as regras abaixo com HTML real de referência e fixtures controladas. Execução única: `pwsh -File teste_antes_de_aplicar/run_all_tests.ps1`.

| Etapa/risco | Testes do protótipo | Resultado |
|---|---|---|
| Contrato observado no HTML IPED 4.3.1 e IDs numéricos | `test01_real_iped431_dom_contract_observed`, `test_01_sample_contract` | PASS: 17 `div.linha` com ID numérico, sem duplicatas no arquivo amostrado; inclui ID `243188` |
| Seleção inclusiva, exclusão de auxiliares e duplicatas | `test02_nonmessage_and_range`, `test03_duplicate_messages_keep_distinct_occurrences`, `test_02_inclusive_range_and_aux_exclusion` | PASS |
| Hash determinístico, sensibilidade a mudança e hash-raiz | `test04_hash_stable_ordered_and_content_sensitive`, `test10_manifest_root_deterministic_and_tamper_evident`, `test_03_hash_determinism_and_change` | PASS |
| Fingerprint de origem/conversa, manifesto e versão fixa | `test15_manifest_source_conversation_and_root`, `test16_manifest_changes_on_source_or_message_mutation` | PASS |
| MIME, bytes, tamanho e recusa de recurso inválido | `test05_attachment_bytes_mime_hash_and_size`, `test_04_data_uri_allowlist_and_limit` | PASS |
| Sanitização e renderização offline/escape | `test06_sanitizer_removes_active_and_external_content`, `test07_capture_message_keys_are_not_original_ids`, `test17_offline_html_has_only_selected_and_escaped_content`, `test23_report_escapes_untrusted_fields` | PASS em fixtures sintéticas |
| Âncoras ausentes/ambíguas e reconciliação | `test08_anchors_unique_and_ordered`, `test09_reconcile_states`, `test_05_missing_or_ambiguous_anchor_fails_closed` | PASS |
| Marcador de início em separador de data e resolução para mensagem seguinte | `test_07_date_start_marker_resolves_to_next_message` | PASS no código 2.4.1-beta; WebView real ainda depende de teste manual |
| Falha parcial, limites e cancelamento | `test18_unresolved_attachment_cannot_mark_complete`, `test19_limits_reject_before_parse_or_allocation`, `test20_cancelled_capture_preserves_previous` | PASS em fixtures |
| Escrita transacional e limpeza | `test11_atomic_failure_preserves_previous_and_cleans_temp`, `test12_atomic_success_promotes_valid_payload` | PASS em diretório temporário |
| Caminhos/symlink e referências de verificador | `test14_path_escape_rejected_by_containment_rule`, `test24_symlink_escape_resolves_outside_root`, `test21_reference_requires_same_schema_and_format`, `test22_public_reference_detects_content_divergence` | PASS no protótipo |
| Compatibilidade legada | `test13_legacy_capture_detection_does_not_rewrite`, `test_06_legacy_is_not_rewritten` | PASS conceitual |
| Regressões do inventário legado e allowlist de hashes do plugin | `CaptureIntegrityTest`, `VerifierIntegrityHashTest` | PASS no JAR final 2.4.1-beta |

Além dos testes unitários/conceituais, `test_prototype_end_to_end.py` executa o protótipo completo: extração de intervalo, geração do HTML/manifesto/anexos, gravação, verificação e detecção de adulteração. Inclui fixture controlada e intervalo real do HTML de IPED 4.3.1.

Última execução: **53/53 testes PASS** (`pwsh -File teste_antes_de_aplicar/run_all_tests.ps1`): 7 testes do contrato/protótipo, 24 testes de aceitação, 6 testes end-to-end, 14 testes de integração do Verificador e 2 regressões Java do plugin para inventário legado, allowlist e descoberta de verificadores confiáveis. A integração valida o Verificador 1.3.0 com capturas íntegras, alteradas e anexos, incluindo PDF e arquivo genérico com MIME/assinatura incompatível. Também foram verificados o JAR Java 8 (bytecode 52), `--self-test` do EXE (código 0), compilação do plugin e validação estrutural do JAR. Esses testes não exercitam a janela WebView real do IPED nem cada formato de anexo no caso; essa validação manual continua pendente.

## 11. Estado de aplicação

1. **Contrato e extração:** implementados para `div.linha[id numérico]` observado no IPED 4.3.1, com falha explícita para âncoras ausentes/ambíguas e anexos não resolvidos.
2. **Manifesto e hashes:** implementados em `messages.json`/`manifest.json`, com fingerprint da origem, chave por mensagem, SHA-256 de anexos e hash-raiz.
3. **HTML offline e anexos:** implementados com sanitização, bloqueio de conteúdo remoto/ativo, cópia sob `attachments/` e validação de tipo/tamanho/hash.
4. **Captura/Re-Extrair:** integrados; o resultado temporário é validado antes da promoção e uma falha não substitui a captura anterior.
5. **Auditoria/verificador:** implementados no Verificador 1.3.0, mantendo o caminho legado para screenshots.
6. **Build e testes automatizados:** aprovados conforme os resultados da seção 10; README, changelog, checksums, histórico de hashes e pacote local atualizados.
7. **Validação manual restante:** abrir IPED 4.3.1 com o JAR 2.4.1-beta, capturar e reextrair mensagens com anexos reais, confirmar renderização offline e conferir capturas antigas. Até essa checagem, manter a versão como beta e não declarar a integração visual aprovada.
