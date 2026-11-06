# Changelog

Mudanças relevantes do Verificador de Integridade.

## [Não Lançado] - Em desenvolvimento

## [1.2.5] - 2026-11-06

### Segurança

- Atualizada a biblioteca `org.json` embutida para 20260719, corrigindo o processamento custoso de números JSON longos (CVE-2026-59171, CWE-400/CWE-770).
- Entradas JSON limitadas a 16 MiB e capturas limitadas a 25.010 arquivos para encerrar entradas excessivas com erro controlado.
- Hash canônico calculado em fluxo para evitar acumular a concatenação completa na memória.
- Relatórios gravados com temporários exclusivos; diretórios com links simbólicos são rejeitados.

## [1.2.4] - 2026-11-06

### Corrigido

- Cópia de hashes e histórico para a área de transferência executada fora da thread da interface; as mensagens de confirmação ficam associadas à janela ativa.
- README atualizado com o tutorial visual, destaques dos botões e distinção entre as imagens do tutorial e o ícone do aplicativo.

## [1.2.3] - 2026-11-05

### Removido

- Botões "Sempre no topo: ON/OFF" e "Minimizar" das janelas principais.

### Modificado

- As janelas continuam fixas sobre as demais (always-on-top), sem botão para alternar esse comportamento.
- A minimização passa a usar apenas o botão nativo da barra de título do Windows.

## [1.2.2] - 2026-11-05

### Adicionado

- Botões "Sempre no topo: ON/OFF" e "Minimizar" em todos os diálogos principais.

### Modificado

- Diálogos principais convertidos para janelas `JFrame` nativas, com entrada na barra de tarefas; o botão "Minimizar" usa o estado nativo e o clique na barra de tarefas restaura a janela.
- Ícone do verificador embutido no JAR, garantindo a identificação das janelas mesmo quando o aplicativo é extraído para uma pasta temporária.
- Janela "Selecione as capturas" com tamanho adaptativo ao monitor (altura máxima = 65% da tela) e scroll vertical quando houver mais itens do que a área visível.
- Tamanho mínimo da janela definido em 640x640.
- Cada execução usa uma pasta temporária exclusiva (`instance-{PID}-{GUID}`); restos antigos só são apagados quando não há outra instância ativa.
- O JVM encerra em todos os caminhos de fechamento, permitindo que o lançador aguarde o término e remova a pasta temporária.
- O aviso "Nenhuma captura" também encerra o processo quando fechado pelo botão X.

### Corrigido

- Empacotamento de todas as classes internas no JAR, evitando `NoClassDefFoundError` ao abrir os diálogos.

## [1.2.1] - 2026-10-31

### Modificado

- Resumo final em blocos estreitos, com cada botão Copiar antes do hash e quebra de linha nos valores longos.
- Janela Integridade Público mais compacta.

## [1.2.0] - 2026-10-31

### Adicionado

- Tela inicial com opções explícitas **Integridade Local** e **Integridade Público**.
- Comparação pública aceita o hash SHA-256 do registro estável ou o texto completo historico-capturas.txt.
- Registro público versionado, determinístico, com inventário completo de caminhos/tamanhos/hashes esperado e encontrado, vinculado ao hash do integridade.json de cada captura; não inclui o horário volátil da execução.

### Corrigido

- O hash do histórico compartilhado não muda só por executar o verificador em outro horário.

## [1.1.0] - 2026-10-31

### Adicionado

- Pastas temporárias exclusivas por execução do EXE.
- Limpeza de restos conhecidos quando nenhuma outra instância do verificador está aberta.
- Validação do pacote extraído contra o manifesto SHA-256 embutido.

### Modificado

- O EXE permite execuções simultâneas sem reutilizar a mesma pasta `app`/`runtime`.
- Versão sincronizada entre o projeto .NET, o JAR e o empacotamento do EXE.
