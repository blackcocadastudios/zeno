# 0001 — GeckoView direto, sem Android Components

**Status:** aceito · 2026-10

## Contexto

A primeira ideia foi partir do Reference Browser da Mozilla, montado sobre os Mozilla Android Components (AC). Os AC resolvem muita coisa (abas, prompts, downloads, extensões), mas trazem a arquitetura do Fenix, componentes de interface baseados em Views, a biblioteca nativa de application-services e integrações de telemetria. Isso vai contra os dois pedidos centrais do Zeno: leveza e um desenho próprio, minimalista.

## Decisão

Usar o **GeckoView diretamente**, atrás de uma interface própria e pequena (`:core:engine`), implementada só em `:engine:gecko`. A interface é toda em Compose, com o sistema de design do Zeno.

## Consequências

- Implementamos nós mesmos os delegates de que precisamos (navegação, progresso, conteúdo, prompts, permissões, histórico, extensões). É mais código, mas cada parte é conhecida e removível.
- Ganhamos controle total do layout, nenhuma dependência transitiva desnecessária e testes na JVM com um motor falso.
- Atualizar o GeckoView afeta um único módulo. Mudanças de API aparecem como erro de compilação ali, não espalhadas pelo app.
- Se algum recurso dos AC fizer falta (por exemplo, o modo leitura), dá para adotar só aquele componente depois.
