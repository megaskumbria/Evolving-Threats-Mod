# -*- coding: utf-8 -*-
"""Offline Russian -> English using Argos Translate (no API rate limits)."""
from __future__ import annotations

_ru_en_ready = False


def ensure_ru_en() -> None:
    global _ru_en_ready
    if _ru_en_ready:
        return
    from argostranslate import package, translate

    if translate.get_translation_from_codes("ru", "en") is not None:
        _ru_en_ready = True
        return
    package.update_package_index()
    available = package.get_available_packages()
    pair = next(
        (p for p in available if p.from_code == "ru" and p.to_code == "en"),
        None,
    )
    if pair is None:
        raise RuntimeError("Argos: no ru->en language package in index")
    package.install_from_path(pair.download())
    _ru_en_ready = True


def translate_ru_en(text: str) -> str:
    ensure_ru_en()
    from argostranslate import translate

    t = translate.get_translation_from_codes("ru", "en")
    if t is None:
        raise RuntimeError("Argos: ru->en package not installed")
    return t.translate(text)
