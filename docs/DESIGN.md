# Linguagem visual do Zeno

Minimalista, azul-claro, com um pé no Japão: papel, tinta e água. O código de tudo isto está em `:core:design`; a galeria de capturas (`DesignGalleryScreenshotTest`) mostra o sistema inteiro nos três temas.

## Ideias que guiam

- **間 Ma** — o espaço vazio tem significado. Margens generosas, poucos elementos por tela.
- **Papel e tinta** — fundo de papel quente (白練 shironeri), texto em tinta (墨 sumi), linhas finas como a estrutura de um shōji.
- **Água** — o azul-claro é o único acento: progresso, foco, seleção. Nunca decoração gratuita.
- **Um traço** — a marca é um ensō (円相), o círculo pintado de uma vez só. "Zen" + "ō".

## Cores

As cores vêm do repertório tradicional japonês (伝統色) e são usadas por **nome semântico** (`Zeno.colors.ink`), nunca por valor.

| Token | Dia 昼 | Noite 墨 | Privado 夜 | Uso |
| --- | --- | --- | --- | --- |
| `paper` | `#FCFAF2` 白練 | `#121416` | `#0F1E2A` | fundo |
| `paperRaised` | `#FFFFFC` | `#1A1E21` | `#152836` | folhas, menus, cartões |
| `paperSunken` | `#E7F1F3` | `#232A2E` | `#1C3344` | campos, chips |
| `ink` | `#22282B` | `#ECE9E2` | `#E8EFF2` | texto e ícones |
| `inkSoft` | `#5A6367` | `#A9B0B4` | `#A3B7C2` | texto secundário |
| `inkFaint` | `#959FA3` | `#6E777C` | `#6A8291` | dicas, desabilitado |
| `line` | `#E3DED2` | `#2C3236` | `#233B4C` | linhas finas |
| `accent` | `#2792C3` 縹 | `#8CC9E3` | `#A5DEE4` 甕覗 | progresso, foco, interruptores |
| `accentInk` | `#165E83` 藍 | `#A8DCF0` | `#BDE7EC` | texto em destaque (contraste AA) |
| `accentContainer` | `#D3ECF2` | `#1E3A47` | `#1F4456` | itens selecionados |
| `seal` | `#C8442E` 朱 | `#E0705A` | `#E07A64` | ações destrutivas, raríssimo |

A navegação privada usa o tema **夜 Yoru** (noite índigo) para nunca ser confundida com a normal.

## Tipografia

| Estilo | Fonte | Tamanho | Uso |
| --- | --- | --- | --- |
| `display` | Shippori Mincho | 34 | marca, títulos grandes |
| `title` | Zen Kaku Gothic New Medium | 21 | títulos de tela |
| `heading` | Zen Kaku Gothic New Medium | 17 | cabeçalhos de seção |
| `body` | Zen Kaku Gothic New | 16 | texto corrido |
| `bodySmall` | Zen Kaku Gothic New | 14 | descrições |
| `label` | Zen Kaku Gothic New Medium | 15 | botões, menus |
| `caption` | Zen Kaku Gothic New | 12 | metadados |
| `url` | Zen Kaku Gothic New | 16 | barra de endereço |
| `mincho` | Shippori Mincho | 17 | frases calmas (estados vazios) |

As duas fontes são OFL, recortadas para latim + alguns kanji decorativos (cerca de 160 KB no total). Licenças em `third_party/fonts/`.

## Espaço, forma e movimento

- Escala de espaço **Ma**: 2, 4, 8, 12, 16, 24, 32, 48, 64 dp. Alvo de toque mínimo de 48 dp.
- Formas: barra de endereço em pílula; cartões com 16 dp; folhas com 24 dp no topo; selos com 12 dp.
- Movimento calmo, como água assentando: curva `(0.2, 0, 0, 1)`, 150/240/360 ms. Nada quica.

## Iconografia

Ícones de linha da coleção Lucide (ISC), redesenhados com traço de **1,5** (o peso de uma caneta-tinteiro) e pontas arredondadas. Gerados por `tools/design/lucide_to_compose.py`.

## Elementos próprios

- **Ensō** — `Enso()`; pode ser "pintado" com `drawProgress`. Gerado por `tools/design/enso.py`, a mesma geometria do ícone do app.
- **Marca** — `ZenoWordmark()`: "Zen" em mincho com o ensō como "o".
- **Selo (印)** — `Seal()`: cada site recebe uma letra em mincho sobre um tom tradicional fixo, como um carimbo hanko. O Zeno não busca favicons: é mais rápido, mais silencioso e não avisa ninguém do que você visitou.
- **Contador de abas** — moldura quadrada com o número; acima de 99 vira ∞.
- **Linha de progresso** — um único traço de 2 dp no acento, que some quando a página assenta.

## Ícone do app

Ensō em 藍 (índigo) sobre ladrilho 水色 (azul-água). Inclui camada monocromática para ícones temáticos do Android 13+.
