# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); versões seguem [SemVer](https://semver.org/lang/pt-BR/).

## [0.1.0] — 2026-10-25

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
- Modo leitura (menu → Modo leitura): só o texto do artigo, com a tipografia e as cores do Zeno, pelo Readability da Mozilla. Não custa nada às páginas comuns; voltar, ou tocar no livro da barra, retorna à página.
- Menu de contexto para links, imagens e mídia; downloads com notificação e lista própria.
- Diálogos de página (alerta, confirmação, login, cores, datas, arquivos), permissões de sites e do Android.
- Painel do site no cadeado da barra: conexão e certificado, proteção contra rastreamento ligada ou desligada só para aquele site, permissões guardadas e “Apagar cookies e dados do site”.
- Abre links de outros apps, texto compartilhado e texto selecionado; atalhos no ícone (nova aba, aba privada); pode ser o navegador padrão.

### Privacidade
- Sem telemetria, sem contas. Proteção contra rastreamento padrão com cookies isolados por site, Global Privacy Control, HTTPS-Only nas abas privadas.
- DNS seguro opcional (Cloudflare, Quad9, NextDNS, Mullvad), apagar dados ao sair.
- Outros apps só conseguem abrir páginas web (http/https) no Zeno; links `intent:` de páginas não escolhem componentes nem levam permissões.
- Downloads de abas privadas são listados só em memória e esquecidos quando elas fecham.
- Permissões de sites lembradas: ao permitir ou bloquear, “Lembrar para este site” (marcado por padrão) guarda a escolha. Sem marcar, a permissão vale só enquanto você está no site e é desfeita ao sair dele ou fechar a aba, mesmo se o Zeno for fechado antes. Câmera e microfone são perguntados toda vez; nas abas privadas nada é lembrado.
- Configurações → Permissões dos sites mostra o que cada site pode usar e onde a proteção está desligada, e permite mudar ou esquecer cada escolha.
- Localização para sites no Android 12 e mais novos: o Zeno pede a localização aproximada junto com a precisa, como o sistema exige.
- Notificações de sites, desligadas por padrão. Ligadas em Configurações → Sites, um site pode notificar se você permitir, enquanto está aberto (sem serviço de push); tocar na notificação traz a aba do site. Com elas desligadas, um site que pede recebe uma recusa silenciosa e o Zeno avisa onde ligar.

### Senhas
- Depois de entrar num site o Zeno oferece salvar o login (ou atualizar a senha), com “Nunca neste site”; num campo de login, os logins salvos aparecem numa barra sobre o teclado, sem cobrir a página; o login salvo é preenchido quando a página abre.
- Usuários e senhas ficam cifrados (AES-256-GCM) com uma chave que não sai do keystore do Android; nada vai para a nuvem.
- Configurações → Senhas pede o bloqueio de tela para abrir, fica fora de capturas de tela e fecha ao sair do app; permite mostrar, copiar, editar e apagar logins, e voltar a oferecer onde foi dito “nunca”.

### Extensões
- Extensões do addons.mozilla.org, com pedido de permissões, ativar/desativar, abas privadas e remover.
- O uBlock Origin é sugerido uma vez na página inicial; Dark Reader, Violentmonkey e SponsorBlock na lista de recomendadas.

### Desenvolvimento
- Módulos: `:core:engine`, `:core:browser` (Kotlin puro), `:core:design`, `:core:data`, `:engine:gecko`, `:app`.
- CI com build debug e release, testes, capturas de tela (Roborazzi), lint e teste de fumaça num emulador, que usa páginas locais (localização, notificações, modo leitura, login) e publica o resultado como anotações da execução.
- Uma versão com data neste arquivo é publicada em Releases quando chega ao `main` (ou pela tag `vX.Y.Z`), se o build e o teste de fumaça passarem naquele commit; assinada com a chave de testes, sai como versão de teste.

### Ainda não
- Notificações de sites com a página fechada (push). Exportar e importar senhas. A barra fica embaixo, de propósito (uma mão).

[0.1.0]: https://github.com/blackcocadastudios/zeno/releases/tag/v0.1.0
