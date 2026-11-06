# Análise: ajustes no plugin (aviso informativo) e no verificador (versão + janela)

## 1. Janela de aviso do plugin (informativa, não urgente)

### Problema atual
A janela exibida em `VerifierIntegrity.showAlertIfNeeded` (chamada em Start e Re-Extrair) tem tom de alerta ("Verificador de integridade - aviso", WARNING_MESSAGE) e texto que sugere problema. O usuário quer um **informativo**, não urgente.

### Proposta
- Trocar `JOptionPane.WARNING_MESSAGE` por `JOptionPane.INFORMATION_MESSAGE`.
- Título: "Informação sobre o verificador de integridade".
- Texto (profissional, explicativo):

> Informação sobre o verificador de integridade
>
> Este plugin pode copiar o verificador de integridade (integridade-verifier.jar/exe) para a pasta de captura. Se você recebeu este caso de terceiros e não confia plenamente na origem, o verificador presente pode ter sido adulterado ou substituído.
>
> Para ter certeza da validade:
> - Baixe o verificador do repositório oficial: https://github.com/iagobusca/chat-captura-IPED
> - Compare o hash SHA-256 do arquivo existente com o hash publicado no repositório (release v2.3.1-beta).
> - Se quiser validar com máxima garantia, use o modo "Integridade Público" do verificador, informando a hash obtida de uma fonte em que você confie.
>
> Esta mensagem é apenas informativa: a captura/re-extração continuará normalmente. Nenhum arquivo foi alterado.

- Tornar a URL clicável: usar um `JEditorPane`/`JTextPane` com `ContentType("text/html")` e um `HyperlinkListener` que abre o navegador via `Desktop.browse(URI)`. Ou um `JLabel` com HTML + MouseListener. Sugestão: `JTextPane` com HTML e link clicável.
- Botão único "Fechar" (OK).
- Exibir uma vez por sessão (flag `alertShown`), no Start e no Re-Extrair, após copiar o verificador.

### Decisões em aberto
- Exibir sempre (mesmo sem verificador) ou só quando houver verificador? Sugestão: sempre (informativo geral), acrescentando o resultado da verificação de hash se houver verificador na pasta.

## 2. Verificador: exibir versão + janela com ícone e controles (topo/minimizar)

### Problema atual
O verificador é um `JOptionPane`/`JDialog` simples, sem título com versão, sem ícone, sem always-on-top/minimize.

### Proposta
- Converter os diálogos principais para `JFrame`/`JDialog` com:
  - Título: "Verificador de Integridade — v1.2.2" (lê a versão de um recurso/constante).
  - Ícone da janela: carregar imagem de `imagem doc/` (ex.: `verificador-icon.png`) via `setIconImage`.
  - Botões na barra de título: minimizar e "sempre no topo" (toggle). Usar `setAlwaysOnTop(true/false)` e `setState(JFrame.ICONIFIED)` via botões customizados, ou usar `JFrame` padrão (que já tem minimizar/maximizar/fechar na barra do SO). O usuário pediu "clicar para o programa subir para o topo ou minimizar" → adicionar botões explícitos "Sempre no topo" e "Minimizar" no painel, além da barra padrão.
  - Exibir a versão do verificador (1.2.2) e a plataforma (Windows/Linux/mac) na janela inicial e no resumo.
- A versão deve ser lida de um arquivo `VERSION` embutido no jar ou constante `VERIFIER_VERSION = "1.2.2"`. Plataforma via `System.getProperty("os.name")`.

### Janelas afetadas
- Tela inicial (Integridade Local / Público) — `chooseMode`.
- "Selecione as capturas" — `chooseCaptures`.
- Resultado da verificação — `showSummaryDialog`.
- "Integridade Público — comparar referência" — `comparePublicHistory`.

### Decisões em aberto
- Usar `JFrame` (com barra do SO) ou `JDialog` modal com botões customizados? Sugestão: `JDialog` não-modal com botões "Sempre no topo"/"Minimizar" customizados, para manter o fluxo atual.
- Ícone: qual imagem? O usuário vai colocar em `imagem doc/`. Sugestão: `imagem doc/verificador-icon.png` (ou .ico). Carregar com fallback (se ausente, usar ícone padrão).

## 3. Pasta "imagem doc" no verificador-integridade

### Proposta
- Criar `verificador-integridade/imagem doc/` contendo imagens do verificador (ícone, screenshots, tutorial).
- O verificador carrega o ícone de `imagem doc/verificador-icon.png` (relativo ao jar ou ao diretório de execução).
- A documentação (doc.md) pode referenciar as imagens.
- O instalador/EXE deve embutir a pasta `imagem doc` para que o ícone esteja disponível em runtime.

### Estrutura proposta
```
verificador-integridade/
  imagem doc/
    verificador-icon.png
    tutorial-selecao.png
    tutorial-resultado.png
  src/IntegrityVerifier.java
  ...
```

### Decisões em aberto
- Formato do ícone: PNG (recomendado) ou ICO? Sugestão: PNG 256x256 (Java carrega via ImageIcon/Toolkit).
- O EXE autoextraível precisa incluir a pasta? Sim, adicionar ao payload do launcher.

## 4. Impacto e próximos passos

- Plugin: ajustar `VerifierIntegrity.showAlertIfNeeded` (informativo + link clicável) e remover tom de alerta.
- Verificador: converter diálogos para JDialog/JFrame com versão, ícone, botões topo/minimizar; embutir `imagem doc`.
- Rebuild: JAR do plugin, JAR do verificador, EXE do verificador.
- Deploy: plugin nos IPEDs de teste; verificador no repositório (release).

### Aprovação
Aprovar a ideia (com ajustes) para implementar? Especificar quais imagens serão colocadas em `imagem doc/` e se a URL do repositório está correta (https://github.com/iagobusca/chat-captura-IPED).
