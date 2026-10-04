"""Готовит загруженные веса к запуску на телефоне.

Запускается отдельным процессом: torch весит больше гигабайта, и держать его
в памяти веб-сервера незачем. Результат печатается в stdout одной строкой JSON.

    python tools/export_onnx.py <входной файл> <папка для результата>
"""

from __future__ import annotations

import ast
import json
import shutil
import sys
from pathlib import Path

IMGSZ = 640


def names_from_onnx(path: Path) -> list[str]:
    """Ultralytics записывает имена классов в метаданные .onnx."""
    import onnx

    model = onnx.load(str(path), load_external_data=False)
    for item in model.metadata_props:
        if item.key != "names":
            continue
        parsed = ast.literal_eval(item.value)
        if isinstance(parsed, dict):
            return [str(parsed[key]) for key in sorted(parsed, key=int)]
        return [str(name) for name in parsed]
    return []


def from_onnx(source: Path, out_dir: Path) -> dict:
    target = out_dir / source.name
    if source.resolve() != target.resolve():
        shutil.copy2(source, target)
    return {"onnx": str(target), "names": names_from_onnx(target)}


def from_weights(source: Path, out_dir: Path) -> dict:
    from ultralytics import YOLO

    model = YOLO(str(source))
    names = [str(model.names[index]) for index in sorted(model.names)]
    exported = Path(
        model.export(format="onnx", imgsz=IMGSZ, opset=12, simplify=True, dynamic=False, nms=False)
    )
    target = out_dir / f"{source.stem}.onnx"
    shutil.copy2(exported, target)
    return {"onnx": str(target), "names": names}


def main() -> None:
    source = Path(sys.argv[1])
    out_dir = Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    if not source.is_file():
        raise SystemExit(f"файл не найден: {source}")

    suffix = source.suffix.lower()
    if suffix == ".onnx":
        result = from_onnx(source, out_dir)
    elif suffix in {".pt", ".pth"}:
        result = from_weights(source, out_dir)
    else:
        raise SystemExit(f"не умею переводить {suffix}, нужен .pt или .onnx")

    result["classes"] = len(result["names"])
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
