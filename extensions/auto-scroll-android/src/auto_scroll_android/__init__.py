import flet as ft


@ft.control("AndroidAccessibility")
class AndroidAccessibility(ft.Service):
    """Interface to AutoScroll's native Android service."""

    async def get_state(self) -> dict[str, object]:
        """Get permission, service, overlay, and scrolling state."""
        return await self._invoke_method("get_state")

    async def request_permission(self) -> bool:
        """Open Android Accessibility settings."""
        return bool(await self._invoke_method("request_permission"))

    async def show_overlay(self) -> bool:
        """Show the native floating controller."""
        return bool(await self._invoke_method("show_overlay"))

    async def hide_overlay(self) -> bool:
        """Hide the native floating controller."""
        return bool(await self._invoke_method("hide_overlay"))

    async def set_config(self, direction: str, speed: float) -> bool:
        """Update persistent native scrolling settings."""
        return bool(
            await self._invoke_method(
                "set_config",
                {
                    "direction": direction,
                    "speed": speed,
                },
            )
        )
