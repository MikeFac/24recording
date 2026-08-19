from __future__ import annotations

import hashlib
import os
import platform
import tempfile
import urllib.request
from pathlib import Path

VERSION = "0.5.6"
ASSETS = {
    ("Linux", "x86_64"): (
        "deep-filter-0.5.6-x86_64-unknown-linux-musl",
        "70775e251eee44c0f2451a1e833326cf8bcbbe304d3e7cd12851e6fce72ef7da",
    ),
}


def main() -> int:
    key = (platform.system(), platform.machine())
    if key not in ASSETS:
        supported = ", ".join(f"{system}/{machine}" for system, machine in ASSETS)
        raise SystemExit(f"Unsupported platform {key[0]}/{key[1]}; supported: {supported}")

    asset, expected_sha256 = ASSETS[key]
    url = f"https://github.com/Rikorose/DeepFilterNet/releases/download/v{VERSION}/{asset}"
    tools_directory = Path(__file__).resolve().parents[1] / ".tools"
    tools_directory.mkdir(parents=True, exist_ok=True)
    destination = tools_directory / "deep-filter"

    if destination.is_file() and sha256_file(destination) == expected_sha256:
        destination.chmod(0o755)
        print(f"DeepFilterNet {VERSION} is already installed at {destination}")
        return 0

    file_descriptor, temporary_name = tempfile.mkstemp(
        prefix="deep-filter-", suffix=".download", dir=tools_directory
    )
    os.close(file_descriptor)
    temporary = Path(temporary_name)
    try:
        print(f"Downloading {url}")
        urllib.request.urlretrieve(url, temporary)
        actual_sha256 = sha256_file(temporary)
        if actual_sha256 != expected_sha256:
            raise RuntimeError(
                f"DeepFilterNet checksum mismatch: expected {expected_sha256}, got {actual_sha256}"
            )
        temporary.chmod(0o755)
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)

    print(f"Installed DeepFilterNet {VERSION} at {destination}")
    return 0


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


if __name__ == "__main__":
    raise SystemExit(main())
