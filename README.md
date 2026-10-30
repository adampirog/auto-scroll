# Auto Scroll

Hands-free scrolling for long content. This repository currently provides the **Flet UI foundation**; system-wide scrolling and the floating Android overlay are not implemented yet.

## Requirements

- Python 3.13+
- Flet 1.x (`flet>=1.0,<2` in `pyproject.toml`)
- Android SDK / build tooling for building an APK

## Run locally

```bash
pip install -e .
flet run main.py
```

Alternatively, if using `uv`:

```bash
uv run --with flet-desktop flet run main.py
```

## Validate

The development tools live in the `dev` dependency group:

```bash
uv sync --group dev
uv run pytest
uv run ruff check .
```

## Build Android APK

```bash
flet build apk
```

The build command uses the Flet packaging configuration in `pyproject.toml` and requires the Android toolchain. It has **not** been verified in the current development environment.

## Roadmap

- **M0.1 — UI foundation:** main setup screen, editable default speed (0.5×–5×) and direction, and clearly identified placeholders for native functionality.
- **M0.2 — Android feasibility:** demonstrate permission setup and one scroll gesture in another app.
- **M0.3 — Floating overlay:** expose basic controls over other apps and connect them to scrolling.

Scrolling settings are currently **in-memory only**. The disabled overlay switch and Android integration statuses are placeholders, not live Android functionality.
