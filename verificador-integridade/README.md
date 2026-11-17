# Verificador de Integridade 1.5.1

Aplicativo Windows e Java para conferir a integridade de capturas de chat, consultar hashes e comparar um registro com uma referência pública recebida por outro canal.

## About the Project

O Verificador de Integridade é um aplicativo para Windows e Java que confere capturas de chat, calcula hashes e compara registros com referências externas. A versão 1.5.1 consolida a compilação e o empacotamento determinísticos auditáveis via [`../build-reproducible.ps1`](../build-reproducible.ps1) (Temurin JDK 21.0.13+9 `--release 8` no JAR, `jlink`/`jpackage` + `payload.zip` determinístico + `.NET 10.0.15` com `-p:Deterministic=true` no EXE, timestamps UTC fixos `1980-02-01T00:00:00Z`), preservando todas as proteções de segurança da série 1.4.4 (scanner HTML léxico F-01, `openFile` com `LinkOption.NOFOLLOW_LINKS` e `maxBytes`, `safeRegularFileSize`, `html.disable` Swing, `MAX_SCAN_ENTRIES`, allowlists HTML/MIME simétricas com o plugin e ausência de tetos artificiais de relatório). O JAR funciona com Java 8 (1.8) ou superior. O EXE Windows x64 inclui os runtimes Java 21 (subconjunto mínimo `jlink`) e .NET 10.0.15 necessários.

## Getting Started

1. Copie `integridade-verifier.exe` para a pasta que contém `index.html` e as capturas; ou execute `integridade-verifier.jar` com Java 8 ou superior.
2. Abra o verificador. Selecione **Integridade Local** ou **Integridade Público**.
3. No modo Local, marque as capturas que deseja verificar, clique em **Confirmar** e acompanhe o carregamento; no modo Público, todas as capturas são selecionadas e verificadas automaticamente durante o carregamento.
4. Consulte os hashes e use os botões destacados para copiar ou comparar as referências.

O EXE para Windows x64 inclui os runtimes necessários. O JAR requer Java 8 (1.8) ou superior. O verificador grava os relatórios na pasta `integridade` ao lado das capturas.

Hashes dos binários 1.5.1: JAR `9809DEC2088F50F88ED1691544CEF45B029783B04498F65F43ECC5634C06C7A3`; EXE `E03DEA3BFA298E125804AB22A51C113665FEAE58529248E0C3947C34EACB7C6F`. Consulte [`../versao_hash.md`](../versao_hash.md) para o histórico completo.

Na apresentação HTML 2, o verificador mantém o tema local do IPED 4.3.1 e confere o CSS, CSP e script fixo de parada de mídia; também exige `sandbox=""` no preview PDF e bloqueia conteúdo remoto.

No formato `html-message-v1`, a verificação local valida os hashes de cada mensagem e anexo, a raiz do manifesto, os caminhos e as referências do HTML. Também lê a cadeia de `integridade.json` e, quando a captura foi migrada, confere o vínculo de `migration.json` e `legacy-annotations.json` ao inventário. A comparação pública aceita um manifesto `html-message-v1` correspondente à mesma conversa e versão IPED. Tipos MIME e assinaturas reconhecidos: imagens, áudio, vídeo, PDF, ZIP/OOXML, Office legado, RAR, 7z, texto/CSV e conteúdo genérico `.bin`.

O JAR limita cada JSON a 16 MiB, profundidade a 64 níveis, caminho a 4.096 caracteres e cada captura a 25.010 arquivos. A seleção inteira tem limite de 10.000 capturas, sem limite artificial de tamanho de relatório; exceder um limite estrutural produz erro controlado sem promover relatórios parciais. A busca de capturas ocorre em segundo plano, atualiza a contagem e pode ser cancelada.

## Usage

Na tela inicial, escolha **Integridade Local** para conferir os arquivos com o registro da captura, ou **Integridade Público** para comparar hash ou histórico recebido externamente. Selecione as capturas, confirme e use os botões marcados no tutorial para copiar ou comparar os dados.

### Tutorial ilustrado

Os círculos vermelhos indicam os botões usados em cada etapa. `verificador-icon.png` é somente o ícone do aplicativo e não faz parte das imagens do tutorial.

### 1. Abrir e escolher o modo

Execute o verificador e selecione o tipo de conferência.

![Janela inicial e opções Local/Público](<imagem doc/001.png>)

### 2. Selecionar capturas (somente modo Integridade Local)

No modo **Integridade Local**, marque as capturas que deseja incluir e clique em **Confirmar** — uma janela modal de carregamento exibirá o progresso da validação antes de abrir o resumo.

No modo **Integridade Público**, a tela de seleção de capturas não é exibida: uma única janela de carregamento descobre todas as capturas válidas, executa a verificação e abre diretamente a tela **Integridade Público**.

### 3. Resultado do modo Local

Consulte os hashes do conjunto, abra o relatório `integridade.html` (que valida a referência pública e marca cada captura como **OK** ou **FALHA**) ou exporte `historico-capturas.txt` e o resumo público anonimizado (`SHA-256 do histórico completo público`).

![Seleção das capturas](<imagem doc/002.png>)

### 3. Conferir o resultado Local

O modo Local compara os arquivos com o registro `integridade.json` de cada captura. Use os botões circulados para copiar a lista de hashes ou o histórico.

![Resultado do modo Local](<imagem doc/004_local.png>)

### 4. Comparar uma referência Pública

Cole o hash recebido e compare, ou cole o conteúdo completo de `historico-capturas.txt` e compare o registro. O botão **OK** apenas fecha a janela; não é um botão de comparação.

![Comparação no modo Público](<imagem doc/004_publico.png>)

## Licença e Copyright

**Copyright (C) 2026 `iagobusca`** — Este utilitário e seu lançador são distribuídos sob os termos da licença **GNU General Public License v3.0 (GPLv3)** (consulte [`../LICENSE`](../LICENSE) e [`../NOTICE`](../NOTICE)).

## Arquivos e documentação

- `integridade-verifier.exe`: versão Windows x64 independente.
- `integridade-verifier.jar`: versão Java 8 (1.8) ou superior.
- `doc.md`: funcionamento detalhado, pastas temporárias, dependências e códigos de saída.
- `CHANGELOG.md`: histórico de alterações.

Para compilar o JAR, use `build.bat` com `JAVA_HOME` configurado. Para reconstruir o EXE, use `build-single-exe.ps1`; o build exige JDK 11, um JDK com `jpackage` e .NET SDK 10.
