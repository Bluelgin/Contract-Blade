#!/usr/bin/env python3
"""Offline renderer for Contract Blade's contract-interior world.

Reads Java Edition Anvil (.mca) region files directly. No Minecraft process and
no third-party Python packages are required.

Examples:
  python tools/render_contract_interior_world.py ./run/saves/Test --stage 5
  python tools/render_contract_interior_world.py ./world --stage 3 --mode top
  python tools/render_contract_interior_world.py ./world --stage 5 --output stage5.svg
  python tools/render_contract_interior_world.py ./world --stage 5 --format png
"""

from __future__ import annotations

import argparse
import binascii
import gzip
import html
import math
import struct
import zlib
from pathlib import Path
from typing import Any, BinaryIO


AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:barrier"}

GALLERY_BASE_X = -8192
GALLERY_BASE_Z = -4096
GALLERY_STAGE_SPACING = 144
GALLERY_ORIGIN_Y = 80
GALLERY_STAGE_RADII = {
    1: 12,
    2: 20,
    3: 30,
    4: 40,
    5: 52,
}
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
    "minecraft:bamboo": "#7fa64b",
    "minecraft:moss_carpet": "#679b52",
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


def hex_rgb(hex_color: str) -> tuple[int, int, int]:
    value = hex_color.lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


class Raster:
    def __init__(self, width: int, height: int, background: str = "#f8f6f1"):
        self.width = width
        self.height = height
        bg = hex_rgb(background)
        self.pixels = bytearray(bg * (width * height))

    def set_pixel(self, x: int, y: int, color: tuple[int, int, int]) -> None:
        if x < 0 or y < 0 or x >= self.width or y >= self.height:
            return
        index = (y * self.width + x) * 3
        self.pixels[index : index + 3] = bytes(color)

    def fill_rect(self, x0: int, y0: int, x1: int, y1: int, color: str) -> None:
        rgb = hex_rgb(color)
        for y in range(max(0, y0), min(self.height, y1)):
            for x in range(max(0, x0), min(self.width, x1)):
                self.set_pixel(x, y, rgb)

    def fill_polygon(self, points: list[tuple[float, float]], color: str) -> None:
        if len(points) < 3:
            return

        rgb = hex_rgb(color)
        min_y = max(0, math.floor(min(y for _, y in points)))
        max_y = min(self.height - 1, math.ceil(max(y for _, y in points)))

        for py in range(min_y, max_y + 1):
            scan_y = py + 0.5
            intersections: list[float] = []
            for index, (x1, y1) in enumerate(points):
                x2, y2 = points[(index + 1) % len(points)]
                if y1 == y2:
                    continue
                if not (min(y1, y2) <= scan_y < max(y1, y2)):
                    continue
                ratio = (scan_y - y1) / (y2 - y1)
                intersections.append(x1 + ratio * (x2 - x1))

            intersections.sort()
            for idx in range(0, len(intersections) - 1, 2):
                start = max(0, math.floor(intersections[idx]))
                end = min(self.width - 1, math.ceil(intersections[idx + 1]))
                for px in range(start, end + 1):
                    self.set_pixel(px, py, rgb)

    def write_png(self, path: Path) -> None:
        def chunk(kind: bytes, payload: bytes) -> bytes:
            checksum = binascii.crc32(kind)
            checksum = binascii.crc32(payload, checksum) & 0xFFFFFFFF
            return (
                struct.pack(">I", len(payload))
                + kind
                + payload
                + struct.pack(">I", checksum)
            )

        scanlines = bytearray()
        row_size = self.width * 3
        for y in range(self.height):
            scanlines.append(0)
            start = y * row_size
            scanlines.extend(self.pixels[start : start + row_size])

        payload = bytearray(b"\x89PNG\r\n\x1a\n")
        payload.extend(
            chunk(
                b"IHDR",
                struct.pack(">IIBBBBB", self.width, self.height, 8, 2, 0, 0, 0),
            )
        )
        payload.extend(chunk(b"IDAT", zlib.compress(bytes(scanlines), level=9)))
        payload.extend(chunk(b"IEND", b""))
        path.write_bytes(bytes(payload))


def block_visual_shape(name: str) -> tuple[float, float]:
    """Return approximate horizontal footprint and height for PNG previews."""
    if name.endswith("_carpet"):
        return 0.96, 0.08
    if "petal" in name:
        return 0.58, 0.06
    if name == "minecraft:lily_pad":
        return 0.82, 0.04
    if name == "minecraft:bamboo":
        return 0.24, 1.0
    if name.endswith("_fence"):
        return 0.30, 1.0
    if name.endswith("_wall"):
        return 0.46, 1.0
    if name == "minecraft:lantern":
        return 0.44, 0.72
    if name.endswith("_slab"):
        return 1.0, 0.5
    return 1.0, 1.0


def is_reduced_footprint(name: str) -> bool:
    width, _height = block_visual_shape(name)
    return width < 0.99


def render_top_png(
    blocks: dict[tuple[int, int, int], str],
    output: Path,
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
    margin = 16
    width = (max_x - min_x + 1) * pixel + margin * 2
    height = (max_z - min_z + 1) * pixel + margin * 2
    raster = Raster(width, height)

    for (x, z), (y, name) in top.items():
        px = margin + (x - min_x) * pixel
        py = margin + (z - min_z) * pixel
        width_factor, _height_factor = block_visual_shape(name)

        if width_factor < 0.99:
            below = blocks.get((x, y - 1, z))
            if below is not None:
                raster.fill_rect(
                    px,
                    py,
                    px + pixel,
                    py + pixel,
                    block_color(below),
                )

            inset = max(1, round(pixel * (1.0 - width_factor) / 2.0))
            raster.fill_rect(
                px + inset,
                py + inset,
                px + pixel - inset,
                py + pixel - inset,
                block_color(name),
            )
        else:
            raster.fill_rect(
                px,
                py,
                px + pixel,
                py + pixel,
                block_color(name),
            )

    raster.write_png(output)


def render_iso_png(
    blocks: dict[tuple[int, int, int], str],
    output: Path,
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

    margin = 36
    width = math.ceil(max_sx - min_sx + tile_width * 4 + margin * 2)
    height = math.ceil(max_sy - min_sy + block_height * 4 + margin * 2)
    ox = -min_sx + margin + tile_width * 2
    oy = -min_sy + margin
    raster = Raster(width, height)

    occupied = set(blocks)
    ordered = sorted(coords, key=lambda pos: (pos[0] + pos[2], pos[1], pos[0] - pos[2]))

    for x, y, z in ordered:
        name = blocks[(x, y, z)]
        base = block_color(name)
        sx, sy = project(x, y, z)
        sx += ox
        sy += oy

        width_factor, height_factor = block_visual_shape(name)
        tw = tile_width * width_factor
        th = tile_height * width_factor
        bh = block_height * height_factor

        top_visible = (x, y + 1, z) not in occupied
        if width_factor < 0.99:
            left_visible = True
            right_visible = True
        else:
            left_visible = (x - 1, y, z) not in occupied
            right_visible = (x, y, z - 1) not in occupied

        top = [
            (sx, sy - bh),
            (sx + tw / 2, sy - bh + th / 2),
            (sx, sy - bh + th),
            (sx - tw / 2, sy - bh + th / 2),
        ]
        left = [
            (sx - tw / 2, sy - bh + th / 2),
            (sx, sy - bh + th),
            (sx, sy + th),
            (sx - tw / 2, sy + th / 2),
        ]
        right = [
            (sx, sy - bh + th),
            (sx + tw / 2, sy - bh + th / 2),
            (sx + tw / 2, sy + th / 2),
            (sx, sy + th),
        ]

        if left_visible and height_factor > 0.05:
            raster.fill_polygon(left, shade(base, 0.72))
        if right_visible and height_factor > 0.05:
            raster.fill_polygon(right, shade(base, 0.58))
        if top_visible:
            raster.fill_polygon(top, base)

    raster.write_png(output)


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


def gallery_origin(stage: int) -> tuple[int, int]:
    return (
        GALLERY_BASE_X + (stage - 1) * GALLERY_STAGE_SPACING,
        GALLERY_BASE_Z,
    )


def rotate_blocks(
    blocks: dict[tuple[int, int, int], str],
    center_x: int,
    center_z: int,
    rotation: int,
) -> dict[tuple[int, int, int], str]:
    if rotation == 0:
        return blocks

    rotated: dict[tuple[int, int, int], str] = {}
    for (x, y, z), name in blocks.items():
        dx = x - center_x
        dz = z - center_z
        if rotation == 90:
            rx, rz = -dz, dx
        elif rotation == 180:
            rx, rz = -dx, -dz
        elif rotation == 270:
            rx, rz = dz, -dx
        else:
            raise ValueError(f"unsupported rotation {rotation}")
        rotated[(center_x + rx, y, center_z + rz)] = name
    return rotated


def gallery_bounds(
    stage: int,
    radius: int | None,
) -> tuple[int, int, int, int]:
    origin_x, origin_z = gallery_origin(stage)
    effective_radius = (
        radius
        if radius is not None
        else GALLERY_STAGE_RADII[stage] + 2
    )
    return (
        origin_x - effective_radius,
        origin_x + effective_radius,
        origin_z - effective_radius,
        origin_z + effective_radius,
    )


def centered_bounds(
    center_x: int,
    center_z: int,
    radius: int,
) -> tuple[int, int, int, int]:
    return (
        center_x - radius,
        center_x + radius,
        center_z - radius,
        center_z + radius,
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Render Contract Blade's contract-interior Anvil data offline."
    )
    parser.add_argument("world", type=Path, help="Minecraft Java world folder (contains level.dat)")
    parser.add_argument("--stage", type=int, choices=range(1, 6), default=5)
    parser.add_argument("--mode", choices=("iso", "top"), default="iso")
    parser.add_argument(
        "--rotation",
        type=int,
        choices=(0, 90, 180, 270),
        default=0,
        help="rotate around the selected gallery or explicit center before rendering",
    )
    parser.add_argument("--format", choices=("svg", "png"), default=None)
    parser.add_argument("--output", type=Path)
    parser.add_argument(
        "--center-x",
        type=int,
        default=None,
        help="render around an explicit world X instead of the built-in gallery stage origin",
    )
    parser.add_argument(
        "--center-z",
        type=int,
        default=None,
        help="render around an explicit world Z instead of the built-in gallery stage origin",
    )
    parser.add_argument(
        "--radius",
        type=int,
        default=None,
        help="override crop radius; default is stage radius + 2 blocks",
    )
    parser.add_argument("--y-min", type=int, default=DEFAULT_Y_MIN)
    parser.add_argument("--y-max", type=int, default=DEFAULT_Y_MAX)
    args = parser.parse_args()

    world = args.world.expanduser().resolve()
    dimension = dimension_path(world)

    explicit_center = args.center_x is not None or args.center_z is not None
    if explicit_center and (args.center_x is None or args.center_z is None):
        parser.error("--center-x and --center-z must be supplied together")

    if explicit_center:
        center_x = args.center_x
        center_z = args.center_z
        effective_radius = (
            args.radius
            if args.radius is not None
            else GALLERY_STAGE_RADII[args.stage] + 2
        )
        min_x, max_x, min_z, max_z = centered_bounds(
            center_x,
            center_z,
            effective_radius,
        )
    else:
        center_x, center_z = gallery_origin(args.stage)
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
    blocks = rotate_blocks(blocks, center_x, center_z, args.rotation)

    output = args.output
    output_format = args.format
    if output_format is None and output is not None:
        suffix = output.suffix.lower().lstrip(".")
        if suffix in {"svg", "png"}:
            output_format = suffix
    if output_format is None:
        output_format = "svg"

    if output is None:
        rotation_suffix = "" if args.rotation == 0 else f"_r{args.rotation}"
        output = Path(
            f"contract_interior_stage_{args.stage}_{args.mode}"
            f"{rotation_suffix}.{output_format}"
        )
    output = output.expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)

    title = (
        f"Contract Interior Stage {args.stage} — "
        f"offline {args.mode} render from Anvil data — {args.rotation}°"
    )
    if output_format == "png":
        if args.mode == "top":
            render_top_png(blocks, output)
        else:
            render_iso_png(blocks, output)
    elif args.mode == "top":
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
