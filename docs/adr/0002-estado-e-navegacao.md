# 0002 — Estado em um único store; navegação sem biblioteca

**Status:** aceito · 2026-10

## Decisão

- O estado do navegador (abas, aba selecionada, carregamento, segurança) vive em um único `BrowserStore` com estado imutável exposto como `StateFlow`. Os eventos do motor viram ações que atualizam esse estado; a interface só lê.
- As telas do app (navegador, abas, configurações, biblioteca) são poucas e rasas. Uma pilha de telas simples, mantida no `ViewModel` principal e tratada pelo botão voltar, cobre tudo sem uma biblioteca de navegação.
- Injeção de dependências manual em um `AppContainer`.

## Consequências

Menos dependências e nenhuma reflexão em tempo de execução. Se o app crescer para dezenas de telas com links profundos, reavaliar (Navigation 3).
