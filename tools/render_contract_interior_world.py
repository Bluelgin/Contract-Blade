#!/usr/bin/env python3
"""Offline renderer for Contract Blade's contract-interior world.

Reads Java Edition Anvil (.mca) region files directly. No Minecraft process and
no third-party Python packages are required.

Examples:
  python tools/render_contract_interior_world.py ./run/saves/Test --stage 5
  python tools/render_contract_interior_world.py ./world --stage 3 --mode top
  python tools/render_contract_interior_world.py ./world --stage 5 --output stage5.svg
"""

from __future__ import annotations

import argparse
import gzip
import html
import math
import struct
import zlib
from pathlib import Path
from typing import Any, BinaryIO


AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}

GALLERY_BASE_X = -8192
GALLERY_BASE_Z = -4096
GALLERY_STAGE_SPACING = 144
GALLERY_ORIGIN_Y = 80
DEFAULT_RADIUS = 60
DEFAULT_Y_MIN = 74
DEFAULT_Y_MAX = 105


BLOCK_COLORS = {
    "minecraft:grass_block": "#68a84f",
    "minecraft:dirt": "#79553a",
    "minecraft:coarse_dirt": "#6f5037",
    "minecraft:moss_block": "#5d8f4d",
    "minecraft:water": "#4c77bd",
    "minecraft:spruce_planks": "#80613e",
    "minecraft:bamboo_mosaic": "#c3a663",
    "minecraft:stripped_dark_oak_log": "#4a382c",
    "minecraft:dark_oak_log": "#493528",
    "minecraft:dark_oak_fence": "#4b3728",
    "minecraft:deepslate_tiles": "#414550",
    "minecraft:deepslate_tile_stairs": "#454954",
    "minecraft:deepslate_tile_slab": "#4a4e58",
    "minecraft:polished_andesite": "#a4a4a4",
    "minecraft:stone_bricks": "#8e8e8a",
    "minecraft:mossy_stone_bricks": "#76816d",
    "minecraft:cobblestone": "#777773",
    "minecraft:cobblestone_wall": "#70706d",
    "minecraft:smooth_stone": "#b5b5b2",
    "minecraft:gravel": "#827f7c",
    "minecraft:sand": "#d9cf9c",
    "minecraft:white_terracotta": "#d4c7b8",
    "minecraft:white_stained_glass": "#e4eef0",
    "minecraft:red_concrete": "#a62f32",
    "minecraft:red_carpet": "#a72e32",
    "minecraft:cherry_log": "#744b4c",
    "minecraft:cherry_leaves": "#e79fba",
    "minecraft:azalea_leaves": "#4f8148",
    "minecraft:pink_petals": "#e6a1bb",
    "minecraft:bookshelf": "#765236",
    "minecraft:lantern": "#e0a64a",
    "minecraft:barrel": "#8b683f",
    "minecraft:chest": "#9e6b32",
    "minecraft:crafting_table": "#8b6038",
    "minecraft:furnace": "#777773",
    "minecraft:smoker": "#685b4c",
    "minecraft:cauldron": "#55585b",
    "minecraft:cake": "#eee2d4",
    "minecraft:potted_dandelion": "#ceb35b",
    "minecraft:potted_azalea_bush": "#668755",
    "minecraft:lily_pad": "#46733f",
    "minecraft:lodestone": "#6c7883",
}


def read_exact(stream: BinaryIO, size: int) -> bytes:
    data = stream.read(size)
    if len(data) != size:
        raise EOFError(f"wanted {size} bytes, got {len(data)}")
    return data


class NbtReader:
    def __init__(self, data: bytes):
        from io import BytesIO

        self.stream = BytesIO(data)

    def _u8(self) -> int:
        return struct.unpack(">B", read_exact(self.stream, 1))[0]

    def _i8(self) -> int:
        return struct.unpack(">b", read_exact(self.stream, 1))[0]

    def _i16(self) -> int:
        return struct.unpack(">h", read_exact(self.stream, 2))[0]

    def _u16(self) -> int:
        return struct.unpack(">H", read_exact(self.stream, 2))[0]

    def _i32(self) -> int:
        return struct.unpack(">i", read_exact(self.stream, 4))[0]

    def _i64(self) -> int:
        return struct.unpack(">q", read_exact(self.stream, 8))[0]

    def _f32(self) -> float:
        return struct.unpack(">f", read_exact(self.stream, 4))[0]

    def _f64(self) -> float:
        return struct.unpack(">d", read_exact(self.stream, 8))[0]

    def _string(self) -> str:
        length = self._u16()
        return read_exact(self.stream, length).decode("utf-8", errors="replace")

    def payload(self, tag_type: int) -> Any:
        if tag_type == 0:
            return None
        if tag_type == 1:
            return self._i8()
        if tag_type == 2:
            return self._i16()
        if tag_type == 3:
            return self._i32()
        if tag_type == 4:
            return self._i64()
        if tag_type == 5:
            return self._f32()
        if tag_type == 6:
            return self._f64()
        if tag_type == 7:
            length = self._i32()
            return read_exact(self.stream, max(0, length))
        if tag_type == 8:
            return self._string()
        if tag_type == 9:
            child_type = self._u8()
            length = self._i32()
            return [self.payload(child_type) for _ in range(max(0, length))]
        if tag_type == 10:
            result: dict[str, Any] = {}
            while True:
                child_type = self._u8()
                if child_type == 0:
                    return result
                name = self._string()
                result[name] = self.payload(child_type)
        if tag_type == 11:
            length = self._i32()
            return [self._i32() for _ in range(max(0, length))]
        if tag_type == 12:
            length = self._i32()
            return [self._i64() for _ in range(max(0, length))]
        raise ValueError(f"unsupported NBT tag type {tag_type}")

    def root(self) -> dict[str, Any]:
        tag_type = self._u8()
        if tag_type != 10:
            raise ValueError(f"NBT root is type {tag_type}, expected compound")
        _root_name = self._string()
        value = self.payload(10)
        if not isinstance(value, dict):
            raise ValueError("NBT root did not decode as a compound")
        return value


def decompress_chunk(payload: bytes, compression: int) -> bytes:
    if compression == 1:
        return gzip.decompress(payload)
    if compression == 2:
        return zlib.decompress(payload)
    if compression == 3:
        return payload
    raise ValueError(f"unsupported Anvil compression type {compression}")


def read_chunk_nbt(dimension: Path, chunk_x: int, chunk_z: int) -> dict[str, Any] | None:
    region_x = chunk_x // 32
    region_z = chunk_z // 32
    local_x = chunk_x - region_x * 32
    local_z = chunk_z - region_z * 32
    region_path = dimension / "region" / f"r.{region_x}.{region_z}.mca"
    if not region_path.exists():
        return None

    with region_path.open("rb") as stream:
        location_index = local_x + local_z * 32
        stream.seek(location_index * 4)
        entry = read_exact(stream, 4)
        sector_offset = int.from_bytes(entry[:3], "big")
        sector_count = entry[3]
        if sector_offset == 0 or sector_count == 0:
            return None

        stream.seek(sector_offset * 4096)
        length = struct.unpack(">I", read_exact(stream, 4))[0]
        compression = struct.unpack(">B", read_exact(stream, 1))[0]
        external = bool(compression & 0x80)
        compression &= 0x7F

        if external:
            external_path = dimension / "region" / f"c.{chunk_x}.{chunk_z}.mcc"
            if not external_path.exists():
                raise FileNotFoundError(f"external chunk payload missing: {external_path}")
            compressed = external_path.read_bytes()
        else:
            compressed = read_exact(stream, max(0, length - 1))

    return NbtReader(decompress_chunk(compressed, compression)).root()


def section_list(root: dict[str, Any]) -> list[dict[str, Any]]:
    sections = root.get("sections")
    if isinstance(sections, list):
        return [entry for entry in sections if isinstance(entry, dict)]

    level = root.get("Level")
    if isinstance(level, dict):
        legacy = level.get("Sections")
        if isinstance(legacy, list):
            return [entry for entry in legacy if isinstance(entry, dict)]
    return []


def palette_name(entry: Any) -> str:
    if isinstance(entry, dict):
        name = entry.get("Name")
        if isinstance(name, str):
            return name
        legacy_name = entry.get("name")
        if isinstance(legacy_name, str):
            return legacy_name
    if isinstance(entry, str):
        return entry
    return "minecraft:air"


def decode_section(section: dict[str, Any]) -> tuple[int, list[str]]:
    section_y = int(section.get("Y", 0))
    block_states = section.get("block_states")
    if not isinstance(block_states, dict):
        block_states = section.get("BlockStates")
        palette = section.get("Palette")
        if not isinstance(palette, list):
            return section_y, ["minecraft:air"] * 4096
        data = block_states if isinstance(block_states, list) else []
    else:
        palette = block_states.get("palette")
        data = block_states.get("data")

    if not isinstance(palette, list) or not palette:
        return section_y, ["minecraft:air"] * 4096

    names = [palette_name(entry) for entry in palette]
    if len(names) == 1:
        return section_y, [names[0]] * 4096

    if not isinstance(data, list):
        return section_y, ["minecraft:air"] * 4096

    bits = max(4, (len(names) - 1).bit_length())
    values_per_long = 64 // bits
    mask = (1 << bits) - 1
    decoded = ["minecraft:air"] * 4096

    for index in range(4096):
        long_index = index // values_per_long
        if long_index >= len(data):
            break
        bit_index = (index % values_per_long) * bits
        raw = int(data[long_index]) & 0xFFFFFFFFFFFFFFFF
        palette_index = (raw >> bit_index) & mask
        if palette_index < len(names):
            decoded[index] = names[palette_index]

    return section_y, decoded


def load_area(
    dimension: Path,
    min_x: int,
    max_x: int,
    min_z: int,
    max_z: int,
    min_y: int,
    max_y: int,
) -> dict[tuple[int, int, int], str]:
    blocks: dict[tuple[int, int, int], str] = {}

    min_chunk_x = min_x // 16
    max_chunk_x = max_x // 16
    min_chunk_z = min_z // 16
    max_chunk_z = max_z // 16

    for chunk_x in range(min_chunk_x, max_chunk_x + 1):
        for chunk_z in range(min_chunk_z, max_chunk_z + 1):
            root = read_chunk_nbt(dimension, chunk_x, chunk_z)
            if root is None:
                continue

            for section in section_list(root):
                section_y, states = decode_section(section)
                base_y = section_y * 16
                if base_y > max_y or base_y + 15 < min_y:
                    continue

                for index, name in enumerate(states):
                    if name in AIR:
                        continue
                    local_x = index & 15
                    local_z = (index >> 4) & 15
                    local_y = (index >> 8) & 15
                    world_x = chunk_x * 16 + local_x
                    world_z = chunk_z * 16 + local_z
                    world_y = base_y + local_y
                    if (
                        min_x <= world_x <= max_x
                        and min_z <= world_z <= max_z
                        and min_y <= world_y <= max_y
                    ):
                        blocks[(world_x, world_y, world_z)] = name

    return blocks


def block_color(name: str) -> str:
    direct = BLOCK_COLORS.get(name)
    if direct:
        return direct
    if "cherry" in name:
        return "#df9bb4"
    if "leaves" in name:
        return "#568250"
    if "log" in name or "wood" in name:
        return "#72513a"
    if "planks" in name:
        return "#98704c"
    if "deepslate" in name:
        return "#484b54"
    if "stone" in name or "andesite" in name:
        return "#969692"
    if "glass" in name:
        return "#d9e9ec"
    if "water" in name:
        return "#4d79c5"
    if "sand" in name:
        return "#d5ca98"
    if "grass" in name or "moss" in name:
        return "#69a454"
    if "flower" in name or "petal" in name:
        return "#e9a5bb"
    if "red_" in name:
        return "#a73a3c"
    return "#9b8d7b"


def shade(hex_color: str, factor: float) -> str:
    value = hex_color.lstrip("#")
    rgb = [int(value[i : i + 2], 16) for i in (0, 2, 4)]
    adjusted = [max(0, min(255, round(channel * factor))) for channel in rgb]
    return "#" + "".join(f"{channel:02x}" for channel in adjusted)


def svg_header(width: int, height: int, title: str) -> list[str]:
    safe_title = html.escape(title)
    return [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#f8f6f1"/>',
        f'<text x="24" y="38" font-family="sans-serif" font-size="24" '
        f'font-weight="700" fill="#222">{safe_title}</text>',
    ]


def render_top(
    blocks: dict[tuple[int, int, int], str],
    output: Path,
    title: str,
    pixel: int = 6,
) -> None:
    if not blocks:
        raise ValueError("no blocks found in render bounds")

    top: dict[tuple[int, int], tuple[int, str]] = {}
    for (x, y, z), name in blocks.items():
        current = top.get((x, z))
        if current is None or y > current[0]:
            top[(x, z)] = (y, name)

    min_x = min(x for x, _ in top)
    max_x = max(x for x, _ in top)
    min_z = min(z for _, z in top)
    max_z = max(z for _, z in top)
    margin = 24
    header = 56
    width = (max_x - min_x + 1) * pixel + margin * 2
    height = (max_z - min_z + 1) * pixel + margin * 2 + header

    lines = svg_header(width, height, title)
    for (x, z), (y, name) in top.items():
        color = block_color(name)
        px = margin + (x - min_x) * pixel
        py = header + margin + (z - min_z) * pixel
        lines.append(
            f'<rect x="{px}" y="{py}" width="{pixel}" height="{pixel}" '
            f'fill="{color}" data-y="{y}" data-block="{html.escape(name)}"/>'
        )
    lines.append("</svg>")
    output.write_text("\n".join(lines), encoding="utf-8")


def render_iso(
    blocks: dict[tuple[int, int, int], str],
    output: Path,
    title: str,
    tile_width: int = 10,
    tile_height: int = 6,
    block_height: int = 7,
) -> None:
    if not blocks:
        raise ValueError("no blocks found in render bounds")

    coords = list(blocks)
    min_world_y = min(y for _, y, _ in coords)

    def project(x: int, y: int, z: int) -> tuple[float, float]:
        sx = (x - z) * tile_width / 2
        sy = (x + z) * tile_height / 2 - (y - min_world_y) * block_height
        return sx, sy

    projected = [project(x, y, z) for x, y, z in coords]
    min_sx = min(x for x, _ in projected)
    max_sx = max(x for x, _ in projected)
    min_sy = min(y for _, y in projected)
    max_sy = max(y for _, y in projected)

    margin = 60
    header = 58
    width = math.ceil(max_sx - min_sx + tile_width * 4 + margin * 2)
    height = math.ceil(max_sy - min_sy + block_height * 4 + margin * 2 + header)

    ox = -min_sx + margin + tile_width * 2
    oy = -min_sy + margin + header

    lines = svg_header(width, height, title)
    occupied = set(blocks)
    ordered = sorted(coords, key=lambda pos: (pos[0] + pos[2], pos[1], pos[0] - pos[2]))

    for x, y, z in ordered:
        name = blocks[(x, y, z)]
        base = block_color(name)
        sx, sy = project(x, y, z)
        sx += ox
        sy += oy

        top_visible = (x, y + 1, z) not in occupied
        left_visible = (x - 1, y, z) not in occupied
        right_visible = (x, y, z - 1) not in occupied

        top = [
            (sx, sy - block_height),
            (sx + tile_width / 2, sy - block_height + tile_height / 2),
            (sx, sy - block_height + tile_height),
            (sx - tile_width / 2, sy - block_height + tile_height / 2),
        ]
        left = [
            (sx - tile_width / 2, sy - block_height + tile_height / 2),
            (sx, sy - block_height + tile_height),
            (sx, sy + tile_height),
            (sx - tile_width / 2, sy + tile_height / 2),
        ]
        right = [
            (sx, sy - block_height + tile_height),
            (sx + tile_width / 2, sy - block_height + tile_height / 2),
            (sx + tile_width / 2, sy + tile_height / 2),
            (sx, sy + tile_height),
        ]

        def polygon(points: list[tuple[float, float]], color: str) -> None:
            pts = " ".join(f"{px:.1f},{py:.1f}" for px, py in points)
            lines.append(
                f'<polygon points="{pts}" fill="{color}" stroke="#00000022" '
                f'stroke-width="0.4" data-block="{html.escape(name)}"/>'
            )

        if left_visible:
            polygon(left, shade(base, 0.72))
        if right_visible:
            polygon(right, shade(base, 0.58))
        if top_visible:
            polygon(top, base)

    lines.append("</svg>")
    output.write_text("\n".join(lines), encoding="utf-8")


def dimension_path(world: Path) -> Path:
    modern = world / "dimensions" / "maid_weapon" / "contract_interior"
    if modern.exists():
        return modern
    raise FileNotFoundError(
        "contract interior dimension not found under "
        f"{modern}; generate/open the gallery in this save first"
    )


def gallery_bounds(stage: int, radius: int) -> tuple[int, int, int, int]:
    origin_x = GALLERY_BASE_X + (stage - 1) * GALLERY_STAGE_SPACING
    origin_z = GALLERY_BASE_Z
    return (
        origin_x - radius,
        origin_x + radius,
        origin_z - radius,
        origin_z + radius,
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Render Contract Blade's contract-interior Anvil data offline."
    )
    parser.add_argument("world", type=Path, help="Minecraft Java world folder (contains level.dat)")
    parser.add_argument("--stage", type=int, choices=range(1, 6), default=5)
    parser.add_argument("--mode", choices=("iso", "top"), default="iso")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--radius", type=int, default=DEFAULT_RADIUS)
    parser.add_argument("--y-min", type=int, default=DEFAULT_Y_MIN)
    parser.add_argument("--y-max", type=int, default=DEFAULT_Y_MAX)
    args = parser.parse_args()

    world = args.world.expanduser().resolve()
    dimension = dimension_path(world)
    min_x, max_x, min_z, max_z = gallery_bounds(args.stage, args.radius)
    blocks = load_area(
        dimension,
        min_x,
        max_x,
        min_z,
        max_z,
        args.y_min,
        args.y_max,
    )

    output = args.output
    if output is None:
        output = Path(f"contract_interior_stage_{args.stage}_{args.mode}.svg")
    output = output.expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)

    title = (
        f"Contract Interior Stage {args.stage} — "
        f"offline {args.mode} render from Anvil data"
    )
    if args.mode == "top":
        render_top(blocks, output, title)
    else:
        render_iso(blocks, output, title)

    print(
        f"Rendered {len(blocks):,} non-air blocks from "
        f"{dimension} -> {output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
