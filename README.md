# CR_mode_server

Бэкенд с админкой и Android-приложение, которое распознаёт карты Clash Royale
прямо на телефоне.

- `server/` — Django + DRF, админка (статическая страница на `/`), база PostgreSQL.
- `android/` — исходники приложения.
- `apk/crmod-arm64-v8a.apk` — готовая сборка для телефона.
- `server/media/seed/` — модель `best_v3.onnx` и русские названия 90 карт, их
  подхватывают сиды.

## Запуск бэка

```bash
cd server
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # вписать свои POSTGRES_* и DJANGO_SECRET_KEY
./start.sh
```

`start.sh` ждёт базу, применяет миграции, собирает статику и прогоняет сиды.
Повторный запуск безопасен: админ не перезаписывается, модель не дублируется.
Отдельно это делает `python manage.py bootstrap`.

На Windows вместо `start.sh` — `.\start.ps1` (поднимает сервер разработки).

Вход в админку: `admin@admin.ru` / `test_test`.

## API

| Метод | Путь | Доступ |
| --- | --- | --- |
| GET | `/api/health/` | открыт |
| POST | `/api/auth/login/` | открыт, нужен для админки |
| GET | `/api/models/?active=1` | открыт, его читает телефон |
| GET | `/api/models/<id>/file/` | открыт, скачивание модели |
| POST / PATCH / DELETE | `/api/models/` | только админ по токену |

CORS открыт для всех источников (`DJANGO_CORS_ALL=1`); можно сузить через
`DJANGO_CORS_ORIGINS`.

## Приложение

Авторизации нет. На главном экране указывается адрес сервера, скачивается и
включается модель, круглая кнопка показывает плашку поверх других приложений.
Плашка «В бой» запускает захват экрана и распознавание, «Бой закончен»
останавливает его.

Телефон запускает только `.onnx`. Новую модель готовит `scripts/export_onnx.py`
из основного проекта: он делает `.onnx` и `.labels.json`, оба файла грузятся
через админку.

Сборка: `cd android && ./gradlew assembleRelease`. Готовые apk лежат в
`android/app/build/outputs/apk/release/`.
