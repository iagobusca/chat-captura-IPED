# Análise: verificação de integridade do verificador no plugin IPED

## 1. Objetivo

Adicionar ao plugin IPED (chat-captura) uma checagem do `integridade-verifier.jar`/`.exe` que possa estar na pasta do caso. Se o arquivo existir e seu SHA-256 não corresponder a uma versão conhecida/validada, exibir um alerta informativo (não bloqueante) sugerindo baixar o verificador do repositório oficial ou comparar o hash. Objetivo: permitir que o analista desconfie de casos recebidos de terceiros sem bloquear o fluxo de captura.

## 2. Escopo e não-escopo

- Escopo: detecção de arquivos `integridade-verifier.jar`/`.exe` na pasta do caso, cálculo de SHA-256, comparação com lista de hashes válidos, alerta uma vez por sessão em Start e Re-Extrair.
- Não-escopo: não impede a captura; não baixa o verificador; não altera arquivos. Não verifica outros componentes.

## 3. Lista de hashes válidos

Criar classe `VerifierHashes` (ou constantes em `CaptureIntegrity`) com um `Map<String, Set<String>>` (nome do arquivo → conjunto de SHA-256 válidos) cobrindo as versões conhecidas:

| Arquivo | Versão | SHA-256 |
|---|---|---|
| integridade-verifier.jar | 1.2.1 | A5415A32F1F9423AB0A187675A38BF71DB640FD346F6846F55510F4856CE3345 |
| integridade-verifier.jar | 1.2.2 | 08A6752270A5C6742865629CA7FD62601D6BCC0028DCECCADF5A566707B5E7C3 |
| integridade-verifier.exe | 1.2.1 | FA04E2DB2A9EC35A6A53DC2713FF4DAC55B8253ECAAC32104F9612612196665F |
| integridade-verifier.exe | 1.2.2 | F1433DBC91607F3FA1CC420C3B42B0DE1404ABEA92BC9F7D3FA91441DA821245 |

Observação: preciso recalcular o hash do jar 1.2.1 original (release-assets antigo) para preencher a lista. Será feito na implementação.

## 4. Onde procurar

A partir de `outputFolder` (pasta de captura informada pelo usuário), verificar:
- o próprio diretório;
- até 3 níveis de diretórios ancestrais (caso a pasta de captura esteja dentro de uma pasta de caso que contém o verificador);
- em cada um desses diretórios, procurar arquivos com nome exato `integridade-verifier.jar` e `integridade-verifier.exe` (sem recursão profunda, para evitar lentidão).

Se nenhum for encontrado, não exibir alerta (verificador é opcional).

## 5. Quando verificar

- Em `startChatCapture(...)` (antes de iniciar a thread de captura) e em `showRecaptureDialog(...)` (ao abrir o diálogo de re-extração), chamar `checkVerifierIntegrity(outputFolder)`.
- Usar flag estático `verifierAlertShown` para exibir o alerta no máximo uma vez por sessão do viewer (evitar repetição em várias capturas).

## 6. Janela de alerta (não bloqueante)

Título: "Verificador de integridade — aviso"
Mensagem (profissional, informativa, exibida uma vez por sessão):

> Verificador de integridade não validado
>
> Foi encontrado um arquivo `integridade-verifier.jar` ou `integridade-verifier.exe` na pasta do caso, mas o seu hash SHA-256 não corresponde a nenhuma versão conhecida e validada pelo projeto.
>
> O que isso significa:
> - Se você recebeu este caso (pasta zipada) de terceiros e não confia plenamente na origem, o verificador presente pode ter sido adulterado ou substituído.
> - Para garantir a confiabilidade da verificação, baixe o verificador diretamente do repositório oficial do projeto ou compare o hash do arquivo existente com o hash publicado no repositório.
> - Se o hash bater com o publicado, o verificador é válido; caso contrário, substitua-o pelo obtido no repositório oficial antes de confiar nos resultados.
>
> Esta mensagem é apenas informativa: a captura/re-extração continuará normalmente. Nenhum arquivo foi alterado.

Botão: "Fechar" (único). Janela modal simples (`JOptionPane.showMessageDialog` ou JDialog customizado), não bloqueia a captura.

Referência dos hashes válidos (repositório oficial):
- https://github.com/.../chat-captura-IPED (release v2.3.0-beta) — integridade-verifier.jar/exe.

## 7. Implementação proposta

- Nova classe `VerifierIntegrity` (ou método estático em `CaptureIntegrity`) com:
  - `static final Map<String, Set<String>> VALID_HASHES`
  - `static List<Path> findVerifierFiles(Path start)`
  - `static boolean isHashValid(String fileName, String sha256)`
  - `static void showAlertIfNeeded(Component parent, Path outputFolder)`
- Integração em `HtmlLinkViewer.startChatCapture` e `showRecaptureDialog`: chamar `VerifierIntegrity.showAlertIfNeeded(SwingUtilities.getWindowAncestor(chatCapturePanel), outputFolder)` antes de prosseguir.
- Flag estático `alertShown` em `VerifierIntegrity`.

## 8. Decisões em aberto

1. Busca: apenas outputFolder + até 3 pais, ou também recursiva na pasta do caso? Sugestão: não recursiva profunda (performance).
2. Incluir hashes de versões antigas (1.2.0 e anteriores) na lista? Sugestão: incluir apenas 1.2.1 e 1.2.2 (versões com registro público). Versões antigas gerariam alerta; aceitável.
3. Se o verificador estiver em subpasta (ex.: `integridade/`), buscar também nela? Sugestão: sim, um nível.
4. Texto do alerta: usar a mensagem acima ou ajustar? Aberto.

## 9. Próximo passo

Aprovar a ideia (com ajustes) para implementar. A implementação não altera o formato de captura nem o verificador; apenas adiciona checagem + alerta.
