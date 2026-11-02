import flet as ft


@ft.control("AndroidAccessibility")
class AndroidAccessibility(ft.Service):
    """Android accessibility and floating-overlay integration."""

    async def is_enabled(self) -> bool:
        return bool(await self._invoke_method("is_enabled"))

    async def request_permission(self) -> bool:
        return bool(await self._invoke_method("request_permission"))

    async def test_swipe(self, delay_ms: int = 5000) -> bool:
        if not 0 <= delay_ms <= 15000:
            raise ValueError("Delay must be between 0 and 15000 ms")

        return bool(
            await self._invoke_method(
                "test_swipe",
                {"delay_ms": delay_ms},
            )
        )

    async def has_overlay_permission(self) -> bool:
        """Whether Android permits drawing over other apps."""
        return bool(await self._invoke_method("overlay_permission_granted"))

    async def request_overlay_permission(self) -> bool:
        """Open Android's overlay permission settings if needed."""
        return bool(await self._invoke_method("request_overlay_permission"))

    async def show_overlay(self) -> bool:
        """Show the floating controls."""
        return bool(await self._invoke_method("show_overlay"))

    async def hide_overlay(self) -> bool:
        """Remove the floating controls."""
        return bool(await self._invoke_method("hide_overlay"))

    async def is_overlay_visible(self) -> bool:
        """Whether our native overlay window currently exists."""
        return bool(await self._invoke_method("is_overlay_visible"))
