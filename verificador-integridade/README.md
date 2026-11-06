# Verificador de Integridade 1.2.5

Aplicativo Windows e Java para conferir a integridade de capturas de chat, consultar hashes e comparar um registro com uma referência pública recebida por outro canal.

## About the Project

O Verificador de Integridade é um aplicativo para Windows e Java que confere capturas de chat, calcula hashes e compara registros com referências externas. A versão 1.2.5 atualiza a biblioteca JSON para corrigir processamento custoso de números longos, limita os metadados a 16 MiB e as capturas a 25.010 arquivos, calcula hashes em fluxo e protege as gravações contra links simbólicos.

## Getting Started

1. Copie `integridade-verifier.exe` para a pasta que contém `index.html` e as capturas; ou execute `integridade-verifier.jar` com Java 11 ou superior.
2. Abra o verificador. Selecione **Integridade Local** ou **Integridade Público**.
3. Marque as capturas que deseja verificar e clique em **OK**.
4. Consulte os hashes e use os botões destacados para copiar ou comparar as referências.

O EXE para Windows x64 inclui os runtimes necessários. O JAR requer Java 11 ou superior. O verificador grava os relatórios na pasta `integridade` ao lado das capturas.

## Usage

Na tela inicial, escolha **Integridade Local** para conferir os arquivos com o registro da captura, ou **Integridade Público** para comparar hash ou histórico recebido externamente. Selecione as capturas, confirme e use os botões marcados no tutorial para copiar ou comparar os dados.

### Tutorial ilustrado

Os círculos vermelhos indicam os botões usados em cada etapa. `verificador-icon.png` é somente o ícone do aplicativo e não faz parte das imagens do tutorial.

### 1. Abrir e escolher o modo

Execute o verificador e selecione o tipo de conferência.

![Janela inicial e opções Local/Público](<imagem doc/001.png>)

### 2. Selecionar capturas

Marque as capturas que deseja incluir e confirme em **OK**.

![Seleção das capturas](<imagem doc/002.png>)

### 3. Conferir o resultado Local

O modo Local compara os arquivos com o registro `integridade.json` de cada captura. Use os botões circulados para copiar a lista de hashes ou o histórico.

![Resultado do modo Local](<imagem doc/004_local.png>)

### 4. Comparar uma referência Pública

Cole o hash recebido e compare, ou cole o conteúdo completo de `historico-capturas.txt` e compare o registro. O botão **OK** apenas fecha a janela; não é um botão de comparação.

![Comparação no modo Público](<imagem doc/004_publico.png>)

## License

O repositório não informa uma licença específica para este utilitário.

## Arquivos e documentação

- `integridade-verifier.exe`: versão Windows x64 independente.
- `integridade-verifier.jar`: versão Java 11 ou superior.
- `doc.md`: funcionamento detalhado, pastas temporárias, dependências e códigos de saída.
- `CHANGELOG.md`: histórico de alterações.

Para compilar o JAR, use `build.bat` com `JAVA_HOME` configurado. Para reconstruir o EXE, use `build-single-exe.ps1`; o build exige JDK 11, um JDK com `jpackage` e .NET SDK 10.
