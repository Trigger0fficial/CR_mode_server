import json
from pathlib import Path

from django.contrib.auth.models import User
from django.core.files import File
from django.core.management.base import BaseCommand

from catalog.models import DetectorModel

EMAIL = "admin@admin.ru"
PASSWORD = "test_test"
SERVER_DIR = Path(__file__).resolve().parents[3]
SEED_DIR = SERVER_DIR / "media" / "seed"
MODEL_NAME = "ClashBot v3"


class Command(BaseCommand):
    help = "Создаёт админа и стартовую модель. Повторный запуск ничего не портит."

    def handle(self, *args, **options):
        user, created = User.objects.get_or_create(
            username=EMAIL,
            defaults={"email": EMAIL, "is_staff": True, "is_superuser": True},
        )
        user.email = EMAIL
        user.is_staff = True
        user.is_superuser = True
        user.is_active = True
        if created:
            user.set_password(PASSWORD)
        user.save()
        self.stdout.write("админ " + EMAIL + (" создан" if created else " уже есть"))

        if DetectorModel.objects.exists():
            self.stdout.write("модели уже в базе, сид пропущен")
            return

        source = next(iter(sorted(SEED_DIR.glob("*.onnx"))), None)
        if source is None:
            self.stderr.write(f"нет .onnx в {SEED_DIR}, модель не добавлена")
            return

        labels_file = source.with_suffix(".labels.json")
        if not labels_file.is_file():
            labels_file = source.parent / f"{source.stem}.labels.json"
        labels = labels_file.read_text(encoding="utf-8") if labels_file.is_file() else ""
        count = len(json.loads(labels)) if labels else 0

        model = DetectorModel(
            name=MODEL_NAME,
            description=f"YOLOv8n на объединённом датасете, {count} карт. Работает на телефоне.",
            note="Стартовая модель из сидов.",
            labels=labels,
            is_active=True,
        )
        with source.open("rb") as handle:
            model.file.save(source.name, File(handle), save=True)
        self.stdout.write(f"модель добавлена: {model.file.name}, классов {count}")
