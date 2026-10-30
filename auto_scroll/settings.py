from dataclasses import dataclass
from typing import Literal


Direction = Literal["up", "down"]


@dataclass
class ScrollSettings:
    speed: float = 1.0
    direction: Direction = "down"

    def set_speed(self, speed: float) -> None:
        """Set a relative speed multiplier within the intended UI range."""

        if not 0.5 <= speed <= 5.0:
            raise ValueError("Speed must be between 0.5x and 5.0x")

        self.speed = float(speed)

    def set_direction(self, direction: str) -> None:
        """Set the reading direction from a UI event."""

        if direction not in ("up", "down"):
            raise ValueError(f"Unknown scroll direction: {direction!r}")

        self.direction = direction

    @property
    def description(self) -> str:
        return f"{self.speed:g}×  ·  {self.direction.capitalize()}"
