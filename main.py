import flet as ft

from auto_scroll.app import AutoScrollApp


def main(page: ft.Page) -> None:
    app = AutoScrollApp(page=page)
    app.mount()


if __name__ == "__main__":
    ft.run(main, assets_dir="assets")
