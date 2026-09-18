# Starter Mono

`Starter Mono` 是 Android Starter 使用的衍生字体族. 它以 JetBrains Mono 2.304 Regular / SemiBold 为基础,
补充 Noto Sans Mono CJK SC 2.004 对应字重的 CJK 字形, 并使用新的字体族名称避免将衍生字体误标为任一上游字体.

## 来源

| 来源 | 版本 | SHA-256 |
| --- | --- | --- |
| [JetBrains Mono Regular](https://github.com/JetBrains/JetBrainsMono/blob/v2.304/fonts/ttf/JetBrainsMono-Regular.ttf) | 2.304 | `a0bf60ef0f83c5ed4d7a75d45838548b1f6873372dfac88f71804491898d138f` |
| [JetBrains Mono SemiBold](https://github.com/JetBrains/JetBrainsMono/blob/v2.304/fonts/ttf/JetBrainsMono-SemiBold.ttf) | 2.304 | `1b3bfa1ed5665a4ce3f9feb68d2d4e40e70bf8b4b7d9a3edd418f321b4e166a0` |
| [Noto Sans Mono CJK SC Regular](https://github.com/notofonts/noto-cjk/blob/Sans2.004/Sans/Mono/NotoSansMonoCJKsc-Regular.otf) | 2.004 | `ec04cc376b34887cedbdf84074e2e226ed2761eeabdcb9173fc1dd7bfd153ef7` |
| [Noto Sans Mono CJK SC Variable](https://github.com/notofonts/noto-cjk/blob/Sans2.004/Sans/Variable/TTF/Mono/NotoSansMonoCJKsc-VF.ttf) | 2.004 | `9fe57a71eb48e50cccb77903123d08857edefcf289c7b309462c4c3d82208126` |

JetBrains Mono 的版权归 The JetBrains Mono Project Authors 所有. 作者列表保存在
[`JETBRAINS_MONO_AUTHORS.txt`](JETBRAINS_MONO_AUTHORS.txt). Noto Sans Mono CJK SC 的字体内版权声明为
`Copyright 2014-2021 Adobe`.

## 修改

生成器 [`scripts/generate-starter-mono.py`](../../scripts/generate-starter-mono.py) 执行以下修改:

* 保留 JetBrains Mono 的非 CJK 字符、字距和 OpenType 编程连字.
* 从 Noto Sans Mono CJK SC 提取已编码的 CJK 字符, 去除竖排和区域化替换表, 使用 SC 默认字形;
  SemiBold 从可变字体固定为 `wght=600` 后再提取.
* 必要时将 Noto 的 CFF 轮廓转换为 TrueType 轮廓, 不改变两套上游字体的轮廓或横向度量.
* JetBrains Mono 保持 600 units advance width, Noto CJK 保持 1000 units advance width;
  普通 UI 混排优先保持原始字形与自然间距, 不强制 `2:1` 字符网格.
* 将字体元数据标记为混合字宽, 不对字体选择器声明严格等宽.
* 将衍生字体族重命名为 `Starter Mono`, 提供原生 Regular 400 和 SemiBold 600, 字体版本为 `1.003`,
  vendor ID 为 `STRT`.

生成文件及 SHA-256:

| 输出 | 字重 | SHA-256 |
| --- | --- | --- |
| `foundation_starter_mono_regular.ttf` | 400 | `528d006cc9b9a9b210c72154c4dca93c55764c64f37b20bfc3e580e85ae6b9a8` |
| `foundation_starter_mono_semibold.ttf` | 600 | `d4c21d4cff98ccc673158c224d6f8975eae13b378b43d4dd83dd690234ddd9f7` |

## 重新生成

生成器需要 Python 3.10 以上版本及固定版本的 FontTools、otf2ttf、cu2qu 和 Brotli. 工具只需安装到临时目录,
不进入 Android 运行时依赖:

```bash
starter_mono_tools="$(mktemp -d)"
python3 -m pip install --target "$starter_mono_tools" \
  fonttools==4.59.2 otf2ttf==0.2 cu2qu==1.6.7.post2 brotli==1.1.0
PYTHONPATH="$starter_mono_tools" python3 scripts/generate-starter-mono.py \
  --jetbrains-mono /path/to/JetBrainsMono-Regular.ttf \
  --noto-cjk /path/to/NotoSansMonoCJKsc-Regular.otf \
  --output foundation/src/main/res/font/foundation_starter_mono_regular.ttf \
  --weight 400
PYTHONPATH="$starter_mono_tools" python3 scripts/generate-starter-mono.py \
  --jetbrains-mono /path/to/JetBrainsMono-SemiBold.ttf \
  --noto-cjk /path/to/NotoSansMonoCJKsc-VF.ttf \
  --output foundation/src/main/res/font/foundation_starter_mono_semibold.ttf \
  --weight 600
```

脚本会根据 `--weight` 校验两份源文件的 SHA-256、版本和真实字重; 版本或内容不匹配时不会生成输出.

## 许可

JetBrains Mono 和 Noto Sans Mono CJK SC 均使用 SIL Open Font License 1.1. 该许可允许合并、修改、嵌入和
随应用分发, 但字体及其组成部分不得单独销售, 衍生字体仍须使用 OFL 1.1. `Starter Mono` 及其生成文件继续
使用 SIL OFL 1.1.

完整许可文本见 [`OFL.txt`](OFL.txt) 和 [`NOTO_CJK_OFL.txt`](NOTO_CJK_OFL.txt).
独立 APK 也会通过
[`foundation_starter_mono_ofl.txt`](../../foundation/src/main/res/raw/foundation_starter_mono_ofl.txt)
携带两套上游字体的版权声明和完整 OFL 1.1 文本.
