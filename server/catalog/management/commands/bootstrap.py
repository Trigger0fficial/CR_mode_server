import time

from django.core.management import call_command
from django.core.management.base import BaseCommand
from django.db import OperationalError, connections


class Command(BaseCommand):
    help = "Ждёт базу, применяет миграции, собирает статику и прогоняет сиды"

    def add_arguments(self, parser):
        parser.add_argument("--wait", type=int, default=60, help="сколько секунд ждать базу")

    def handle(self, *args, **options):
        self.wait_for_db(options["wait"])
        call_command("migrate", interactive=False, verbosity=1)
        call_command("collectstatic", interactive=False, verbosity=0)
        self.stdout.write("статика собрана")
        call_command("seed")
        self.stdout.write(self.style.SUCCESS("бэк готов к запуску"))

    def wait_for_db(self, limit):
        deadline = time.monotonic() + limit
        while True:
            try:
                connections["default"].cursor().close()
                return
            except OperationalError as error:
                if time.monotonic() >= deadline:
                    raise
                self.stdout.write(f"база не отвечает ({error.__class__.__name__}), жду…")
                time.sleep(2)
