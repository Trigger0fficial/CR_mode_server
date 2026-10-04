from django.contrib.auth import authenticate
from django.http import FileResponse
from rest_framework import status, viewsets
from rest_framework.authtoken.models import Token
from rest_framework.decorators import action, api_view, permission_classes
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.permissions import AllowAny, BasePermission
from rest_framework.response import Response

from .convert import convert_later
from .models import DetectorModel
from .serializers import DetectorModelSerializer

TRUE = {"1", "true", "on", "yes"}


class ReadAnyWriteStaff(BasePermission):
    """Телефон читает каталог без входа, менять его может только админ."""

    def has_permission(self, request, view):
        if request.method in {"GET", "HEAD", "OPTIONS"}:
            return True
        return bool(request.user and request.user.is_staff)


@api_view(["GET"])
@permission_classes([AllowAny])
def health(_request):
    return Response({"ok": True})


@api_view(["POST"])
@permission_classes([AllowAny])
def login(request):
    email = (request.data.get("email") or "").strip().lower()
    password = request.data.get("password") or ""
    user = authenticate(request, username=email, password=password)
    if user is None or not user.is_active:
        return Response(
            {"detail": "Неверная почта или пароль"},
            status=status.HTTP_400_BAD_REQUEST,
        )
    token, _ = Token.objects.get_or_create(user=user)
    return Response({"token": token.key, "email": user.username, "is_staff": user.is_staff})


class DetectorModelViewSet(viewsets.ModelViewSet):
    serializer_class = DetectorModelSerializer
    permission_classes = [ReadAnyWriteStaff]
    parser_classes = [JSONParser, MultiPartParser, FormParser]
    http_method_names = ["get", "post", "patch", "delete", "head", "options"]

    def get_queryset(self):
        qs = DetectorModel.objects.all()
        staff = bool(self.request.user and self.request.user.is_staff)
        if not staff:
            # Телефону показываем только то, что уже переведено в .onnx.
            return qs.filter(is_active=True, status=DetectorModel.READY)
        if self.request.query_params.get("active") == "1":
            qs = qs.filter(is_active=True)
        return qs

    def create(self, request, *args, **kwargs):
        name = (request.data.get("name") or "").strip()
        upload = request.FILES.get("file")
        if not name or upload is None:
            return Response(
                {"detail": "Нужны название и файл модели"},
                status=status.HTTP_400_BAD_REQUEST,
            )
        if not upload.name.lower().endswith((".pt", ".pth", ".onnx")):
            return Response(
                {"detail": "Нужен файл .pt или .onnx"},
                status=status.HTTP_400_BAD_REQUEST,
            )
        obj = DetectorModel.objects.create(
            name=name,
            description=(request.data.get("description") or "").strip(),
            note=(request.data.get("note") or "").strip(),
            source_file=upload,
            status=DetectorModel.PENDING,
            is_active=str(request.data.get("is_active", "true")).lower() in TRUE,
        )
        convert_later(obj.id)
        return Response(self.get_serializer(obj).data, status=status.HTTP_201_CREATED)

    def partial_update(self, request, *args, **kwargs):
        obj = self.get_object()
        for field in ("name", "description", "note"):
            if field in request.data:
                setattr(obj, field, (request.data.get(field) or "").strip())
        if "is_active" in request.data:
            obj.is_active = str(request.data.get("is_active")).lower() in TRUE
        again = request.FILES.get("file") is not None
        if again:
            obj.source_file = request.FILES["file"]
            obj.status = DetectorModel.PENDING
            obj.error = ""
        obj.save()
        if again:
            convert_later(obj.id)
        return Response(self.get_serializer(obj).data)

    @action(detail=True, methods=["post"])
    def convert(self, request, pk=None):
        """Повторная попытка, если конвертация упала."""
        obj = self.get_object()
        if not obj.source_file:
            return Response(
                {"detail": "Исходный файл не сохранён, загрузите модель заново"},
                status=status.HTTP_400_BAD_REQUEST,
            )
        DetectorModel.objects.filter(pk=obj.pk).update(status=DetectorModel.PENDING, error="")
        convert_later(obj.pk)
        obj.refresh_from_db()
        return Response(self.get_serializer(obj).data)

    @action(detail=True, methods=["get"], permission_classes=[AllowAny])
    def file(self, request, pk=None):
        obj = self.get_object()
        if not obj.file:
            return Response(
                {"detail": "Модель ещё не готова"},
                status=status.HTTP_409_CONFLICT,
            )
        handle = obj.file.open("rb")
        filename = obj.file.name.rsplit("/", 1)[-1]
        response = FileResponse(handle, as_attachment=True, filename=filename)
        response["Content-Length"] = obj.file.size
        return response
