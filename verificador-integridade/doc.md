# Verificador de Integridade 1.2.1

**Versão:** 1.2.1 | Data: 31/10/2026.

## Índice

- [Visao geral](#visao-geral)
- [Arquitetura e funcionamento](#arquitetura-e-funcionamento)
- [Execucao](#execucao)
- [Pastas temporarias](#pastas-temporarias)
- [Dependencias](#dependencias)
- [Codigos de saida e erros](#codigos-de-saida-e-erros)
- [Limitacoes e migracao](#limitacoes-e-migracao)

## Visao geral

Ao iniciar, o verificador oferece **Integridade Local** (compara conteúdo ao registro local) ou **Integridade Público** (compara com hash público ou histórico completo recebido externamente). Depois, lê capturas existentes, recalcula hashes e grava relatórios. O pacote oferece um JAR Java 11 e um EXE Windows de arquivo único. O EXE contém o runtime Java e o runtime .NET necessários; o usuário não precisa instalar Java ou .NET.

## Arquitetura e funcionamento

```mermaid
graph TD
    Usuario[Usuário] --> EXE[EXE independente]
    EXE --> Temp[Pasta temporária exclusiva]
    Temp --> App[Aplicativo empacotado]
    Temp --> Java[Runtime Java incluído]
    App --> Capturas[Pasta ao lado do EXE]
    Capturas --> Relatorios[Relatórios de integridade]
    EXE --> Limpeza[Limpeza da pasta própria ao encerrar]
```

Cada execução cria uma pasta `instance-<pid>-<id>` dentro de `%TEMP%\Avilla\IntegridadeVerifier`. Dentro dela ficam `app`, `runtime` e um bloqueio de uso exclusivo. Execuções simultâneas usam pastas diferentes. O EXE valida os SHA-256 do pacote depois de extraí-lo.

## Execucao

Para abrir a interface, execute `integridade-verifier.exe` ao lado de `index.html`. O verificador usa essa pasta para localizar as capturas.

O JAR pode ser executado com Java 11 ou superior:

```powershell
java -jar .\integridade-verifier.jar
```

A opção interna de compilação `--self-test` extrai e valida o pacote, sem abrir a interface. A opção de teste `--self-test-hold-ms=<milissegundos>` mantém o processo aberto por até 60 segundos para conferir instâncias simultâneas.

## Pastas temporarias

Na inicialização, o EXE verifica se há outro processo do verificador em execução. Se não houver, remove os diretórios reconhecidos como resíduos antigos: `execucao-atual`, `app`, `runtime`, `app_*`, `runtime_*`, `instance-*` e `instancia-*`. Se outra instância estiver aberta, ela mantém seus arquivos temporários; a nova execução ainda inicia em sua própria pasta. Ao terminar, cada processo tenta remover somente a pasta que criou. Resíduos bloqueados ou inacessíveis são preservados para uma tentativa posterior.

## Dependencias

- Uso do JAR: Java 11 ou superior.
- Uso do EXE: Windows x64; não requer Java nem .NET instalados.
- Build: JDK 11, JDK com `jpackage`, .NET SDK 10 e PowerShell.
- O aplicativo Java usa `java.base`, `java.desktop` e a biblioteca JSON embutida no JAR standalone.

## Codigos de saida e erros

| Código | Significado |
| --- | --- |
| `0` | Interface fechada normalmente ou teste interno de extração concluído. |
| `1` | Falha ao preparar, extrair ou validar o pacote do EXE. |

Em falhas do lançador, uma janela informa o erro. O processo Java pode apresentar mensagens próprias da interface.

## Limitacoes e migracao

A limpeza atua somente em diretórios reconhecidos dentro de `%TEMP%\Avilla\IntegridadeVerifier`; não percorre outras pastas do Windows. Se uma instância ainda estiver aberta, a limpeza geral é adiada para evitar apagar arquivos em uso. A versão 1.2.1 acrescenta os modos local/público e um registro público determinístico. A comparação pública depende de uma referência obtida fora da pasta verificada.



## Referência pública do histórico

O arquivo integridade/historico-capturas.txt usa o formato HISTORICO_PUBLICO_V1, ordena capturas por nome e não inclui o horário da execução. Assim, o hash SHA-256 permanece estável para o mesmo estado. Cada captura inclui o SHA-256 do integridade.json, que vincula o inventário e a cadeia de eventos completos, além das linhas de inventário com caminho, tamanho e hashes esperado/encontrado de cada arquivo. No modo **Integridade Público**, cole esse hash ou o conteúdo integral do arquivo recebido por canal confiável. A comparação informa igualdade com aquela referência; a checagem dos arquivos contra o inventário ainda aparece separadamente como estado local.
