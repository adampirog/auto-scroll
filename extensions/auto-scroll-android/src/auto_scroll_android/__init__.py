import flet as ft


@ft.control("AndroidAccessibility")
class AndroidAccessibility(ft.Service):
    """Interface to Android's Accessibility Service."""

    async def is_enabled(self) -> bool:
        """Check whether the accessibility service is enabled."""
        return bool(await self._invoke_method("is_enabled"))

    async def request_permission(self) -> bool:
        """Open Android settings to enable accessibility access."""
        return bool(await self._invoke_method("request_permission"))

    async def test_swipe(self, delay_ms: int = 5000) -> bool:
        """Dispatch one upward swipe after a short delay.

        An upward swipe moves a scrollable document downward.
        """
        if not 0 <= delay_ms <= 15000:
            raise ValueError("Delay must be between 0 and 15000 ms")

        return bool(await self._invoke_method("test_swipe", {"delay_ms": delay_ms}))
