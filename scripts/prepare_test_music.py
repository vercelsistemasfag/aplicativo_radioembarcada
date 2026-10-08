"""Injeta somente MP3 de um arquivo opcional, sem registrar a URL de download."""

import json
import os
import re
from pathlib import Path, PurePosixPath
import shutil
import stat
import subprocess
import tempfile
from urllib.parse import urlsplit
import zipfile


class MusicArchiveError(Exception):
    """Erro público sanitizado; nunca contém URL ou saída de ferramentas externas."""


def archive_url():
    url = os.environ.get("MUSIC_ARCHIVE_URL", "").strip()
    if not url and os.environ.get("MUSIC_ARCHIVE_VARIABLE_PRESENT") == "true":
        # Não interpolar vars.MUSIC_ARCHIVE_URL no YAML: o runner imprimiria a URL em env.
        result = subprocess.run(
            ["gh", "api", f"repos/{os.environ['GITHUB_REPOSITORY']}/actions/variables/MUSIC_ARCHIVE_URL"],
            capture_output=True, text=True, timeout=30,
        )
        if result.returncode:
            raise MusicArchiveError("Não foi possível ler a variável MUSIC_ARCHIVE_URL. Configure-a como Actions secret.")
        url = json.loads(result.stdout).get("value", "").strip()
        if url and os.environ.get("GITHUB_ACTIONS") == "true":
            masked = url.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
            print(f"::add-mask::{masked}", flush=True)
    return url


def download_archive(url, output):
    parts = urlsplit(url)
    if parts.scheme != "https" or not parts.netloc or any(ord(c) < 32 for c in url):
        raise MusicArchiveError("MUSIC_ARCHIVE_URL deve apontar para um download HTTPS válido.")
    # stdin evita expor a URL no comando; stderr suprimido evita URLs em erros/redirects.
    escaped = url.replace("\\", "\\\\").replace('"', '\\"')
    result = subprocess.run(
        ["curl", "--disable", "--fail", "--silent", "--location", "--proto", "=https",
         "--proto-redir", "=https", "--retry", "3", "--connect-timeout", "20",
         "--max-time", "300", "--output", str(output), "--config", "-"],
        input=f'url = "{escaped}"\n', text=True, capture_output=True, timeout=360,
    )
    if result.returncode:
        raise MusicArchiveError("Falha no download da biblioteca opcional. Verifique acesso/validade do link configurado.")


def safe_path(name):
    path = PurePosixPath(name.replace("\\", "/"))
    if path.is_absolute() or ".." in path.parts or re.match(r"^[A-Za-z]:", name):
        raise MusicArchiveError("O arquivo musical contém um caminho inválido.")
    return Path(*path.parts)


def extract_archive(archive, destination):
    with archive.open("rb") as source:
        signature = source.read(8)
    if zipfile.is_zipfile(archive):
        with zipfile.ZipFile(archive) as source:
            for entry in source.infolist():
                path = safe_path(entry.filename)
                if stat.S_ISLNK(entry.external_attr >> 16):
                    raise MusicArchiveError("Links simbólicos não são aceitos na biblioteca.")
                if entry.is_dir() or path.suffix.lower() != ".mp3":
                    continue
                output = destination / path
                output.parent.mkdir(parents=True, exist_ok=True)
                with source.open(entry) as data, output.open("wb") as target:
                    shutil.copyfileobj(data, target)
    elif signature.startswith(b"Rar!\x1a\x07"):
        # Validar nomes antes de extrair; unrar suporta RAR4/RAR5 e não solicita senha.
        listing = subprocess.run(["unrar", "lb", "-p-", str(archive)],
                                 capture_output=True, text=True, timeout=60)
        if listing.returncode:
            raise MusicArchiveError("Não foi possível ler o RAR. Use RAR/ZIP sem senha.")
        for name in listing.stdout.splitlines():
            safe_path(name)
        result = subprocess.run(["unrar", "x", "-o+", "-p-", "-idq", str(archive), str(destination) + "/"],
                                capture_output=True, timeout=300)
        if result.returncode:
            raise MusicArchiveError("Não foi possível extrair o RAR. Verifique integridade e ausência de senha.")
    else:
        raise MusicArchiveError("Formato não suportado. Configure um arquivo RAR ou ZIP.")


def prepare_music(url, destination):
    if not url:
        print("MUSIC_ARCHIVE_URL ausente: biblioteca opcional não incluída; o APK será gerado normalmente.")
        return 0
    with tempfile.TemporaryDirectory(prefix="radio-music-") as folder:
        temporary = Path(folder)
        archive = temporary / "library.archive"
        extracted = temporary / "extracted"
        extracted.mkdir()
        download_archive(url, archive)
        extract_archive(archive, extracted)
        tracks = []
        for root, directories, files in os.walk(extracted, followlinks=False):
            if any((Path(root) / name).is_symlink() for name in directories + files):
                raise MusicArchiveError("Links simbólicos não são aceitos na biblioteca.")
            tracks.extend(Path(root) / name for name in files if Path(name).suffix.lower() == ".mp3")
        if not tracks:
            raise MusicArchiveError("O arquivo configurado não contém MP3.")
        for track in tracks:
            target = destination / track.relative_to(extracted)
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists():
                shutil.copy2(track, target)
        print(f"Biblioteca opcional incluída: {len(tracks)} MP3; subpastas preservadas.")
        return len(tracks)


def main():
    try:
        prepare_music(archive_url(), Path("app/src/main/assets/music"))
    except MusicArchiveError as error:
        print(f"::error::{error}")
        return 1
    except (OSError, ValueError, subprocess.SubprocessError, zipfile.BadZipFile, RuntimeError):
        print("::error::Falha ao preparar a biblioteca opcional. Verifique arquivo, ferramentas e configuração.")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
