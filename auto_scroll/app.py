"""The Auto Scroll application's Flet setup screen.

Android accessibility and floating-overlay integration is not available yet.
This module intentionally avoids requesting permissions or starting scrolling.
"""

import flet as ft

from auto_scroll.settings import Direction, ScrollSettings
from auto_scroll.theme import CANVAS, CARD, INK, MUTED, PALE, TEAL


class AutoScrollApp:
    """Render the app's settings UI and keep its controls in sync."""

    def __init__(self, *, page: ft.Page, settings: ScrollSettings | None = None) -> None:
        self.page = page
        self.settings = settings if settings is not None else ScrollSettings()
        self._speed_text: ft.Text | None = None
        self._summary_text: ft.Text | None = None
        self._up_button: ft.Button | None = None
        self._down_button: ft.Button | None = None

    def mount(self) -> None:
        """Attach the initial, non-native setup screen to the page."""
        self.page.title = "Auto Scroll"
        self.page.bgcolor = CANVAS
        self.page.theme_mode = ft.ThemeMode.LIGHT
        self.page.padding = 0
        self.page.theme = ft.Theme(color_scheme_seed=TEAL)

        self._speed_text = ft.Text(self._speed_description(), size=20, weight=ft.FontWeight.BOLD,
                                   color=INK)
        self._summary_text = ft.Text(self.settings.description, size=13, color=MUTED)
        self._up_button = self._direction_button("up", "Up", ft.Icons.ARROW_UPWARD)
        self._down_button = self._direction_button("down", "Down", ft.Icons.ARROW_DOWNWARD)
        self._sync_direction_buttons()

        self.page.add(
            ft.SafeArea(
                expand=True,
                content=ft.Container(
                    expand=True,
                    padding=20,
                    content=ft.Column(
                        expand=True,
                        spacing=18,
                        scroll=ft.ScrollMode.AUTO,
                        controls=[
                            self._header(),
                            self._overlay_card(),
                            self._permissions_card(),
                            self._controls_card(),
                            self._help_text(),
                        ],
                    ),
                ),
            )
        )

    def _header(self) -> ft.Row:
        return ft.Row(
            spacing=14,
            controls=[
                ft.Container(
                    width=50,
                    height=50,
                    bgcolor=TEAL,
                    border_radius=15,
                    alignment=ft.Alignment.CENTER,
                    content=ft.Icon(ft.Icons.KEYBOARD_DOUBLE_ARROW_DOWN, color=CARD, size=28),
                ),
                ft.Column(
                    spacing=2,
                    controls=[
                        ft.Text("Auto Scroll", color=INK, size=24, weight=ft.FontWeight.BOLD),
                        ft.Text("Hands-free scrolling for long content", color=MUTED, size=12),
                    ],
                ),
            ],
        )

    def _overlay_card(self) -> ft.Container:
        return self._card(
            ft.Column(
                spacing=12,
                controls=[
                    ft.Row(
                        alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                        controls=[
                            ft.Column(
                                spacing=4,
                                expand=True,
                                controls=[
                                    ft.Text("Floating controls", color=INK, size=17,
                                            weight=ft.FontWeight.W_600),
                                    ft.Text("Show controls over other apps", color=MUTED, size=12),
                                ],
                            ),
                            ft.Switch(value=False, disabled=True),
                        ],
                    ),
                    ft.Container(
                        bgcolor=PALE,
                        padding=12,
                        border_radius=10,
                        content=ft.Row(
                            spacing=8,
                            controls=[
                                ft.Icon(ft.Icons.INFO_OUTLINE, color=TEAL, size=18),
                                ft.Text("Overlay integration is coming in M0.2.",
                                        color=INK, size=12, expand=True),
                            ],
                        ),
                    ),
                ],
            )
        )

    def _permissions_card(self) -> ft.Container:
        return self._card(
            ft.Column(
                spacing=14,
                controls=[
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
                    ft.Text("These are planned capabilities, not live permission checks.",
                            size=11, color=MUTED),
                ],
            )
        )

    def _permission_row(self, icon: str, title: str, description: str) -> ft.Row:
        return ft.Row(
            spacing=12,
            controls=[
                ft.Icon(icon, color=TEAL, size=24),
                ft.Column(
                    spacing=2,
                    expand=True,
                    controls=[
                        ft.Text(title, color=INK, weight=ft.FontWeight.W_600, size=13),
                        ft.Text(description, color=MUTED, size=11),
                    ],
                ),
                ft.Text("Planned", color=MUTED, size=11),
            ],
        )

    def _controls_card(self) -> ft.Container:
        return self._card(
            ft.Column(
                spacing=12,
                controls=[
                    ft.Row(
                        alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                        controls=[
                            self._section_title("Default scrolling"),
                            self._summary_text,
                        ],
                    ),
                    ft.Row(
                        alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                        controls=[
                            ft.Text("Speed", color=INK, size=14),
                            self._speed_text,
                        ],
                    ),
                    ft.Slider(
                        min=0.5,
                        max=5.0,
                        divisions=9,
                        value=self.settings.speed,
                        active_color=TEAL,
                        label="{value}×",
                        round=1,
                        on_change=self._on_speed_change,
                    ),
                    ft.Row(
                        alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                        controls=[
                            ft.Text("0.5×  Slower", size=11, color=MUTED),
                            ft.Text("5×  Faster", size=11, color=MUTED),
                        ],
                    ),
                    ft.Text("Direction", color=INK, size=14),
                    ft.Row(
                        spacing=10,
                        controls=[self._up_button, self._down_button],
                    ),
                    ft.Text("These values are currently kept in memory for this session.",
                            color=MUTED, size=11),
                ],
            )
        )

    def _direction_button(self, direction: Direction, text: str, icon: str) -> ft.Button:
        return ft.Button(
            content=text,
            icon=icon,
            expand=True,
            on_click=lambda _: self._select_direction(direction),
        )

    def _select_direction(self, direction: Direction) -> None:
        self.settings.set_direction(direction)
        self._sync_direction_buttons()
        assert self._up_button is not None and self._down_button is not None
        self._up_button.update()
        self._down_button.update()
        self._refresh_summary()

    def _sync_direction_buttons(self) -> None:
        assert self._up_button is not None and self._down_button is not None
        for direction, button in (("up", self._up_button), ("down", self._down_button)):
            selected = direction == self.settings.direction
            button.bgcolor = TEAL if selected else PALE
            button.color = CARD if selected else INK
            button.icon_color = CARD if selected else TEAL

    def _on_speed_change(self, event: ft.Event[ft.Slider]) -> None:
        self.settings.set_speed(float(event.control.value))
        assert self._speed_text is not None
        self._speed_text.value = self._speed_description()
        self._speed_text.update()
        self._refresh_summary()

    def _refresh_summary(self) -> None:
        assert self._summary_text is not None
        self._summary_text.value = self.settings.description
        self._summary_text.update()

    def _speed_description(self) -> str:
        return f"{self.settings.speed:g}×"

    @staticmethod
    def _section_title(title: str) -> ft.Text:
        return ft.Text(title, color=INK, size=16, weight=ft.FontWeight.W_600)

    @staticmethod
    def _card(content: ft.Column) -> ft.Container:
        return ft.Container(
            bgcolor=CARD,
            border_radius=18,
            padding=18,
            content=content,
        )

    @staticmethod
    def _help_text() -> ft.Text:
        return ft.Text(
            "V1 target: scroll webpages and documents in other Android apps. "
            "This build is the Flet UI foundation only.",
            color=MUTED,
            size=12,
            text_align=ft.TextAlign.CENTER,
        )
