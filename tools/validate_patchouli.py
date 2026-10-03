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
    service = Path('src/main/java/com/maidweapon/forge/system/ContractHandbookService.java').read_text(encoding='utf-8')
    assert 'PlayerEvent.ItemCraftedEvent' in service and 'itemId.getNamespace()' in service
    assert 'PlayerLoggedInEvent' not in service and 'ItemPickupEvent' not in service
    assert 'Player.PERSISTED_NBT_TAG' in service and 'PlayerEvent.Clone' in service
    assert 'ContractBladeHandbookReceived' in service and 'ContractBladeHandbookPending' in service
    assert '!player.getInventory().add(book)' in service
    altar = parsed[RESOURCE_ROOT / 'data/maid_weapon/advancements/tutorial_altar_crafted.json']
    assert len(altar['requirements']) == 1 and set(altar['requirements'][0]) == set(altar['criteria'])
    actual = {value['conditions']['recipe_id'] for value in altar['criteria'].values()}
    expected = {'maid_weapon:altar/' + p.stem for p in (RESOURCE_ROOT / 'data/maid_weapon/recipes/altar').glob('*.json')}
    assert actual == expected and 'display' not in altar
    for criterion in altar['criteria'].values():
        assert criterion['trigger'] == 'touhou_little_maid:altar/altar_craft'
    for table in translations.values():
        assert table['maid_weapon.message.handbook_received']
        assert 'automatically' not in table['maid_weapon.tooltip.direct_bind_hint']
        assert '自动显形' not in table['maid_weapon.tooltip.direct_bind_hint']
        for key in references:
            if '.page.' in key:
                assert not any(word in table[key] for word in ('通用七罪', '契约七罪', '色欲', '罪孽', 'Lust', 'embedded sins', 'contract sins', 'versioned format', '权威内置契约'))
    print(f"Validated {len(parsed)} JSON files and {len(references)} Patchouli translations")


if __name__ == "__main__":
    main()
