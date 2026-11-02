from collections.abc import Awaitable, Callable

import flet as ft
from auto_scroll_android import AndroidAccessibility

from auto_scroll.settings import Direction, ScrollSettings
from auto_scroll.theme import CANVAS, CARD, INK, MUTED, PALE, TEAL


class AutoScrollApp:
    def __init__(self, *, page: ft.Page, settings: ScrollSettings | None = None) -> None:
        self.page = page
        self.settings = settings if settings is not None else ScrollSettings()

    def mount(self) -> None:
        self.page.title = "Auto Scroll"
        self.page.bgcolor = CANVAS
        self.page.theme_mode = ft.ThemeMode.LIGHT
        self.page.padding = 0
        self.page.theme = ft.Theme(color_scheme_seed=TEAL)

        accessibility = AndroidAccessibility()

        permissions_card, check_access = self._permissions_card(accessibility)

        content = self._column(
            self._header(),
            self._overlay_card(accessibility),
            permissions_card,
            self._controls_card(),
            spacing=18,
            expand=True,
            scroll=ft.ScrollMode.AUTO,
        )

        async def on_lifecycle(
            event: ft.AppLifecycleStateChangeEvent,
        ) -> None:
            if event.state == ft.AppLifecycleState.RESUME:
                await check_access()

        self.page.on_app_lifecycle_state_change = on_lifecycle

        self.page.add(
            ft.SafeArea(
                expand=True,
                content=ft.Container(expand=True, padding=20, content=content),
            )
        )

        self.page.run_task(check_access)

    def _header(self) -> ft.Row:
        return self._row(
            ft.Container(
                width=50,
                height=50,
                bgcolor=TEAL,
                border_radius=15,
                alignment=ft.Alignment.CENTER,
                content=ft.Icon(ft.Icons.KEYBOARD_DOUBLE_ARROW_DOWN, color=CARD, size=28),
            ),
            self._column(
                ft.Text("Auto Scroll", color=INK, size=24, weight=ft.FontWeight.BOLD),
                ft.Text("Hands-free scrolling for long content", color=MUTED, size=12),
                spacing=2,
                expand=True,
            ),
            spacing=14,
        )

    def _overlay_card(self, accessibility: AndroidAccessibility) -> ft.Container:
        status = ft.Text("Overlay hidden", color=MUTED, size=12)

        overlay_switch = ft.Switch(value=False)

        async def refresh() -> None:
            try:
                visible = await accessibility.is_overlay_visible()
                overlay_switch.value = visible
                status.value = "Overlay active" if visible else "Overlay hidden"
            except Exception as exc:
                overlay_switch.value = False
                status.value = f"Overlay error: {exc}"

            overlay_switch.update()
            status.update()

        async def toggle(event: ft.Event[ft.Switch]) -> None:
            requested = bool(event.control.value)
            overlay_switch.disabled = True
            overlay_switch.update()

            message: str | None = None

            try:
                if requested:
                    if not await accessibility.is_enabled():
                        message = "Enable Accessibility first"
                    elif not await accessibility.has_overlay_permission():
                        await accessibility.request_overlay_permission()
                        message = "Allow display over other apps in Settings"
                    elif not await accessibility.show_overlay():
                        message = "Unable to show overlay"
                else:
                    await accessibility.hide_overlay()

            except Exception as exc:
                message = f"Overlay error: {exc}"

            finally:
                overlay_switch.disabled = False
                await refresh()

                if message is not None:
                    status.value = message
                    status.update()

        overlay_switch.on_change = toggle

        return self._card(
            self._row(
                self._column(
                    ft.Text("Floating controls", color=INK, size=17, weight=ft.FontWeight.W_600),
                    ft.Text("Show controls over other apps", color=MUTED, size=12),
                    spacing=4,
                    expand=True,
                ),
                overlay_switch,
                alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
            ),
            status,
            ft.Button(content="Refresh status", icon=ft.Icons.REFRESH, on_click=refresh),
        )

    def _permissions_card(
        self, accessibility: AndroidAccessibility
    ) -> tuple[ft.Container, Callable[[], Awaitable[None]]]:
        status = ft.Text("Checking accessibility...", color=MUTED, size=13)

        async def check_access() -> None:
            try:
                enabled = await accessibility.is_enabled()

                status.value = "Accessibility enabled" if enabled else "Accessibility disabled"
                status.color = TEAL if enabled else MUTED

            except Exception as exc:
                status.value = f"Accessibility unavailable: {exc}"
                status.color = MUTED

            status.update()

        async def request_access() -> None:
            try:
                if await accessibility.is_enabled():
                    await check_access()
                    return

                await accessibility.request_permission()
                status.value = "Enable Auto Scroll in Android Settings"
                status.color = MUTED
                status.update()

            except Exception as exc:
                status.value = f"Unable to open settings: {exc}"
                status.update()

        card = self._card(
            self._row(
                self._section_title("Accessibility"),
                status,
                alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
            ),
            self._row(
                ft.Button(
                    content="Check access",
                    icon=ft.Icons.REFRESH,
                    expand=True,
                    on_click=check_access,
                ),
                ft.Button(
                    content="Enable",
                    icon=ft.Icons.ACCESSIBILITY_NEW,
                    expand=True,
                    on_click=request_access,
                ),
                spacing=10,
            ),
        )

        return card, check_access

    def _controls_card(self) -> ft.Container:
        speed_text = ft.Text(
            f"{self.settings.speed:g}x", size=20, weight=ft.FontWeight.BOLD, color=INK
        )

        summary_text = ft.Text(self.settings.description, size=13, color=MUTED)
        up_button = ft.Button(content="Up", icon=ft.Icons.ARROW_UPWARD, expand=True)
        down_button = ft.Button(content="Down", icon=ft.Icons.ARROW_DOWNWARD, expand=True)

        def sync_direction_buttons() -> None:
            for direction, button in (
                ("up", up_button),
                ("down", down_button),
            ):
                selected = direction == self.settings.direction
                button.bgcolor = TEAL if selected else PALE
                button.color = CARD if selected else INK
                button.icon_color = CARD if selected else TEAL

        def change_direction(direction: Direction) -> None:
            self.settings.set_direction(direction)
            sync_direction_buttons()

            summary_text.value = self.settings.description

            up_button.update()
            down_button.update()
            summary_text.update()

        def change_speed(event: ft.Event[ft.Slider]) -> None:
            value = event.control.value
            if value is None:
                return

            self.settings.set_speed(float(value))

            speed_text.value = f"{self.settings.speed:g}x"
            summary_text.value = self.settings.description

            speed_text.update()
            summary_text.update()

        up_button.on_click = lambda _: change_direction("up")
        down_button.on_click = lambda _: change_direction("down")

        sync_direction_buttons()

        return self._card(
            self._row(
                self._section_title("Default scrolling"),
                summary_text,
                alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
            ),
            self._row(
                ft.Text("Speed", color=INK, size=14),
                speed_text,
                alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
            ),
            ft.Slider(
                min=0.5,
                max=5.0,
                divisions=9,
                value=self.settings.speed,
                active_color=TEAL,
                label="{value}x",
                round=1,
                on_change=change_speed,
            ),
            self._row(
                ft.Text("0.5x  Slower", color=MUTED, size=11),
                ft.Text("5x  Faster", color=MUTED, size=11),
                alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
            ),
            ft.Text("Direction", color=INK, size=14),
            self._row(up_button, down_button),
        )

    @staticmethod
    def _section_title(title: str) -> ft.Text:
        return ft.Text(title, color=INK, size=16, weight=ft.FontWeight.W_600)

    @staticmethod
    def _row(
        *controls: ft.Control,
        spacing: int = 10,
        alignment: ft.MainAxisAlignment = ft.MainAxisAlignment.START,
    ) -> ft.Row:
        return ft.Row(controls=list(controls), spacing=spacing, alignment=alignment)

    @staticmethod
    def _column(
        *controls: ft.Control,
        spacing: int = 12,
        expand: bool = False,
        scroll: ft.ScrollMode | None = None,
    ) -> ft.Column:
        return ft.Column(controls=list(controls), spacing=spacing, expand=expand, scroll=scroll)

    @classmethod
    def _card(cls, *controls: ft.Control) -> ft.Container:
        return ft.Container(
            bgcolor=CARD, border_radius=18, padding=18, content=cls._column(*controls)
        )
