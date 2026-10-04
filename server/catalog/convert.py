"""Перевод загруженных весов в формат, который запускает телефон."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import threading
from pathlib import Path

from django.core.files import File
from django.db import connections

from .card_names import russian_name

SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "export_onnx.py"
TIMEOUT = 15 * 60


def interpreter() -> str:
    """Python, в котором стоит ultralytics. По умолчанию — текущий."""
    return os.environ.get("CRMOD_PYTHON") or sys.executable


def convert(model) -> None:
    """Делает из `source_file` готовый .onnx и список русских названий карт."""
    source = Path(model.source_file.path)
    with tempfile.TemporaryDirectory(prefix="crmod-convert-") as workdir:
        finished = subprocess.run(
            [interpreter(), str(SCRIPT), str(source), workdir],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=TIMEOUT,
        )
        if finished.returncode != 0:
            raise RuntimeError(short(finished.stderr) or "конвертер завершился с ошибкой")
        line = next((row for row in reversed(finished.stdout.splitlines()) if row.startswith("{")), "")
        if not line:
            raise RuntimeError(short(finished.stderr) or "конвертер ничего не вернул")

        result = json.loads(line)
        onnx = Path(result["onnx"])
        names = [russian_name(name) for name in result["names"]]
        if not names:
            raise RuntimeError("в модели нет списка классов, телефон не покажет названия карт")

        with onnx.open("rb") as handle:
            model.file.save(onnx.name, File(handle), save=False)
        model.labels = json.dumps(names, ensure_ascii=False)
        model.status = model.READY
        model.error = ""
        model.save()


def short(text: str, limit: int = 600) -> str:
    """Из простыни логов конвертера оставляем последние строки."""
    rows = [row.strip() for row in (text or "").splitlines() if row.strip()]
    tail = " / ".join(rows[-3:])
    return tail[:limit]


def convert_later(model_id: int) -> None:
    """Конвертация идёт в фоне: загрузка через админку не должна ждать минуты."""
    threading.Thread(target=run, args=(model_id,), daemon=True).start()


def run(model_id: int) -> None:
    from .models import DetectorModel

    try:
        model = DetectorModel.objects.get(pk=model_id)
    except DetectorModel.DoesNotExist:
        return
    try:
        convert(model)
    except Exception as error:  # noqa: BLE001 — текст ошибки нужен администратору
        DetectorModel.objects.filter(pk=model_id).update(
            status=DetectorModel.FAILED,
            error=str(error)[:600] or error.__class__.__name__,
        )
    finally:
        connections.close_all()
