import flet as ft


@ft.control("AndroidAccessibility")
class AndroidAccessibility(ft.Service):
    """Android accessibility and floating-overlay integration."""

    async def is_enabled(self) -> bool:
        """Whether the native accessibility service is connected."""
        return bool(await self._invoke_method("is_enabled"))

    async def request_permission(self) -> bool:
        """Open Android accessibility settings if access is unavailable."""
        return bool(await self._invoke_method("request_permission"))

    async def has_overlay_permission(self) -> bool:
        """Whether Android permits drawing over other apps."""
        return bool(await self._invoke_method("overlay_permission_granted"))

    async def request_overlay_permission(self) -> bool:
        """Open Android overlay permission settings if needed."""
        return bool(await self._invoke_method("request_overlay_permission"))

    async def show_overlay(self) -> bool:
        """Show the floating controller."""
        return bool(await self._invoke_method("show_overlay"))

    async def hide_overlay(self) -> bool:
        """Hide the floating controller."""
        return bool(await self._invoke_method("hide_overlay"))

    async def is_overlay_visible(self) -> bool:
        """Whether the floating controller is visible."""
        return bool(await self._invoke_method("is_overlay_visible"))
