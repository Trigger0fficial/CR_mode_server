#!/usr/bin/env bash
# Запуск бэка на сервере: миграции, статика, сиды, затем gunicorn.
set -euo pipefail

cd "$(dirname "$0")"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

python manage.py bootstrap
exec gunicorn config.wsgi:application \
  --bind "${BIND:-0.0.0.0:8000}" \
  --workers "${WORKERS:-3}" \
  --timeout "${TIMEOUT:-120}"
