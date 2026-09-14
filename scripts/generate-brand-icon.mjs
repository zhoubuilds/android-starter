import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const config = JSON.parse(await readFile(resolve(root, 'design/brand/icon.json'), 'utf8'));
const raster = process.argv.includes('--raster');
const unknownArguments = process.argv.slice(2).filter(argument => argument !== '--raster');
assert.equal(unknownArguments.length, 0, `Unknown arguments: ${unknownArguments.join(', ')}`);

// 栅格导出是可选的设计工具能力, 不进入 Android 依赖或日常 Gradle 构建.
let sharp;
if (raster) {
    const require = createRequire(import.meta.url);
    try {
        sharp = require('sharp');
    } catch (error) {
        throw new Error('Raster export requires sharp 0.35.4. Install it in a tools directory and set NODE_PATH to its node_modules.', { cause: error });
    }
}

const writtenFiles = [];
const tilt = Math.tan(config.tiltDegrees * Math.PI / 180);
const number = value => String(Number(value.toFixed(5)));
const coordinate = point => point.map(number).join(',');

function roundedPolygon(points, radius) {
    const corners = points.map((point, index) => {
        const previous = points[(index + points.length - 1) % points.length];
        const next = points[(index + 1) % points.length];
        const beforeLength = Math.hypot(previous[0] - point[0], previous[1] - point[1]);
        const afterLength = Math.hypot(next[0] - point[0], next[1] - point[1]);
        const distance = Math.min(radius, beforeLength / 2, afterLength / 2);
        return {
            point,
            before: point.map((value, axis) => value + (previous[axis] - value) * distance / beforeLength),
            after: point.map((value, axis) => value + (next[axis] - value) * distance / afterLength),
        };
    });
    const commands = [{ type: 'M', points: [corners[0].after] }];
    for (let index = 1; index <= corners.length; index++) {
        const corner = corners[index % corners.length];
        commands.push({ type: 'L', points: [corner.before] });
        commands.push({ type: 'Q', points: [corner.point, corner.after] });
    }
    commands.push({ type: 'Z', points: [] });
    return commands;
}

function transformPoint(point, half, adaptive = false) {
    const rotated = half
        ? point.map((value, axis) => config.rotationCenter[axis] * 2 - value)
        : point;
    const y = rotated[1] - config.rotationCenter[1];
    const preview = [rotated[0] - config.rotationCenter[0] - tilt * y + config.viewport / 2, y + config.viewport / 2];
    if (!adaptive) return preview;
    const offset = (config.adaptive.viewport - config.viewport * config.adaptive.scale) / 2;
    return preview.map(value => value * config.adaptive.scale + offset);
}

function shape(polygon, radius, half, adaptive = false) {
    const commands = roundedPolygon(polygon, radius);
    return commands.map(command => command.type + command.points
        .map(point => coordinate(transformPoint(point, half, adaptive))).join(' ')).join('');
}

function foreground(adaptive = false, monochrome = false) {
    const layers = [];
    for (let half = 0; half < 2; half++) {
        layers.push({ color: monochrome ? '#000000' : config.colors.foreground, path: shape(config.bodyPolygon, config.bodyRadius, half, adaptive) });
        layers.push({ color: monochrome ? '#000000' : config.colors.accent, path: shape(config.accentPolygon, config.accentRadius, half, adaptive) });
    }
    return layers;
}

function background(round = false, square = false) {
    const size = config.viewport;
    if (square) return `M0,0H${size}V${size}H0Z`;
    if (round) return `M${size},${size / 2}A${size / 2},${size / 2} 0 1,1 0,${size / 2}A${size / 2},${size / 2} 0 1,1 ${size},${size / 2}Z`;
    const radius = config.legacyBackgroundRadius;
    const edge = size - radius;
    return `M${radius},0H${edge}A${radius},${radius} 0 0,1 ${size},${radius}V${edge}A${radius},${radius} 0 0,1 ${edge},${size}H${radius}A${radius},${radius} 0 0,1 0,${edge}V${radius}A${radius},${radius} 0 0,1 ${radius},0Z`;
}

function legacyLayers(round = false, square = false) {
    return [{ color: config.colors.background, path: background(round, square) }, ...foreground()];
}

function svg(layers, viewport = config.viewport) {
    const paths = layers.map(layer => `  <path fill="${layer.color}" d="${layer.path}" />`).join('\n');
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${viewport}" height="${viewport}" viewBox="0 0 ${viewport} ${viewport}">\n${paths}\n</svg>\n`;
}

function vector(layers, viewport, dimension) {
    const paths = layers.map(layer => `    <path\n        android:fillColor="${layer.color}"\n        android:pathData="${layer.path}" />`).join('\n');
    return `<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="${dimension}dp"\n    android:height="${dimension}dp"\n    android:viewportWidth="${viewport}"\n    android:viewportHeight="${viewport}">\n${paths}\n</vector>\n`;
}

async function output(path, contents) {
    const destination = resolve(root, path);
    await mkdir(dirname(destination), { recursive: true });
    await writeFile(destination, contents);
    writtenFiles.push(path);
}

// 控制点也落在安全圆内, 可保证二次曲线及其填充区域不会超出安全圆.
let maxAdaptiveRadius = 0;
for (const polygon of [config.bodyPolygon, config.accentPolygon]) {
    for (let half = 0; half < 2; half++) {
        for (const point of polygon) {
            const transformed = transformPoint(point, half, true);
            const radius = Math.hypot(...transformed.map(value => value - config.adaptive.viewport / 2));
            maxAdaptiveRadius = Math.max(maxAdaptiveRadius, radius);
        }
    }
}
assert.ok(maxAdaptiveRadius <= config.adaptive.safeRadius, 'Adaptive foreground exceeds its safe circle');
const accentBounds = [0, 1].map(half => {
    const values = config.accentPolygon.map(point => transformPoint(point, half)[1]);
    return [Math.min(...values), Math.max(...values)];
});
assert.deepEqual(accentBounds[0], accentBounds[1], 'Accent blocks must share horizontal top and bottom edges');

const regularSvg = svg(legacyLayers());
const roundSvg = svg(legacyLayers(true));
const monochromeLayers = foreground(true, true);
await output('design/brand/icon.svg', regularSvg);
await output('design/brand/icon-round.svg', roundSvg);
await output('design/brand/icon-foreground.svg', svg(foreground(true), config.adaptive.viewport));
await output('design/brand/icon-monochrome.svg', svg(monochromeLayers, config.adaptive.viewport));
await output('app/src/main/res/drawable-v24/ic_launcher_foreground.xml', vector(foreground(true), config.adaptive.viewport, 108));
await output('app/src/main/res/drawable/ic_launcher_monochrome.xml', vector(monochromeLayers, config.adaptive.viewport, 108));
await output('app/src/main/res/drawable/ic_launcher_background.xml', vector([
    { color: config.colors.background, path: 'M0,0H108V108H0Z' },
], 108, 108));

// 传统启动图标使用各密度 WebP; VectorDrawable 只输出到 drawable 目录.
const adaptiveIcon = `<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/ic_launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n`;
await output('app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml', adaptiveIcon);
await output('app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml', adaptiveIcon);

if (raster) {
    for (const [density, size] of Object.entries(config.legacyDensities)) {
        for (const round of [false, true]) {
            const name = round ? 'ic_launcher_round' : 'ic_launcher';
            const contents = await sharp(Buffer.from(round ? roundSvg : regularSvg), { density: 576 })
                .resize(size, size).webp({ lossless: true }).toBuffer();
            await output(`app/src/main/res/mipmap-${density}/${name}.webp`, contents);
        }
    }
    await output('design/brand/icon.png', await sharp(Buffer.from(regularSvg), { density: 576 }).resize(512, 512).png().toBuffer());
    await output('design/brand/icon-play-store.png', await sharp(Buffer.from(svg(legacyLayers(false, true))), { density: 576 })
        .resize(512, 512).removeAlpha().png().toBuffer());
}

console.log(JSON.stringify({
    colors: config.colors,
    accentBounds,
    adaptiveMaxRadius: Number(maxAdaptiveRadius.toFixed(5)),
    adaptiveSafeRadius: config.adaptive.safeRadius,
    raster,
    writtenFiles,
}, null, 2));
