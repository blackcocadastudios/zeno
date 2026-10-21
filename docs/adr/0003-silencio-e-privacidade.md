# 0003 — Silêncio e privacidade por padrão

**Status:** aceito · 2026-10

## Decisão

- Nenhuma telemetria, conta, sincronização ou notificação promocional.
- Proteção contra rastreamento do Gecko no nível padrão (estrito disponível), HTTPS-first e Global Privacy Control ligados.
- Sem favicons: sites são representados por selos gerados localmente (ver `docs/DESIGN.md`), o que evita requisições extras a cada visita.
- Sugestões de pesquisa desligadas por padrão, porque enviam cada letra digitada ao buscador.
- Bloqueio de anúncios pelo uBlock Origin, sugerido uma vez na página inicial e instalável pela tela de extensões, em vez de uma lista própria.
- Backup em nuvem desativado para os dados do navegador.
- Abas privadas não deixam rastro em disco: nada de histórico, sessão ou miniaturas; os downloads feitos nelas são salvos (é o que se pediu), mas a lista que os mostra fica só na memória e esquece tudo quando as abas privadas fecham.
- Outros apps só abrem páginas `http`/`https` no Zeno; links `intent:` vindos de páginas nunca escolhem componentes nem levam permissões do Zeno.

## Consequências

Algumas comodidades exigem um toque a mais (ativar sugestões, instalar o uBlock Origin). Em troca, o Zeno não faz nenhuma requisição que o usuário não tenha pedido.
