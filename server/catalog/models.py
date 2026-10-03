from django.db import models


class DetectorModel(models.Model):
    name = models.CharField("название", max_length=120)
    description = models.TextField("описание", blank=True)
    note = models.TextField("примечание", blank=True)
    file = models.FileField("файл", upload_to="models/", help_text="формат .onnx, его запускает телефон")
    labels = models.TextField("классы", blank=True, help_text="JSON-список имён карт в порядке модели")
    is_active = models.BooleanField("активна", default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-updated_at"]
        verbose_name = "модель"
        verbose_name_plural = "модели"

    def __str__(self):
        return self.name
