import json
from pathlib import Path


RESOURCE_ROOT = Path("src/main/resources")
BOOK_ROOT = RESOURCE_ROOT / "assets/maid_weapon/patchouli_books/contract_fragments"


def strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for child in value:
            yield from strings(child)
    elif isinstance(value, dict):
        for child in value.values():
            yield from strings(child)


def main():
    parsed = {}
    failures = []
    for path in RESOURCE_ROOT.rglob("*.json"):
        try:
            parsed[path] = json.loads(path.read_text(encoding="utf-8"))
        except Exception as error:
            failures.append(f"{path}: {error}")
    if failures:
        raise SystemExit("Invalid JSON:\n" + "\n".join(failures))

    translations = {
        locale: json.loads((RESOURCE_ROOT / f"assets/maid_weapon/lang/{locale}.json")
                           .read_text(encoding="utf-8"))
        for locale in ("zh_cn", "en_us")
    }
    references = set()
    for path, value in parsed.items():
        if BOOK_ROOT in path.parents or path == BOOK_ROOT:
            references.update(text for text in strings(value)
                              if text.startswith("patchouli.maid_weapon."))
    missing = {
        locale: sorted(references - set(table))
        for locale, table in translations.items()
    }
    missing = {locale: keys for locale, keys in missing.items() if keys}
    if missing:
        raise SystemExit("Missing Patchouli translations: " + repr(missing))
    print(f"Validated {len(parsed)} JSON files and {len(references)} Patchouli translations")


if __name__ == "__main__":
    main()
