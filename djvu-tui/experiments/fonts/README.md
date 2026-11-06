# Experimental terminal fonts (work in progress)

These fonts encode 15-bit masks at U+F0000..U+F7FFF. They contain no normal UI glyphs; terminal font fallback is required. They do not change djvu-tui's interactive renderer.

Generate and check with Python 3 and `fonttools`:

```sh
python3 -m pip install fonttools
python3 experiments/fonts/generate.py
python3 experiments/fonts/verify.py
```

Output is under `target/experimental-fonts`. `font_experiment` is a preliminary full-page timing/output tool; PAGE is one-based:

```sh
cargo run --release --example font_experiment -- /path/to/book.djvu 1 pixels
```

Modes are `braille`, `pixels`, and `contour`. For PUA output, select the matching generated font in a terminal supporting supplementary private-use characters. The current tool stretches the whole page to 160×49 cells and does not preserve page proportions; it is useful for checking encoding and timings, not visual comparison. Contour cells share raster nodes. This prototype has not yet been validated in Terminal.app: fallback, one-cell width, antialiasing and seams may vary with terminal configuration.
