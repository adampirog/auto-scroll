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

        access_status = ft.Text("Checking accessibility...", color=MUTED, size=13)

        overlay_status = ft.Text("Overlay hidden", color=MUTED, size=12)

        overlay_switch = ft.Switch(value=False, disabled=True)

        speed_value = ft.Text(
            f"{self.settings.speed:.1f}x",
            color=INK,
            size=20,
            weight=ft.FontWeight.BOLD,
        )

        speed_slider = ft.Slider(
            min=0.5,
            max=5.0,
            divisions=9,
            value=self.settings.speed,
            label="{value}x",
            round=1,
            active_color=TEAL,
            disabled=True,
        )

        direction_summary = ft.Text(self.settings.description, color=MUTED, size=13)

        up_button = ft.Button(content="Up", icon=ft.Icons.ARROW_UPWARD, expand=True, disabled=True)

        down_button = ft.Button(
            content="Down", icon=ft.Icons.ARROW_DOWNWARD, expand=True, disabled=True
        )

        ready = False

        def update_direction_buttons() -> None:
            for direction, button in (("up", up_button), ("down", down_button)):
                selected = self.settings.direction == direction
                button.bgcolor = TEAL if selected else PALE
                button.color = CARD if selected else INK
                button.icon_color = CARD if selected else TEAL

        def update_settings_display() -> None:
            speed_value.value = f"{self.settings.speed:.1f}×"
            direction_summary.value = self.settings.description
            update_direction_buttons()

        async def refresh() -> None:
            nonlocal ready

            try:
                state = await accessibility.get_state()

                enabled = bool(state.get("permission_enabled"))
                ready = bool(state.get("service_ready"))

                if ready:
                    access_status.value = "Accessibility ready"
                    access_status.color = TEAL
                elif enabled:
                    access_status.value = "Accessibility enabled, service unavailable"
                    access_status.color = MUTED
                else:
                    access_status.value = "Accessibility disabled"
                    access_status.color = MUTED

                overlay_visible = ready and bool(state.get("overlay_visible"))

                overlay_switch.value = overlay_visible
                overlay_switch.disabled = not ready

                overlay_status.value = "Overlay active" if overlay_visible else "Overlay hidden"

                speed_slider.disabled = not ready
                up_button.disabled = not ready
                down_button.disabled = not ready

                if ready:
                    self.settings.set_speed(float(state["speed"]))
                    self.settings.set_direction(str(state["direction"]))

                    speed_slider.value = self.settings.speed
                    update_settings_display()

            except Exception as exc:
                ready = False
                access_status.value = f"Accessibility error: {exc}"
                access_status.color = MUTED

                overlay_switch.value = False
                overlay_switch.disabled = True

                speed_slider.disabled = True
                up_button.disabled = True
                down_button.disabled = True

            self.page.update()

        async def enable_access() -> None:
            try:
                await accessibility.request_permission()
            except Exception as exc:
                access_status.value = f"Unable to open Settings: {exc}"
                self.page.update()

        async def toggle_overlay(event: ft.Event[ft.Switch]) -> None:
            requested = bool(event.control.value)

            overlay_switch.disabled = True
            self.page.update()

            message: str | None = None

            try:
                if requested:
                    success = await accessibility.show_overlay()
                else:
                    success = await accessibility.hide_overlay()

                if not success:
                    message = "Unable to change overlay state"

            except Exception as exc:
                message = f"Overlay error: {exc}"

            await refresh()

            if message is not None:
                overlay_status.value = message
                self.page.update()

        def change_speed(event: ft.Event[ft.Slider]) -> None:
            value = event.control.value

            if value is None:
                return

            self.settings.set_speed(float(value))
            update_settings_display()
            self.page.update()

        async def save_speed(event: ft.Event[ft.Slider]) -> None:
            value = event.control.value

            if value is None:
                return

            self.settings.set_speed(float(value))

            try:
                success = await accessibility.set_config(
                    self.settings.direction, self.settings.speed
                )

                if not success:
                    overlay_status.value = "Unable to save speed"
            except Exception as exc:
                overlay_status.value = f"Settings error: {exc}"

            await refresh()

        async def select_direction(direction: Direction) -> None:
            self.settings.set_direction(direction)
            update_settings_display()
            self.page.update()

            try:
                success = await accessibility.set_config(
                    self.settings.direction,
                    self.settings.speed,
                )

                if not success:
                    overlay_status.value = "Unable to save direction"
            except Exception as exc:
                overlay_status.value = f"Settings error: {exc}"

            await refresh()

        async def select_up() -> None:
            await select_direction("up")

        async def select_down() -> None:
            await select_direction("down")

        overlay_switch.on_change = toggle_overlay
        speed_slider.on_change = change_speed
        speed_slider.on_change_end = save_speed

        up_button.on_click = select_up
        down_button.on_click = select_down

        update_direction_buttons()

        content = self._column(
            self._header(),
            self._card(
                self._row(
                    self._column(
                        self._section_title("Floating controls"),
                        ft.Text("Show controls over other apps", color=MUTED, size=12),
                        spacing=4,
                        expand=True,
                    ),
                    overlay_switch,
                    alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                ),
                overlay_status,
                ft.Button(
                    content="Refresh status",
                    icon=ft.Icons.REFRESH,
                    on_click=refresh,
                ),
            ),
            self._card(
                self._row(
                    self._section_title("Accessibility"),
                    access_status,
                    alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                ),
                self._row(
                    ft.Button(
                        content="Check access",
                        icon=ft.Icons.REFRESH,
                        expand=True,
                        on_click=refresh,
                    ),
                    ft.Button(
                        content="Settings",
                        icon=ft.Icons.ACCESSIBILITY_NEW,
                        expand=True,
                        on_click=enable_access,
                    ),
                ),
            ),
            self._card(
                self._row(
                    self._section_title("Scrolling"),
                    direction_summary,
                    alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                ),
                self._row(
                    ft.Text("Speed", color=INK, size=14),
                    speed_value,
                    alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                ),
                speed_slider,
                self._row(
                    ft.Text("0.5×  Slower", color=MUTED, size=11),
                    ft.Text("5×  Faster", color=MUTED, size=11),
                    alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                ),
                ft.Text("Direction", color=INK, size=14),
                self._row(up_button, down_button),
            ),
            spacing=18,
            expand=True,
            scroll=ft.ScrollMode.AUTO,
        )

        async def on_lifecycle(event: ft.AppLifecycleStateChangeEvent) -> None:
            if event.state == ft.AppLifecycleState.RESUME:
                await refresh()

        self.page.on_app_lifecycle_state_change = on_lifecycle

        self.page.add(
            ft.SafeArea(
                expand=True,
                content=ft.Container(expand=True, padding=20, content=content),
            )
        )

        self.page.run_task(refresh)

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
