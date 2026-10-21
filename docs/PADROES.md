# Padrões do Zeno

Regras que toda mudança precisa cumprir. Se uma regra atrapalhar de verdade, mude a regra aqui (com o motivo) em vez de abrir exceção no código.

## Princípios

1. **Silêncio.** O navegador não fala sozinho: sem notificações promocionais, sem notícias, sem telemetria, sem contas.
2. **Leveza vem do que se corta.** O motor é o Gecko completo; a leveza vem de não carregar o que não é usado.
3. **Uma mão.** As ações frequentes ficam na metade de baixo da tela e a no máximo dois toques.
4. **間 (ma).** Espaço vazio é parte do desenho. Na dúvida, tire.

## Orçamentos de desempenho

| Métrica | Orçamento | Como medir |
| --- | --- | --- |
| Abertura a frio até a primeira tela útil | < 500 ms (aparelho médio) | `adb shell am start -W`, campo `TotalTime` |
| RAM com 1 aba ociosa | ≤ Firefox no mesmo aparelho | `adb shell dumpsys meminfo` |
| Regressão de RAM ou abertura | bloqueia o merge acima de 5% | comparação com a versão anterior |
| APK arm64 (release) | registrado a cada build no relatório de CI | `tools/ci/collect_report.py` |
| Quadros da interface | 60 fps ao rolar listas e trocar de aba | Perfetto / GPU rendering |

Abas em segundo plano são suspensas depois de um tempo sem uso (o estado é salvo e restaurado ao voltar).

## Arquitetura

- Camadas finas: **interface (Compose) → estado (`BrowserController`) → motor (`Engine`)**. Só o módulo `:engine:gecko` conhece o GeckoView.
- `:core:engine` e `:core:browser` são Kotlin puro (sem Android) e testados na JVM com um motor falso.
- Estado imutável em um único `StateFlow`; a interface só lê estado e envia ações.
- Injeção de dependência manual (`AppContainer`). Nada de reflexão em tempo de execução.
- Cada funcionalidade nova entra isolada o bastante para ser removida sem refatorar o resto.

## Dependências

Toda dependência custa tamanho e superfície de ataque, então cada uma precisa de justificativa escrita aqui:

| Dependência | Por quê |
| --- | --- |
| GeckoView | o motor; extensões reais, proteção contra rastreamento, Firefox de verdade |
| Jetpack Compose + Material 3 | interface declarativa; Material só como base, sempre com os tokens do Zeno |
| AndroidX Activity, Annotation Experimental, Core, Lifecycle, SplashScreen | integração básica com o Android (Annotation Experimental: aceitar as APIs experimentais do GeckoView com `@OptIn`) |
| Room (+ KSP) | histórico, favoritos e downloads com SQL verificado na compilação |
| DataStore | preferências com escrita atômica e assíncrona |
| kotlinx.coroutines | concorrência estruturada |
| kotlinx.serialization | gravar e restaurar a sessão de abas |
| ProfileInstaller | aplica os perfis de compilação do Compose em instalações fora da Play Store |
| Readability (Mozilla, copiado) | o modo leitura do Firefox, rodando numa página de extensão embutida (ver ADR 0005) |
| Testes: JUnit, Robolectric, Roborazzi, Turbine | testes na JVM e capturas de tela da interface |

Sem bibliotecas de rede (o GeckoView já faz requisições), sem bibliotecas de imagem, sem navegação de terceiros, sem DI por reflexão. Versões ficam travadas no catálogo `gradle/libs.versions.toml`; o GeckoView é atualizado com prioridade, porque motor desatualizado é a maior brecha de um navegador. As demais sobem de propósito, uma de cada vez e com o CI verde; por isso o aviso do lint “há versão mais nova” fica desligado (ele muda com o calendário, não com o código).

## Segurança e privacidade

- Sem telemetria, sem contas, sem rede além da navegação (e da instalação de extensões pedida pelo usuário).
- HTTPS-first, proteção contra rastreamento ligada por padrão, Global Privacy Control ligado.
- Permissões mínimas no manifesto; cada permissão nova precisa de motivo na tabela abaixo.
- Dados de navegação nunca vão para backup na nuvem (`allowBackup=false` e regras de extração vazias).
- Senhas salvas: usuário e senha cifrados com AES-256-GCM e chave no keystore do Android; mostrar ou copiar pede o bloqueio de tela (ADR 0006).
- Abas privadas não gravam histórico, sessão nem miniaturas em disco.
- Intents e esquemas externos são tratados como entrada não confiável: outros apps só abrem páginas `http`/`https`; texto compartilhado vira pesquisa, nunca `file:`, `about:`, `data:` ou `javascript:`.

### Permissões

| Permissão | Por quê | Quando é pedida |
| --- | --- | --- |
| `INTERNET` | navegar | nunca (concedida na instalação) |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | sites de mapas e entregas | só quando um site pede e você permite no Zeno (as duas juntas: o Android 12+ exige; o Gecko precisa da precisa) |
| `CAMERA`, `RECORD_AUDIO` | videochamadas e leitura de códigos na web | idem |
| `POST_NOTIFICATIONS` | progresso e fim de downloads; avisos dos sites que você permitiu | no primeiro download, ou ao ligar as notificações de sites (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | um download continua com a tela apagada | nunca (concedida na instalação) |
| `USE_BIOMETRIC` | confirmar que é você (digital, rosto ou bloqueio de tela) antes de mostrar senhas salvas | nunca (concedida na instalação) |

## Interface

- Layout de referência: Firefox para Android, barra de endereço embaixo.
- Toda cor, espaço, forma e tipo vem dos tokens de `:core:design` (ver `docs/DESIGN.md`). Valor solto no código é bug.
- Acessibilidade desde o primeiro dia: alvos de toque ≥ 48dp, `contentDescription` em ícones, contraste AA para texto, layout que suporta fonte grande.
- Strings em `res/values/strings.xml` (português) — nada de texto fixo na interface.

## Qualidade

- Testes unitários para a lógica (`:core:browser`, `:core:data`), capturas de tela (Roborazzi) para componentes e telas.
- Lint sem erros. Avisos novos precisam de justificativa.
- O CI roda build debug e release, testes, capturas e lint a cada push, e um teste de fumaça num emulador (`tools/ci/smoke.sh`) que usa o app de verdade: abre páginas, pesquisa, troca de aba, instala o uBlock Origin, mede abertura e memória.
- Revisão de cada etapa com a instrução de **tentar quebrar** o código, não de aprovar.

## Processo

- Decisões relevantes viram um ADR curto em `docs/adr/`.
- Versão semântica e `CHANGELOG.md`, mesmo sendo um projeto pessoal.
- Commits pequenos e com mensagem que explica o porquê.
