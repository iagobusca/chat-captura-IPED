# Validação da SPEC_RENDERIZACAO_WHATSAPP_HTML

Data: 2026-11-07

## Testes prévios do protótipo (etapas A–F)

Executado `teste_antes_de_aplicar/renderizacao_whatsapp/run_rendering_spec_tests.ps1` depois da aplicação ao código. Resultado: 6/6 aprovados em 47,0 s.

- A: contrato visual do IPED 4.3.1 validado usando seu JavaFX WebView e o HTML de exemplo.
- B: intervalo selecionado, ordem, conteúdo autônomo e determinismo aprovados.
- C: imagem, PDF, DOC/DOCX, arquivo genérico, áudio/vídeo, ações e parada da mídia aprovados.
- D: CSP/script, CSS, SVG, MIME, fragmentos, caminhos e conteúdo ativo adulterado rejeitados.
- E: abertura offline em `file:///` no JavaFX WebView do IPED 4.3.1 passou; não foram observadas dependências remotas.
- F: baseline e compatibilidade de capturas legadas passaram sem reescrita.

## Verificação automatizada de produção

Executado `teste_antes_de_aplicar/run_all_tests.ps1`; resultado aprovado:

- 7 testes do protótipo de captura.
- 24 testes de aceitação do pipeline.
- 6 testes end-to-end de captura HTML por mensagem.
- 16 testes Java de integração do Verificador.
- 2 testes unitários de integridade/allowlist do plugin.

Total: 65 testes automatizados aprovados: 55 na suíte geral, 6 etapas A–F e 4 verificações diretas do contrato do renderizador de produção e do JAR compilado. O JAR compilado foi executado em Java 8 e verificou a captura existente `captura-chat-20261107-090152` como consistente. O EXE compilado passou em `--self-test` com código 0. O JAR do plugin passou em `validate-plugin-jar.ps1`; o ZIP contém 10 arquivos e os quatro binários principais conferem byte a byte com os artefatos do repositório.

## Artefatos validados

- Plugin: 2.5.0-beta, `iped-viewers-impl-4.3.1.jar`, 622.142 bytes.
- Verificador JAR: 1.3.1, Java 8 bytecode 52, 131.441 bytes.
- Verificador EXE: 1.3.1 Windows x64, 78.384.196 bytes.
- Instalador: `instalador-chat-captura-IPED-2.5.0-beta-verificador-1.3.1.zip`, 73.668.564 bytes.

Os SHA-256 finais estão em `CHECKSUMS.md` e `versao_hash.md`.

## Limite da validação

A captura de referência fornecida é anterior à nova apresentação; foi validada pelo Verificador para confirmar compatibilidade legada. Nesta execução não foi criada uma captura nova pela janela do plugin nem aberta no IPED completo. Portanto, o fluxo interativo de geração dentro do IPED continua pendente de validação manual; os testes do protótipo no WebView e os testes automatizados de produção não substituem esse passo.
