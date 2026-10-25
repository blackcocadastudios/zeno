# 0004 — Níveis de SDK e assinatura

**Status:** aceito · 2026-10

## SDK

- `minSdk 26` (Android 8.0): o mínimo do GeckoView 158.
- `compileSdk 37.2`: exigido pelo GeckoView 158.
- `targetSdk 36`: as mudanças de comportamento do Android 17 serão revisadas antes de subir.

## Assinatura

- Builds de depuração usam `app/debug.keystore`, versionada de propósito para que APKs do CI possam ser atualizados por cima uns dos outros.
- Builds de release usam a chave definida em `keystore.properties` (local) ou nos segredos `ZENO_KEYSTORE_BASE64`, `ZENO_KEYSTORE_PASSWORD`, `ZENO_KEY_ALIAS` e `ZENO_KEY_PASSWORD` (CI). Sem eles, usam a chave de depuração, e o README explica o risco: qualquer pessoa pode assinar um APK com uma chave pública, então para uso diário configure uma chave própria.

## Publicação

- Uma tag `vX.Y.Z` publica o APK de celular em Releases (job `release` do `ci.yml`), só depois de o build, os testes, o lint e o teste de fumaça passarem naquele commit. `tools/ci/release.py` confere que a tag é a `versionName`, que a seção do CHANGELOG tem data e que a assinatura do APK é válida, e monta as notas a partir do CHANGELOG.
- Assinado com a chave de depuração, o release sai como pré-lançamento (“teste”) e avisa no topo das notas. A cada push o CI ensaia a publicação com as mesmas conferências, sem publicar, para a tag não ser o primeiro teste.
- No Brasil, desde 30/09/2026, o Android pode recusar apps de desenvolvedores não verificados; um APK não registrado se instala pelo ADB ou pelo fluxo avançado do Android (espera única de 24 horas). Registrar o Zeno numa conta de distribuição limitada do Android Developer Console (gratuita, até 20 aparelhos) tira esse passo, mas pede uma chave própria: registrar a de depuração, que é pública, deixaria qualquer um assinar em nome do Zeno.
