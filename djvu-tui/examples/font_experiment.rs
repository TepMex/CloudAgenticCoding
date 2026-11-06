//! Headless timing and PUA output for experimental fonts.
use djvu_rs::{
    Document,
    djvu_render::{RenderOptions, RenderRequest},
};
use djvu_tui::{
    experimental_glyphs::{glyph, masks},
    raster::{GrayImage, Symbols, render},
};
use std::{io::Write, time::Instant};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<_> = std::env::args().collect();
    if args.len() != 4 {
        return Err("usage: font_experiment FILE.djvu PAGE(1-based) braille|pixels|contour".into());
    }
    let page_no: usize = args[2].parse()?;
    let mode = &args[3];
    let (cols, rows) = (160u32, 49u32);
    let (w, h) = match mode.as_str() {
        "braille" => (cols * 2, rows * 4),
        "pixels" => (cols * 3, rows * 5),
        "contour" => (cols * 2 + 1, rows * 4 + 1),
        _ => return Err("unknown mode".into()),
    };
    let start = Instant::now();
    let doc = Document::from_bytes(std::fs::read(&args[1])?)?;
    let page = doc
        .inner()
        .page(page_no.checked_sub(1).ok_or("page must be positive")?)?;
    let pixmap = RenderRequest::new(RenderOptions {
        width: w,
        height: h,
        ..Default::default()
    })
    .pixmap(page)?;
    let image = GrayImage {
        width: pixmap.width,
        height: pixmap.height,
        pixels: pixmap
            .data
            .chunks_exact(4)
            .map(|p| {
                ((u32::from(p[0]) * 54 + u32::from(p[1]) * 183 + u32::from(p[2]) * 19) >> 8) as u8
            })
            .collect(),
    };
    let threshold = image.threshold();
    let decode = start.elapsed();
    let start = Instant::now();
    let chars = if mode == "braille" {
        render(&image, Symbols::Braille, threshold, false, (0, 0)).cells
    } else {
        masks(&image, cols, rows, mode == "contour", threshold)
            .into_iter()
            .map(glyph)
            .collect()
    };
    let symbols = start.elapsed();
    let start = Instant::now();
    let mut output = String::new();
    for row in chars.chunks(cols as usize) {
        output.extend(row);
        output.push('\n');
    }
    std::io::stdout().write_all(output.as_bytes())?;
    eprintln!(
        "mode={mode} page={page_no} decode_ms={:.3} symbols_ms={:.3} encode_write_ms={:.3}",
        decode.as_secs_f64() * 1000.,
        symbols.as_secs_f64() * 1000.,
        start.elapsed().as_secs_f64() * 1000.
    );
    Ok(())
}
