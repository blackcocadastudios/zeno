#!/usr/bin/env python3
"""Prepares the GitHub release of a version from the phone APK built by CI.

usage: release.py <apk-dir> <out-dir> [--tag vX.Y.Z | --main]

Checks the APK's signature with apksigner, then writes into <out-dir>:
  zeno-<version>-arm64.apk  the APK under the name people download
  notes.md                  how to install and check it, then the version's
                            CHANGELOG section
  release.env               TAG, TITLE, ASSET, PRERELEASE and PUBLISH for the
                            publish step in ci.yml

An APK signed with the repository's public debug key (no ZENO_* secrets) is
published as a pre-release whose notes say so at the top.

A version is published when main has a dated CHANGELOG section for it (--main;
the publish step skips versions already released), or when its tag is pushed
(--tag: it must be v<versionName>, and the section dated). Without either
(every CI run) this is a rehearsal: the same checks and files, PUBLISH=false,
and a notice of what releasing the commit would publish.
"""
import argparse
import glob
import hashlib
import os
import re
import shlex
import shutil
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEBUG_KEYSTORE = os.path.join(ROOT, "app", "debug.keystore")


def escape(message):
    return message.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def fail(message):
    print(f"::error title=Release::{escape(message)}")
    sys.exit(1)


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as fh:
        return fh.read()


def version_name():
    match = re.search(r'versionName\s*=\s*"([^"]+)"', read("app/build.gradle.kts"))
    if not match:
        fail("versionName not found in app/build.gradle.kts")
    return match.group(1)


def changelog(version):
    """The version's CHANGELOG section: (body, dated)."""
    text = read("CHANGELOG.md")
    match = re.search(rf"^## \[{re.escape(version)}\][^\n]*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    if not match:
        fail(f"CHANGELOG.md has no section for {version}")
    heading = match.group(0).splitlines()[0]
    # Link definitions at the end of the file render as nothing; leave them out.
    body = re.sub(r"^\[[^\]]+\]: \S+\s*$", "", match.group(1), flags=re.M).strip()
    return body, re.search(r"\d{4}-\d{2}-\d{2}", heading) is not None


def find_apk(directory):
    apks = glob.glob(os.path.join(directory, "*arm64-v8a*.apk"))
    if len(apks) != 1:
        fail(f"expected one arm64-v8a APK in {directory}, found {len(apks)}")
    return apks[0]


def tool_version(path):
    name = os.path.basename(os.path.dirname(path))
    return [int(part) if part.isdigit() else -1 for part in re.split(r"[.-]", name)]


def apksigner():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    tools = glob.glob(os.path.join(sdk, "build-tools", "*", "apksigner")) if sdk else []
    if not tools:
        fail("apksigner not found (ANDROID_HOME/build-tools): it checks the APK's signature")
    return max(tools, key=tool_version)


def signer(apk):
    """SHA-256 of the one certificate the APK is signed with, after apksigner verified it."""
    result = subprocess.run([apksigner(), "verify", "--verbose", "--print-certs", apk],
                            capture_output=True, text=True)
    if result.returncode != 0:
        fail("apksigner rejected the APK:\n" + (result.stderr or result.stdout)[-3000:])
    # Older apksigner prints "Signer #1 certificate …", newer prints it per scheme ("V2 Signer: certificate …").
    found = re.findall(r"^(.*)certificate SHA-256 digest: ([0-9a-f]{64})$", result.stdout, re.M)
    digests = {digest for prefix, digest in found if "Source Stamp" not in prefix}
    count = re.search(r"^Number of signers: (\d+)$", result.stdout, re.M)
    if len(digests) != 1 or (count and count.group(1) != "1"):
        fail(f"expected one signer with one certificate; apksigner printed:\n{result.stdout[-3000:]}")
    return digests.pop()


def keytool():
    candidates = [shutil.which("keytool")]
    candidates += [os.path.join(v, "bin", "keytool") for k, v in os.environ.items() if k.startswith("JAVA_HOME")]
    for path in candidates:
        if path and os.path.exists(path):
            return path
    fail("keytool not found: it reads the debug key's certificate")


def debug_certificate():
    """SHA-256 of the repository's debug certificate, as apksigner prints it."""
    der = subprocess.run([keytool(), "-exportcert", "-keystore", DEBUG_KEYSTORE, "-alias", "androiddebugkey",
                          "-storepass", "android"], capture_output=True, check=True).stdout
    return hashlib.sha256(der).hexdigest()


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as fh:
        for block in iter(lambda: fh.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def fingerprint(hex_digest):
    return ":".join(hex_digest[i:i + 2] for i in range(0, len(hex_digest), 2)).upper()


def notes(asset, megabytes, apk_digest, certificate, debug, changes):
    lines = []
    if debug:
        lines += [
            "> **Versão de teste.** Este APK está assinado com a chave de testes do repositório, que é pública: "
            "serve para experimentar o Zeno, não para o dia a dia. O Android só atualiza um app com a mesma chave, "
            "então passar depois a uma versão assinada com a chave do projeto pede desinstalar esta "
            "(favoritos, histórico e senhas salvas vão junto).",
            "",
        ]
    lines += [
        "### Instalar",
        f"1. No celular (Android 8 ou mais novo), baixe **{asset}** ({megabytes} MB), em *Assets* logo abaixo, "
        "e abra o arquivo.",
        "2. Na primeira vez, o Android pede para deixar o navegador (ou o app de arquivos) instalar apps: "
        "permita e toque em *Instalar*.",
        "3. Se o Android recusar por ser de um desenvolvedor não verificado (no Brasil, desde 30/09/2026): "
        f"instale pelo computador com `adb install {asset}`, ou ligue *Configurações → Sistema → "
        "Opções do desenvolvedor → Apps de desenvolvedores não verificados*, que pede uma espera única de 24 horas.",
        "",
        "### Conferir o download",
        f"- SHA-256 do APK: `{apk_digest}`",
        f"- Certificado de assinatura (SHA-256): `{fingerprint(certificate)}`",
        "",
        "---",
        "",
        changes,
        "",
    ]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk_dir")
    parser.add_argument("out_dir")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--tag", help="a pushed tag, published as it is")
    mode.add_argument("--main", action="store_true", help="main: publish the version once its section is dated")
    args = parser.parse_args()

    version = version_name()
    tag = f"v{version}"
    changes, dated = changelog(version)
    if args.tag is not None:
        if args.tag != tag:
            fail(f"tag {args.tag} does not match versionName {version} (expected {tag})")
        if not dated:
            fail(f"date the {version} section of CHANGELOG.md before publishing it")
    publish = args.tag is not None or (args.main and dated)

    apk = find_apk(args.apk_dir)
    certificate = signer(apk)
    debug = certificate == debug_certificate()

    os.makedirs(args.out_dir, exist_ok=True)
    asset = f"zeno-{version}-arm64.apk"
    shutil.copyfile(apk, os.path.join(args.out_dir, asset))
    megabytes = round(os.path.getsize(apk) / 1e6)
    apk_digest = sha256(apk)
    with open(os.path.join(args.out_dir, "notes.md"), "w", encoding="utf-8") as fh:
        fh.write(notes(asset, megabytes, apk_digest, certificate, debug, changes))
    title = f"Zeno {version}" + (" (teste)" if debug else "")
    env = {"TAG": tag, "TITLE": title, "ASSET": asset,
           "PRERELEASE": "true" if debug else "false", "PUBLISH": "true" if publish else "false"}
    with open(os.path.join(args.out_dir, "release.env"), "w", encoding="utf-8") as fh:
        fh.writelines(f"{name}={shlex.quote(value)}\n" for name, value in env.items())

    key = "the public debug key, as a pre-release" if debug else f"certificate {fingerprint(certificate)}"
    what = f"Publishing {tag}:" if publish else f"Releasing {tag} would publish"
    undated = "" if dated else f" Nothing is published until the {version} section of CHANGELOG.md has a date."
    message = f"{what} {asset} ({megabytes} MB), signed with {key}. SHA-256 {apk_digest}.{undated}"
    print(f"::notice title=Release::{escape(message)}")


if __name__ == "__main__":
    main()
