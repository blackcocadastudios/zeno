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

- O APK de celular vai para Releases (job `release` do `ci.yml`) quando o `main` tem, no CHANGELOG, a data de uma versão ainda sem release, ou quando a tag `vX.Y.Z` é enviada; sempre depois de o build, os testes, o lint e o teste de fumaça passarem naquele commit. Publicar é, então, juntar ao `main` uma versão com data. `tools/ci/release.py` confere a `versionName` (e a tag, se houver), a data no CHANGELOG e a assinatura do APK, e monta as notas a partir do CHANGELOG.
- Assinado com a chave de depuração, o release sai como pré-lançamento (“teste”) e avisa no topo das notas. A cada push o CI ensaia a publicação com as mesmas conferências, sem publicar, para a tag não ser o primeiro teste.
- No Brasil, desde 30/09/2026, o Android pode recusar apps de desenvolvedores não verificados; um APK não registrado se instala pelo ADB ou pelo fluxo avançado do Android (espera única de 24 horas). Registrar o Zeno numa conta de distribuição limitada do Android Developer Console (gratuita, até 20 aparelhos) tira esse passo, mas pede uma chave própria: registrar a de depuração, que é pública, deixaria qualquer um assinar em nome do Zeno.
