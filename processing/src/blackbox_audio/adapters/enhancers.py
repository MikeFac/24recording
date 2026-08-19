from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

from ..domain import ToolInfo
from .ffmpeg import require_command


class DeepFilterNetEnhancer:
    def __init__(self, executable: str | None = None) -> None:
        self._executable = find_deepfilter(executable)
        version = subprocess.run(
            [self._executable, "--version"],
            capture_output=True,
            text=True,
            check=False,
        )
        self._version = (version.stdout or version.stderr).strip() or "unknown"

    @property
    def info(self) -> ToolInfo:
        return ToolInfo(
            name="deepfilternet",
            version=self._version,
            parameters={
                "compensate_delay": True,
                "attenuation_limit_db": ATTENUATION_LIMIT_DB,
                "post_filter": False,
            },
        )

    def enhance(self, source: Path, destination: Path) -> None:
        with tempfile.TemporaryDirectory(prefix="deepfilter-", dir=destination.parent) as temporary:
            output_directory = Path(temporary)
            completed = subprocess.run(
                [
                    self._executable,
                    "--compensate-delay",
                    "--atten-lim-db",
                    str(ATTENUATION_LIMIT_DB),
                    "--output-dir",
                    str(output_directory),
                    str(source),
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            if completed.returncode != 0:
                detail = completed.stderr.strip() or completed.stdout.strip() or "unknown error"
                raise RuntimeError(f"DeepFilterNet failed: {detail}")
            outputs = list(output_directory.rglob("*.wav"))
            if len(outputs) != 1:
                raise RuntimeError(
                    f"DeepFilterNet produced {len(outputs)} WAV files; expected exactly one"
                )
            shutil.move(str(outputs[0]), destination)


def find_deepfilter(explicit: str | None) -> str:
    configured = explicit or os.environ.get("BLACKBOX_DEEPFILTER")
    if configured:
        return require_command(configured)
    installed = shutil.which("deep-filter")
    if installed:
        return installed
    project_tool = Path(__file__).resolve().parents[3] / ".tools" / "deep-filter"
    if project_tool.is_file() and os.access(project_tool, os.X_OK):
        return str(project_tool)
    raise RuntimeError(
        "DeepFilterNet is not installed; run scripts/install_deepfilter.py "
        "or set BLACKBOX_DEEPFILTER"
    )


class PassthroughEnhancer:
    """Explicit diagnostic adapter; never selected as an automatic fallback."""

    @property
    def info(self) -> ToolInfo:
        return ToolInfo(name="passthrough", version="1", parameters={})

    def enhance(self, source: Path, destination: Path) -> None:
        shutil.copyfile(source, destination)


ATTENUATION_LIMIT_DB = 12
