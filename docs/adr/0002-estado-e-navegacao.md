# 0002 — Estado em um único dono; navegação sem biblioteca

**Status:** aceito · 2026-10

## Decisão

- O estado do navegador (abas, aba selecionada, carregamento, segurança) vive em um único `BrowserController`, com estado imutável exposto como `StateFlow`. Os eventos do motor viram atualizações desse estado; a interface só lê e chama métodos.
- Renderizadores são criados sob demanda: uma aba restaurada ou suspensa não tem sessão do motor até ser selecionada. Abas em segundo plano sem uso são suspensas (o histórico da aba é salvo e restaurado).
- “Nova aba” mostra a página inicial do Zeno sem criar uma aba; a aba nasce quando algo é aberto. Assim, cancelar não deixa abas vazias para trás.
- As telas do app (navegador, abas, configurações, biblioteca) são poucas e rasas. Uma pilha de telas simples, mantida no `MainViewModel` e tratada pelo botão voltar, cobre tudo sem uma biblioteca de navegação. O navegador fica composto por baixo das outras telas, para a página não perder a superfície ao voltar.
- Injeção de dependências manual em um `AppContainer`.

## Consequências

Menos dependências e nenhuma reflexão em tempo de execução. O `BrowserController` é Kotlin puro e testado na JVM com um motor falso. Se o app crescer para dezenas de telas com links profundos, reavaliar (Navigation 3).
