import json

from rest_framework import serializers

from .models import DetectorModel


class DetectorModelSerializer(serializers.ModelSerializer):
    size = serializers.SerializerMethodField()
    filename = serializers.SerializerMethodField()
    source_filename = serializers.SerializerMethodField()
    labels = serializers.SerializerMethodField()

    class Meta:
        model = DetectorModel
        fields = [
            "id",
            "name",
            "description",
            "note",
            "is_active",
            "status",
            "error",
            "size",
            "filename",
            "source_filename",
            "labels",
            "created_at",
            "updated_at",
        ]
        read_only_fields = [
            "id",
            "status",
            "error",
            "size",
            "filename",
            "source_filename",
            "labels",
            "created_at",
            "updated_at",
        ]

    def get_size(self, obj):
        try:
            return obj.file.size
        except (OSError, ValueError):
            return 0

    def get_filename(self, obj):
        return (obj.file.name or "").rsplit("/", 1)[-1]

    def get_source_filename(self, obj):
        return (obj.source_file.name or "").rsplit("/", 1)[-1]

    def get_labels(self, obj):
        raw = (obj.labels or "").strip()
        if not raw:
            return []
        try:
            parsed = json.loads(raw)
        except ValueError:
            return [line.strip() for line in raw.splitlines() if line.strip()]
        if isinstance(parsed, dict):
            return [parsed[key] for key in sorted(parsed, key=lambda k: int(k))]
        return list(parsed) if isinstance(parsed, list) else []
