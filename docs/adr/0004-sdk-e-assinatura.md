# 0004 — Níveis de SDK e assinatura

**Status:** aceito · 2026-10

## SDK

- `minSdk 26` (Android 8.0): o mínimo do GeckoView 158.
- `compileSdk 37.2`: exigido pelo GeckoView 158.
- `targetSdk 36`: as mudanças de comportamento do Android 17 serão revisadas antes de subir.

## Assinatura

- Builds de depuração usam `app/debug.keystore`, versionada de propósito para que APKs do CI possam ser atualizados por cima uns dos outros.
- Builds de release usam a chave definida em `keystore.properties` (local) ou nos segredos `ZENO_KEYSTORE_BASE64`, `ZENO_KEYSTORE_PASSWORD`, `ZENO_KEY_ALIAS` e `ZENO_KEY_PASSWORD` (CI). Sem eles, usam a chave de depuração, e o README explica o risco: qualquer pessoa pode assinar um APK com uma chave pública, então para uso diário configure uma chave própria.
