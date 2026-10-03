from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .views import DetectorModelViewSet, health, login

router = DefaultRouter()
router.register("models", DetectorModelViewSet, basename="model")

urlpatterns = [
    path("health/", health),
    path("auth/login/", login),
    path("", include(router.urls)),
]
