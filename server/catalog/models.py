from django.db import models


class DetectorModel(models.Model):
    PENDING = "pending"
    READY = "ready"
    FAILED = "failed"
    STATUSES = [
        (PENDING, "готовится"),
        (READY, "готова"),
        (FAILED, "ошибка"),
    ]

    name = models.CharField("название", max_length=120)
    description = models.TextField("описание", blank=True)
    note = models.TextField("примечание", blank=True)
    source_file = models.FileField(
        "загруженный файл",
        upload_to="sources/",
        blank=True,
        help_text="то, что принёс администратор: .pt или .onnx",
    )
    file = models.FileField(
        "файл для телефона",
        upload_to="models/",
        blank=True,
        help_text=".onnx, его готовит сервер",
    )
    labels = models.TextField("классы", blank=True, help_text="JSON-список имён карт в порядке модели")
    status = models.CharField("состояние", max_length=10, choices=STATUSES, default=PENDING)
    error = models.TextField("ошибка конвертации", blank=True)
    is_active = models.BooleanField("активна", default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-updated_at"]
        verbose_name = "модель"
        verbose_name_plural = "модели"

    def __str__(self):
        return self.name
