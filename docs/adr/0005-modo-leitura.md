# 0005 — Modo leitura por uma extensão embutida

**Status:** aceito · 2026-10

## Contexto

O GeckoView não oferece modo leitura: o `about:reader` do Firefox para computador depende de partes do Firefox que não vêm no GeckoView, e o do Firefox para Android é uma extensão embutida com um script de conteúdo que roda em todas as páginas para saber se cada uma “parece um artigo”: um custo pago em toda página, mesmo por quem nunca lê no modo leitura.

## Decisão

- O modo leitura é uma página de uma extensão embutida no APK (`engine/gecko/src/main/assets/extensions/reader`), instalada com `ensureBuiltIn` e mantida pelo Gecko entre execuções. Não tem script de fundo nem script de conteúdo: nada roda até alguém pedir para ler.
- Entrar no modo leitura é navegar para `reader.html?url=<página>`. A página baixa a página de novo, analisa o HTML num documento inerte (`DOMParser`, sem scripts nem carregamentos), encontra o artigo com o Readability da Mozilla (o mesmo código do Firefox, sem alterações) e mostra o resultado limpo: sem scripts, estilos, formulários, molduras, atributos de evento nem pixels de rastreamento, com links só para endereços web.
- A política de segurança da página só permite os scripts da própria extensão; imagens e o download da página vêm da web.
- O voltar sai do modo leitura (a página original volta do cache de navegação, na mesma posição). A barra mostra o site da página com um livro no lugar do cadeado; tocar no livro também volta.
- Favoritos, compartilhar e “adicionar ao início” usam o endereço da página, não o do modo leitura. O histórico só guarda endereços web, então o modo leitura não aparece nele.
- Nas abas privadas a extensão roda no contexto privado da aba.

## Consequências

- Nenhum custo nas páginas comuns. O modo leitura custa um download a mais da página (geralmente pequeno) cada vez que é aberto.
- Páginas que só mostram o texto depois de rodar scripts não funcionam no modo leitura; a página explica e oferece o original.
- O Readability (Apache 2.0) vai no APK com sua licença; atualizá-lo é copiar um arquivo (`third_party/readability/README.md`).
