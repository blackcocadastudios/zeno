# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); versões seguem [SemVer](https://semver.org/lang/pt-BR/).

## [0.1.0] — não lançada

Primeira versão utilizável.

### Navegação
- Motor GeckoView 158 (o mesmo do Firefox 158), usado direto, sem Android Components.
- Barra de endereço embaixo, com contador de abas e menu; desce enquanto se lê e volta ao rolar para cima (como no Firefox), e some enquanto se digita numa página.
- Puxar a página para baixo recarrega: um ensō se desenha enquanto se puxa.
- Edição de endereço com sugestões do histórico e dos favoritos, completando o domínio enquanto se digita; sugestões do mecanismo de pesquisa só se ligadas.
- Página inicial própria: ensō, atalhos (fixados e mais visitados) e ondas seigaiha.
- Abas normais e privadas (tema Yoru), grade de abas com miniaturas em memória; fechar com um gesto para o lado, desfazer logo depois.
- Abas em segundo plano suspensas para poupar memória e restauradas ao voltar; sessão salva atomicamente.
- Encontrar na página, tradução da página (modelos locais do Firefox), versão para computador, tela cheia de vídeo com rotação automática.
- Menu de contexto para links, imagens e mídia; downloads com notificação e lista própria.
- Diálogos de página (alerta, confirmação, login, cores, datas, arquivos), permissões de sites e do Android.
- Abre links de outros apps, texto compartilhado e texto selecionado; atalhos no ícone (nova aba, aba privada); pode ser o navegador padrão.

### Privacidade
- Sem telemetria, sem contas. Proteção contra rastreamento padrão com cookies isolados por site, Global Privacy Control, HTTPS-Only nas abas privadas.
- DNS seguro opcional (Cloudflare, Quad9, NextDNS, Mullvad), apagar dados ao sair.
- Outros apps só conseguem abrir páginas web (http/https) no Zeno; links `intent:` de páginas não escolhem componentes nem levam permissões.
- Downloads de abas privadas são listados só em memória e esquecidos quando elas fecham.

### Extensões
- Extensões do addons.mozilla.org, com pedido de permissões, ativar/desativar, abas privadas e remover.
- O uBlock Origin é sugerido uma vez na página inicial; Dark Reader, Violentmonkey e SponsorBlock na lista de recomendadas.

### Desenvolvimento
- Módulos: `:core:engine`, `:core:browser` (Kotlin puro), `:core:design`, `:core:data`, `:engine:gecko`, `:app`.
- CI com build debug e release, testes, capturas de tela (Roborazzi), lint e teste de fumaça num emulador.

### Ainda não
- Modo leitura, gerenciador de senhas, exceções de proteção por site, notificações de sites. A barra fica embaixo, de propósito (uma mão).
