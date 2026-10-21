# 0003 — Silêncio e privacidade por padrão

**Status:** aceito · 2026-10

## Decisão

- Nenhuma telemetria, conta, sincronização ou notificação promocional.
- Proteção contra rastreamento do Gecko no nível padrão (estrito disponível), HTTPS-first e Global Privacy Control ligados.
- Sem favicons: sites são representados por selos gerados localmente (ver `docs/DESIGN.md`), o que evita requisições extras a cada visita.
- Sugestões de pesquisa desligadas por padrão, porque enviam cada letra digitada ao buscador.
- Bloqueio de anúncios pelo uBlock Origin, instalado com um toque pela tela de extensões, em vez de uma lista própria.
- Backup em nuvem desativado para os dados do navegador.

## Consequências

Algumas comodidades exigem um toque a mais (ativar sugestões, instalar o uBlock Origin). Em troca, o Zeno não faz nenhuma requisição que o usuário não tenha pedido.
