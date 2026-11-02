import flet as ft

from auto_scroll.settings import Direction, ScrollSettings
from auto_scroll.theme import CANVAS, CARD, INK, MUTED, PALE, TEAL


class AutoScrollApp:
    def __init__(self, *, page: ft.Page, settings: ScrollSettings | None = None) -> None:
        self.page = page
        self.settings = settings if settings is not None else ScrollSettings()

    def mount(self) -> None:
        """Build and mount the main application screen."""
        self.page.title = "Auto Scroll"
        self.page.bgcolor = CANVAS
        self.page.theme_mode = ft.ThemeMode.LIGHT
        self.page.padding = 0
        self.page.theme = ft.Theme(color_scheme_seed=TEAL)

        content = self._column(
            self._header(),
            self._overlay_card(),
            self._permissions_card(),
            self._controls_card(),
            self._help_text(),
            spacing=18,
            expand=True,
            scroll=ft.ScrollMode.AUTO,
        )

        self.page.add(
            ft.SafeArea(
                expand=True,
                content=ft.Container(expand=True, padding=20, content=content),
            )
        )

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

    def _overlay_card(self) -> ft.Container:
        return self._card(
            self._row(
                self._column(
                    ft.Text("Floating controls", color=INK, size=17, weight=ft.FontWeight.W_600),
                    ft.Text("Show controls over other apps", color=MUTED, size=12),
                    spacing=4,
                    expand=True,
                ),
                ft.Switch(value=False, disabled=True),
                alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
            ),
            ft.Container(
                bgcolor=PALE,
                padding=12,
                border_radius=10,
                content=self._row(
                    ft.Icon(ft.Icons.INFO_OUTLINE, color=TEAL, size=18),
                    ft.Text(
                        "Overlay integration is coming in M0.2.", color=INK, size=12, expand=True
                    ),
                    spacing=8,
                ),
            ),
        )

    def _permissions_card(self) -> ft.Container:
        return self._card(
            self._section_title("Android integration"),
            self._permission_row(
                ft.Icons.ACCESSIBILITY_NEW,
                "Accessibility service",
                "Required to scroll in other apps",
            ),
            ft.Divider(height=1, color=CANVAS),
            self._permission_row(
                ft.Icons.LAYERS_OUTLINED,
                "Floating controls",
                "Will be provided by the Android service",
            ),
            ft.Text(
                "These are planned capabilities, not live permission checks.", color=MUTED, size=11
            ),
        )

    def _permission_row(self, icon: ft.IconData, title: str, description: str) -> ft.Row:
        return self._row(
            ft.Icon(icon, color=TEAL, size=24),
            self._column(
                ft.Text(title, color=INK, size=13, weight=ft.FontWeight.W_600),
                ft.Text(description, color=MUTED, size=11),
                spacing=2,
                expand=True,
            ),
            ft.Text("Planned", color=MUTED, size=11),
            spacing=12,
        )

    def _controls_card(self) -> ft.Container:
        """Build scrolling controls and their local event handlers."""
        speed_text = ft.Text(
            f"{self.settings.speed:g}x", size=20, weight=ft.FontWeight.BOLD, color=INK
        )
        summary_text = ft.Text(self.settings.description, size=13, color=MUTED)

        up_button = ft.Button(content="Up", icon=ft.Icons.ARROW_UPWARD, expand=True)
        down_button = ft.Button(content="Down", icon=ft.Icons.ARROW_DOWNWARD, expand=True)

        def sync_direction_buttons() -> None:
            for direction, button in (("up", up_button), ("down", down_button)):
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
            ft.Text(
                "These values are currently kept in memory for this session.", color=MUTED, size=11
            ),
        )

    @staticmethod
    def _section_title(title: str) -> ft.Text:
        return ft.Text(title, color=INK, size=16, weight=ft.FontWeight.W_600)

    @staticmethod
    def _help_text() -> ft.Text:
        return ft.Text(
            "V1 target: scroll webpages and documents in other Android apps. "
            "This build is the Flet UI foundation only.",
            color=MUTED,
            size=12,
            text_align=ft.TextAlign.CENTER,
        )

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
