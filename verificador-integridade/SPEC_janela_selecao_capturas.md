# Análise: janela "Selecione as capturas" do Verificador de Integridade

## 1. Diagnóstico

Arquivo: `verificador-integridade/src/IntegrityVerifier.java`, método `chooseCaptures` (linhas ~117-121):

```java
JPanel panel = new JPanel();
panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
JCheckBox all = new JCheckBox("Desmarcar todas", true);
panel.add(all);
List<JCheckBox> boxes = new ArrayList<>();
for (Capture c : captures) { JCheckBox b = new JCheckBox(c.name, true); boxes.add(b); panel.add(b); }
all.addActionListener(e -> { ... });
int r = JOptionPane.showConfirmDialog(null, new JScrollPane(panel),
        "Selecione as capturas", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
```

Problemas:
- O `JScrollPane` não tem `preferredSize` definido, então o `JOptionPane` dimensiona a janela pelo tamanho natural do painel (todas as checkboxes empilhadas). Com >20 capturas a janela cresce além da área útil da tela.
- Não há limite de altura nem tamanho mínimo; em monitores de 768 px de altura a janela não cabe e as ações (OK/Cancelar) ficam fora da tela.
- `JOptionPane.showConfirmDialog` não permite controlar `setMinimumSize`/`setPreferredSize` de forma confiável antes de exibir.

## 2. Objetivo

- Janela "Selecione as capturas" com scroll vertical efetivo quando houver mais itens do que a área visível.
- Tamanho adaptativo ao monitor do usuário.
- Tamanho mínimo da janela: 640 (largura e altura mínimas).
- Sem quebrar o fluxo atual (OK/Cancelar, "Marcar/Desmarcar todas").

## 3. Proposta de implementação

Substituir `JOptionPane.showConfirmDialog` por um `JDialog` customizado, construído a partir de um `JOptionPane` (ou painel próprio) para manter o estilo. Passos:

1. Calcular geometria do monitor:
   ```java
   Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
   int margin = 80;
   int minWidth = 640, minHeight = 640;
   int maxWidth = Math.max(minWidth, screen.width - margin);
   int maxHeight = Math.max(minHeight, screen.height - margin);
   int width  = Math.min(maxWidth, Math.max(minWidth, 560));   // alvo ~560-900
   int height = Math.min(maxHeight, Math.max(minHeight, 700)); // alvo ~700, limitado pela tela
   ```
   Observação: a altura efetiva visível do scroll será `height - 120` (título + botões).

2. Configurar o scroll:
   ```java
   JScrollPane scroll = new JScrollPane(panel);
   scroll.setPreferredSize(new Dimension(width - 40, height - 120));
   scroll.setMinimumSize(new Dimension(minWidth - 40, minHeight - 120));
   scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
   scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
   ```

3. Usar `JOptionPane` + `JDialog` para controlar tamanho mínimo:
   ```java
   JOptionPane option = new JOptionPane(scroll, JOptionPane.PLAIN_MESSAGE,
           JOptionPane.OK_CANCEL_OPTION);
   JDialog dialog = option.createDialog(null, "Selecione as capturas");
   dialog.setMinimumSize(new Dimension(minWidth, minHeight));
   dialog.setResizable(true);
   dialog.pack();                 // respeita preferredSize do scroll
   // centralizar e não exceder a tela:
   dialog.setLocationRelativeTo(null);
   dialog.setVisible(true);
   int r = (option.getValue() instanceof Integer) ? (Integer) option.getValue()
           : JOptionPane.CANCEL_OPTION;
   ```

4. Manter a lógica de "Marcar/Desmarcar todas" e a coleta de selecionados inalterada.

## 4. Decisões em aberto para aprovação

- **Altura máxima**: usar 70% da altura da tela ou teto fixo de ~760 px? Sugestão: `min(screen.height - 80, 760)` (nunca maior que a tela, nunca menor que 640).
- **Largura**: mínimo 640; alvo 640-900 conforme tela. Nomes longos de captura podem precisar de mais largura; sugestão `min(screen.width - 80, 900)`.
- **Aplicar o mesmo padrão** a outras janelas (`showSummaryDialog`, `comparePublicHistory`, `chooseMode`)? Apenas a "Selecione as capturas" foi pedida; as demais ficam para uma etapa futura.
- **Rebuild do EXE**: o `.exe` é gerado por `build-single-exe.ps1` (jpackage + .NET launcher). Após alterar o `.java`, é necessário recompilar o JAR (`build.bat`) e depois regenerar o EXE. Posso fazer o build do JAR agora; o EXE exige .NET SDK 10 e jpackage — confirmar se devemos tentar.

## 5. Impacto

- Arquivo alterado: `verificador-integridade/src/IntegrityVerifier.java` (método `chooseCaptures`).
- Versão do verificador sobe para 1.2.2 (CHANGELOG + VERSION).
- JAR regenerado (`integridade-verifier.jar`); EXE regenerado se solicitado.
- Nenhuma mudança no plugin IPED (JAR do IPED) nem nas capturas.

## 6. Próximo passo (após aprovação)

Implementar o patch em `chooseCaptures`, recompilar JAR com `build.bat` (JAVA_HOME JDK 11), validar executando `java -jar integridade-verifier.jar` em uma pasta com capturas de teste e conferir que a janela não excede a tela com >20 itens.
