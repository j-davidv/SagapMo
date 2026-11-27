"""Refresh the bundled OpenFreeMap campus snapshot, or validate it without network access."""

import argparse
import datetime
import gzip
import hashlib
import json
import math
from pathlib import Path
import re
import tempfile
from urllib.parse import quote
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "app/src/main/assets/campus-map"
PREFIX = "sagapmo://campus/"
BOUNDS = [121.055, 13.765, 121.095, 13.805]
MIN_ZOOM, MAX_ZOOM = 12, 14
HOST = "https://tiles.openfreemap.org"
LICENSES = {
    "OpenFreeMap.md": "https://raw.githubusercontent.com/hyperknot/openfreemap/main/LICENSE.md",
    "OpenMapTiles.md": "https://raw.githubusercontent.com/openmaptiles/openmaptiles/master/LICENSE.md",
    "Bright.md": "https://raw.githubusercontent.com/openmaptiles/osm-bright-gl-style/master/LICENSE.md",
    "Liberty.md": "https://raw.githubusercontent.com/maputnik/osm-liberty/gh-pages/LICENSE.md",
    "NotoSans.txt": "https://raw.githubusercontent.com/notofonts/latin-greek-cyrillic/main/OFL.txt",
    "Maki.txt": "https://raw.githubusercontent.com/mapbox/maki/main/LICENSE.txt",
}


def tile_coordinates(bounds, zoom):
    west, south, east, north = bounds
    scale = 2 ** zoom

    def x(lon):
        return math.floor((lon + 180) / 360 * scale)

    def y(lat):
        return math.floor((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * scale)

    return [(zoom, tx, ty) for tx in range(x(west), x(east) + 1) for ty in range(y(north), y(south) + 1)]


def varint(data, offset):
    result, shift = 0, 0
    while True:
        value = data[offset]
        offset += 1
        result |= (value & 127) << shift
        if value < 128:
            return result, offset
        shift += 7


def fields(data):
    """Read the protobuf wire fields needed for vector-tile label properties."""
    offset = 0
    while offset < len(data):
        tag, offset = varint(data, offset)
        wire = tag & 7
        if wire == 0:
            value, offset = varint(data, offset)
        elif wire == 2:
            size, offset = varint(data, offset)
            value = data[offset:offset + size]
            offset += size
        elif wire in (1, 5):
            size = 8 if wire == 1 else 4
            value = data[offset:offset + size]
            offset += size
        else:
            raise ValueError(f"Unsupported protobuf wire type {wire}")
        yield tag >> 3, value


def label_characters(tile, property_names):
    characters = set()
    for number, layer in fields(tile):
        if number != 3:
            continue
        layer_fields = list(fields(layer))
        keys = [value.decode() for field, value in layer_fields if field == 3]
        values = [next((v.decode() for f, v in fields(value) if f == 1), "")
                  for field, value in layer_fields if field == 4]
        for field, feature in layer_fields:
            if field != 2:
                continue
            for field, packed in fields(feature):
                if field != 2:
                    continue
                tags, offset = [], 0
                while offset < len(packed):
                    tag, offset = varint(packed, offset)
                    tags.append(tag)
                for key, value in zip(tags[::2], tags[1::2]):
                    if keys[key] in property_names:
                        characters.update(values[value])
    return characters


def text_properties(expression):
    if isinstance(expression, list):
        if len(expression) > 1 and expression[0] == "get" and isinstance(expression[1], str):
            yield expression[1]
        for child in expression:
            yield from text_properties(child)
    elif isinstance(expression, str):
        yield from re.findall(r"\{([^{}]+)\}", expression)


def check(directory=BUNDLE):
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    entries = manifest["files"]
    for name, metadata in entries.items():
        data = (directory / name).read_bytes()
        assert len(data) == metadata["bytes"], f"Wrong size: {name}"
        assert hashlib.sha256(data).hexdigest() == metadata["sha256"], f"Wrong hash: {name}"
    for style_name in ("bright", "liberty"):
        style = json.loads((directory / f"styles/{style_name}.json").read_text())
        assert style["version"] == 8
        assert style["glyphs"].startswith(PREFIX)
        assert style["sprite"].startswith(PREFIX)
        for suffix in (".json", ".png", "@2x.json", "@2x.png"):
            assert style["sprite"].removeprefix(PREFIX) + suffix in entries
        for source in style["sources"].values():
            assert "url" not in source, "A remote TileJSON dependency remains"
            assert source["bounds"] == manifest["bounds"]
            assert source["attribution"]
            assert source["minzoom"] == manifest["minZoom"]
            assert source["maxzoom"] == manifest["maxZoom"]
            for zoom in range(source["minzoom"], source["maxzoom"] + 1):
                for z, x, y in tile_coordinates(source["bounds"], zoom):
                    url = source["tiles"][0].format(z=z, x=x, y=y)
                    assert url.startswith(PREFIX)
                    assert url.removeprefix(PREFIX) in entries, f"Missing tile: {url}"
        for font in manifest["fonts"]:
            for glyph_range in manifest["glyphRanges"]:
                assert f"fonts/{font}/{glyph_range}.pbf" in entries
    total = sum(item["bytes"] for item in entries.values())
    print(f"Verified {len(entries)} bundled resources, {total / 1024 / 1024:.2f} MiB, no remote map dependencies.")


def refresh(directory):
    entries = {}

    def save(path, data, url=None):
        target = directory / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        entries[path] = {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
        if url:
            entries[path]["source"] = url

    def download(url, path=None):
        request = Request(url, headers={"User-Agent": "SagapMo-campus-map-bundler/1.0"})
        with urlopen(request, timeout=60) as response:
            data = response.read()
        if data.startswith(b"\x1f\x8b"):
            data = gzip.decompress(data)
        if path:
            save(path, data, url)
        return data

    styles = {name: json.loads(download(f"{HOST}/styles/{name}")) for name in ("bright", "liberty")}
    tilejson = json.loads(download(styles["bright"]["sources"]["openmaptiles"]["url"]))
    assert tilejson["maxzoom"] >= MAX_ZOOM
    fonts, properties = set(), set()
    for style in styles.values():
        for layer in style["layers"]:
            layout = layer.get("layout", {})
            if "text-font" in layout:
                fonts.add(",".join(layout["text-font"]))
            properties.update(text_properties(layout.get("text-field", "")))
    characters = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
    for zoom in range(MIN_ZOOM, MAX_ZOOM + 1):
        for z, x, y in tile_coordinates(BOUNDS, zoom):
            path = f"tiles/{z}/{x}/{y}.pbf"
            data = download(tilejson["tiles"][0].format(z=z, x=x, y=y), path)
            characters.update(label_characters(data, properties))
    ranges = sorted({0, 256, 8192} | {ord(char) // 256 * 256 for char in characters if ord(char) <= 65535})
    glyph_ranges = [f"{start}-{start + 255}" for start in ranges]
    for font in sorted(fonts):
        for glyph_range in glyph_ranges:
            path = f"fonts/{font}/{glyph_range}.pbf"
            download(f"{HOST}/fonts/{quote(font, safe=',')}/{glyph_range}.pbf", path)
    sprite = styles["bright"]["sprite"]
    assert all(style["sprite"] == sprite for style in styles.values())
    for suffix in (".json", ".png", "@2x.json", "@2x.png"):
        download(sprite + suffix, "sprites/ofm" + suffix)
    for name, style in styles.items():
        # The global relief raster appears below zoom 6, outside this campus snapshot.
        style["layers"] = [layer for layer in style["layers"] if layer.get("source") != "ne2_shaded"]
        style["sources"] = {"openmaptiles": {
            "type": "vector", "tiles": [PREFIX + "tiles/{z}/{x}/{y}.pbf"],
            "bounds": BOUNDS, "minzoom": MIN_ZOOM, "maxzoom": MAX_ZOOM,
            "attribution": tilejson["attribution"],
        }}
        style["glyphs"] = PREFIX + "fonts/{fontstack}/{range}.pbf"
        style["sprite"] = PREFIX + "sprites/ofm"
        save(f"styles/{name}.json", json.dumps(style, ensure_ascii=False, separators=(",", ":")).encode(), f"{HOST}/styles/{name}")
    for name, url in LICENSES.items():
        download(url, f"licenses/{name}")
    save("NOTICE.txt", (
        "SagapMo campus map snapshot\n"
        "Map data: OpenStreetMap contributors, Open Database License (ODbL) 1.0.\n"
        "https://www.openstreetmap.org/copyright\n"
        "https://opendatacommons.org/licenses/odbl/1-0/\n"
        "Tiles: OpenFreeMap / OpenMapTiles. Styles: Bright and Liberty, BSD-3-Clause code / CC BY 4.0 design.\n"
        "Styles modified to reference bundled campus resources and omit global relief.\n"
        "Noto Sans: SIL Open Font License 1.1. Maki icons: CC0. See licenses/.\n"
    ).encode())
    manifest = {
        "created": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "bounds": BOUNDS, "minZoom": MIN_ZOOM, "maxZoom": MAX_ZOOM,
        "tileSource": tilejson["tiles"][0], "fonts": sorted(fonts), "glyphRanges": glyph_ranges,
        "files": dict(sorted(entries.items())),
    }
    (directory / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    check(directory)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true", help="Download a new campus snapshot from OpenFreeMap")
    arguments = parser.parse_args()
    if arguments.refresh:
        # Publish only after every resource downloads and the complete snapshot validates.
        with tempfile.TemporaryDirectory(prefix="sagapmo-campus-map-") as staging:
            prepared = Path(staging)
            refresh(prepared)
            for resource in prepared.rglob("*"):
                if resource.is_file():
                    destination = BUNDLE / resource.relative_to(prepared)
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(resource.read_bytes())
    else:
        check()
