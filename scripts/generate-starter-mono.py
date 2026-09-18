#!/usr/bin/env python3

import argparse
import hashlib
from datetime import datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory

from fontTools import subset
from fontTools.merge import Merger
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from otf2ttf.cli import otf_to_ttf


JETBRAINS_MONO_REGULAR_SHA256 = "a0bf60ef0f83c5ed4d7a75d45838548b1f6873372dfac88f71804491898d138f"
JETBRAINS_MONO_SEMIBOLD_SHA256 = "1b3bfa1ed5665a4ce3f9feb68d2d4e40e70bf8b4b7d9a3edd418f321b4e166a0"
NOTO_CJK_REGULAR_SHA256 = "ec04cc376b34887cedbdf84074e2e226ed2761eeabdcb9173fc1dd7bfd153ef7"
NOTO_CJK_VARIABLE_SHA256 = "9fe57a71eb48e50cccb77903123d08857edefcf289c7b309462c4c3d82208126"
MAC_EPOCH_OFFSET = 2_082_844_800
OUTPUT_TIMESTAMP = int(datetime(2026, 9, 15, tzinfo=timezone.utc).timestamp()) + MAC_EPOCH_OFFSET
FONT_VERSION = "1.003"

CJK_RANGES = (
    (0x2E80, 0x2FFF),
    (0x3000, 0x303F),
    (0x3100, 0x312F),
    (0x31A0, 0x31EF),
    (0x3200, 0x33FF),
    (0x3400, 0x4DBF),
    (0x4E00, 0x9FFF),
    (0xF900, 0xFAFF),
    (0xFE10, 0xFE1F),
    (0xFE30, 0xFE4F),
    (0xFF00, 0xFFEF),
    (0x20000, 0x2FA1F),
    (0x30000, 0x323AF),
)

def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="合并 JetBrains Mono 与 Noto Sans Mono CJK SC 中文字形.",
    )
    parser.add_argument("--jetbrains-mono", required=True, type=Path)
    parser.add_argument("--noto-cjk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--weight", choices=(400, 600), default=400, type=int)
    return parser.parse_args()


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_source(path: Path, expected_sha256: str, family: str, version: str) -> TTFont:
    actual_sha256 = file_sha256(path)
    if actual_sha256 != expected_sha256:
        raise ValueError(f"Unexpected source hash for {path}: {actual_sha256}")

    font = TTFont(path, recalcTimestamp=False)
    family_names = {
        record.toUnicode()
        for record in font["name"].names
        if record.nameID in (1, 16)
    }
    version_names = {
        record.toUnicode()
        for record in font["name"].names
        if record.nameID == 5
    }
    if family not in family_names:
        raise ValueError(f"Unexpected font family for {path}: {sorted(family_names)}")
    if not any(version in value for value in version_names):
        raise ValueError(f"Unexpected font version for {path}: {sorted(version_names)}")
    return font


def is_cjk_code_point(code_point: int) -> bool:
    return any(start <= code_point <= end for start, end in CJK_RANGES)


def remove_cjk_mappings(font: TTFont) -> None:
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap = {
                code_point: glyph_name
                for code_point, glyph_name in table.cmap.items()
                if not is_cjk_code_point(code_point)
            }


def subset_noto_cjk(font: TTFont) -> set[int]:
    selected_code_points = {
        code_point
        for code_point in font.getBestCmap()
        if is_cjk_code_point(code_point)
    }
    options = subset.Options()
    options.layout_features = []
    options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]
    options.name_languages = [0x0409]
    options.notdef_glyph = True
    options.notdef_outline = True
    options.recommended_glyphs = True
    options.glyph_names = True

    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=selected_code_points)
    subsetter.subset(font)
    for table_name in ("BASE", "DSIG", "GDEF", "GPOS", "GSUB", "VORG", "vhea", "vmtx"):
        if table_name in font:
            del font[table_name]
    return selected_code_points


def source_hashes(weight: int) -> tuple[str, str]:
    if weight == 400:
        return JETBRAINS_MONO_REGULAR_SHA256, NOTO_CJK_REGULAR_SHA256
    return JETBRAINS_MONO_SEMIBOLD_SHA256, NOTO_CJK_VARIABLE_SHA256


def instantiate_weight(font: TTFont, weight: int) -> TTFont:
    if "fvar" in font:
        weight_axes = [axis for axis in font["fvar"].axes if axis.axisTag == "wght"]
        if len(weight_axes) != 1:
            raise ValueError("Variable source must provide exactly one wght axis")
        weight_axis = weight_axes[0]
        if not weight_axis.minValue <= weight <= weight_axis.maxValue:
            raise ValueError(
                f"Weight {weight} is outside the source range "
                f"{weight_axis.minValue}..{weight_axis.maxValue}"
            )
        font = instantiateVariableFont(font, {"wght": weight}, inplace=False)

    actual_weight = font["OS/2"].usWeightClass
    if actual_weight != weight:
        raise ValueError(f"Expected weight {weight}, found {actual_weight}")
    return font


def font_names(weight: int) -> dict[int, str]:
    style_name = "Regular" if weight == 400 else "SemiBold"
    postscript_name = f"StarterMono-{style_name}"
    return {
        0: (
            "Copyright 2020 The JetBrains Mono Project Authors; "
            "Copyright 2014-2021 Adobe."
        ),
        1: "Starter Mono",
        2: style_name,
        3: f"{FONT_VERSION};STARTER;{postscript_name}",
        4: f"Starter Mono {style_name}",
        5: f"Version {FONT_VERSION}",
        6: postscript_name,
        13: (
            "This Font Software is licensed under the SIL Open Font License, "
            "Version 1.1."
        ),
        14: "https://openfontlicense.org",
        16: "Starter Mono",
        17: style_name,
    }


def replace_names(font: TTFont, weight: int) -> None:
    name_table = font["name"]
    name_table.names = []
    for name_id, value in font_names(weight).items():
        name_table.setName(value, name_id, 3, 1, 0x0409)
        name_table.setName(value, name_id, 1, 0, 0)


def finalize_font(font: TTFont, weight: int) -> None:
    replace_names(font, weight)
    font["head"].created = OUTPUT_TIMESTAMP
    font["head"].modified = OUTPUT_TIMESTAMP
    font["OS/2"].achVendID = "STRT"
    font["OS/2"].fsType = 0
    font["OS/2"].usWeightClass = weight
    font["post"].formatType = 3.0
    font["post"].isFixedPitch = 0
    font["hhea"].recalc(font)
    font["OS/2"].recalcUnicodeRanges(font)
    font.recalcTimestamp = False


def generate_font(
    jetbrains_path: Path,
    noto_path: Path,
    output_path: Path,
    weight: int,
) -> None:
    jetbrains_sha256, noto_sha256 = source_hashes(weight)
    jetbrains_font = instantiate_weight(
        verify_source(
            jetbrains_path,
            jetbrains_sha256,
            "JetBrains Mono",
            "Version 2.304",
        ),
        weight,
    )
    noto_font = instantiate_weight(
        verify_source(
            noto_path,
            noto_sha256,
            "Noto Sans Mono CJK SC",
            "Version 2.004",
        ),
        weight,
    )
    if jetbrains_font["head"].unitsPerEm != noto_font["head"].unitsPerEm:
        raise ValueError("Source fonts must use the same units per em")

    jetbrains_cmap = jetbrains_font.getBestCmap()
    noto_cmap = noto_font.getBestCmap()
    latin_advance = jetbrains_font["hmtx"].metrics[jetbrains_cmap[ord("0")]][0]
    cjk_advance = noto_font["hmtx"].metrics[noto_cmap[ord("中")]][0]

    remove_cjk_mappings(jetbrains_font)
    selected_code_points = subset_noto_cjk(noto_font)
    if "CFF " in noto_font or "CFF2" in noto_font:
        otf_to_ttf(noto_font, post_format=3.0)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix="starter-mono-") as temporary_directory:
        temporary_path = Path(temporary_directory)
        jetbrains_subset_path = temporary_path / "jetbrains-mono.ttf"
        noto_subset_path = temporary_path / "noto-cjk.ttf"
        jetbrains_font.save(jetbrains_subset_path)
        noto_font.save(noto_subset_path)

        merged_font = Merger().merge([jetbrains_subset_path, noto_subset_path])
        finalize_font(merged_font, weight)
        merged_font.save(output_path)

    print(
        f"Generated weight {weight} at {output_path} with "
        f"{len(selected_code_points)} CJK code points, "
        f"{latin_advance}-unit Latin width and {cjk_advance}-unit CJK width."
    )


def main() -> None:
    arguments = parse_arguments()
    generate_font(
        arguments.jetbrains_mono,
        arguments.noto_cjk,
        arguments.output,
        arguments.weight,
    )


if __name__ == "__main__":
    main()
