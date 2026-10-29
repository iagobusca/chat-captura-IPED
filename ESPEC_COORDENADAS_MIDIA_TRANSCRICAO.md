# Especificacao: Coordenadas separadas para midia e transcricao

## Objetivo
Permitir posicionar botoes de midia e transcricao exatamente sobre o icone de audio/video ou sobre o texto da transcricao, em vez de usar apenas o retangulo da mensagem.

## Campos novos por bloco
- `mediaX`, `mediaY`, `mediaWidth`, `mediaHeight`
- `textX`, `textY`, `textWidth`, `textHeight`

## Fluxo
1. Durante a captura, o JavaScript identifica o elemento de midia e o elemento textual dentro da mensagem.
2. As coordenadas desses subelementos sao calculadas em relacao ao bloco da mensagem.
3. O Java armazena esses campos em `CaptureBlock`.
4. Os campos sao gravados em `manifest.json` e `whatsapp-coordinates.json`.
5. O `whatsapp.html` usa `mediaRect` para o botao de midia e `transcriptRect` para o botao `T`.
6. Se os subcampos nao existirem, usar fallback para o retangulo da mensagem.

## Compatibilidade
- Capturas antigas continuam funcionando com fallback.
- Campos ausentes ou invalidos nao quebram a renderizacao.

## Testes sugeridos
- Capturar chat com mensagem de audio e transcricao.
- Capturar chat com mensagem de video e transcricao.
- Verificar se os botoes ficam sobre os elementos corretos.
- Testar fallback em captura antiga sem os campos.