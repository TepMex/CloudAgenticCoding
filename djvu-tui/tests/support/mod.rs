//! Original geometric test images; no downloaded scans or font assets.
use djvu_rs::{
    Bitmap, Pixmap,
    djvu_encode::{EncodeQuality, PageEncoder},
};

pub fn bitmap(width: u32, height: u32) -> Bitmap {
    let mut bitmap = Bitmap::new(width, height);
    let scale = (width / 100).max(1);
    // Hand-drawn 5x7 patterns spelling "DJVU TUI".
    let glyphs = [
        [30, 17, 17, 17, 17, 17, 30],
        [7, 2, 2, 2, 18, 18, 12],
        [17, 17, 17, 17, 17, 10, 4],
        [17, 17, 17, 17, 17, 17, 14],
        [0; 7],
        [31, 4, 4, 4, 4, 4, 4],
        [17, 17, 17, 17, 17, 17, 14],
        [14, 4, 4, 4, 4, 4, 14],
    ];
    for line in 0..8 {
        let top = (8 + line * 13) * scale;
        for (letter, rows) in glyphs.iter().enumerate() {
            for (y, row) in rows.iter().enumerate() {
                for x in 0..5 {
                    if row & (1 << (4 - x)) != 0 {
                        for dy in 0..scale {
                            for dx in 0..scale {
                                let px = (8 + letter as u32 * 7 + x) * scale + dx;
                                let py = top + y as u32 * scale + dy;
                                if px < width && py < height {
                                    bitmap.set(px, py, true);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    bitmap
}

pub fn bilevel() -> Vec<u8> {
    PageEncoder::from_bitmap(&bitmap(320, 480))
        .encode()
        .unwrap()
}

pub fn colour() -> Vec<u8> {
    let mut image = Pixmap::try_new(320, 240, 255, 255, 255, 255).unwrap();
    for y in 16..224 {
        for x in 16..304 {
            let inside = (x as i32 - 160).pow(2) + (y as i32 - 120).pow(2) < 80 * 80;
            image.set_rgb(
                x,
                y,
                (x * 255 / 320) as u8,
                if inside { 20 } else { (y * 255 / 240) as u8 },
                if inside { 60 } else { 200 },
            );
        }
    }
    PageEncoder::from_pixmap(&image)
        .with_quality(EncodeQuality::Photo)
        .encode()
        .unwrap()
}

pub fn shared() -> Vec<u8> {
    let pages = vec![bitmap(320, 480); 6];
    djvu_rs::jb2_encode::encode_djvm_bundle_jb2(&pages, 2, 300)
}

pub fn rotated() -> Vec<u8> {
    let mut data = bilevel();
    let info = data.windows(4).position(|w| w == b"INFO").unwrap();
    data[info + 8 + 9] = 5; // INFO: 90 degrees clockwise.
    data
}

pub fn indirect() -> Vec<u8> {
    djvu_rs::djvm::create_indirect(&["page001.djvu"]).unwrap()
}

pub fn cases() -> Vec<(&'static str, Vec<u8>)> {
    vec![
        ("bilevel.djvu", bilevel()),
        ("colour.djvu", colour()),
        ("shared.djvu", shared()),
        ("rotated.djvu", rotated()),
        ("indirect.djvu", indirect()),
    ]
}
