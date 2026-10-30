"""Tests for the settings model that backs the Flet controls."""

import pytest

from auto_scroll.settings import ScrollSettings


def test_defaults() -> None:
    settings = ScrollSettings()
    assert settings.speed == 1.0
    assert settings.direction == "down"
    assert settings.description == "1×  ·  Down"


@pytest.mark.parametrize("value", [0.5, 1.0, 2.5, 5.0])
def test_speed_range(value: float) -> None:
    settings = ScrollSettings()
    settings.set_speed(value)
    assert settings.speed == value


@pytest.mark.parametrize("value", [-1, 0.49, 5.01, float("inf"), float("nan")])
def test_invalid_speed(value: float) -> None:
    settings = ScrollSettings()
    with pytest.raises(ValueError):
        settings.set_speed(value)


@pytest.mark.parametrize("value", ["up", "down"])
def test_direction(value: str) -> None:
    settings = ScrollSettings()
    settings.set_direction(value)
    assert settings.direction == value


def test_invalid_direction() -> None:
    with pytest.raises(ValueError):
        ScrollSettings().set_direction("left")
