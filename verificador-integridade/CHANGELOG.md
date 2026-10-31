# Changelog

Mudanças relevantes do Verificador de Integridade.

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

## [Não Lançado] - Em desenvolvimento

## [1.1.0] - 2026-10-31

### Adicionado

- Pastas temporárias exclusivas por execução do EXE.
- Limpeza de restos conhecidos quando nenhuma outra instância do verificador está aberta.
- Validação do pacote extraído contra o manifesto SHA-256 embutido.

### Modificado

- O EXE permite execuções simultâneas sem reutilizar a mesma pasta `app`/`runtime`.
- Versão sincronizada entre o projeto .NET, o JAR e o empacotamento do EXE.
