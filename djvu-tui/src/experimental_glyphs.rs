//! Experimental masks for custom terminal fonts. The regular TUI does not use these.
use crate::raster::GrayImage;

pub const BASE: u32 = 0xF0000;
pub const MASK_COUNT: usize = 1 << 15;

/// A mask is laid out row-major, with the upper left sample in bit zero.
pub fn glyph(mask: u16) -> char {
    assert!((mask as usize) < MASK_COUNT);
    char::from_u32(BASE + u32::from(mask)).unwrap()
}

/// Convert independent 3×5 areas or overlapping 3×5 contour nodes to masks.
/// The contour raster is `2 * columns + 1` by `4 * rows + 1`, so adjacent
/// cells share their boundary samples, including at the viewport edge.
pub fn masks(image: &GrayImage, columns: u32, rows: u32, contour: bool, threshold: u8) -> Vec<u16> {
    let (step_x, step_y) = if contour { (2, 4) } else { (3, 5) };
    let (width, height) = if contour {
        (columns * step_x + 1, rows * step_y + 1)
    } else {
        (columns * step_x, rows * step_y)
    };
    assert_eq!((image.width, image.height), (width, height));
    assert_eq!(image.pixels.len(), width as usize * height as usize);
    let mut out = Vec::with_capacity((columns * rows) as usize);
    for row in 0..rows {
        for col in 0..columns {
            let mut mask = 0;
            for y in 0..5 {
                for x in 0..3 {
                    let p = ((row * step_y + y) * width + col * step_x + x) as usize;
                    if image.pixels[p] <= threshold {
                        mask |= 1 << (y * 3 + x);
                    }
                }
            }
            out.push(mask);
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn all_masks_map_to_distinct_private_use_characters() {
        for mask in 0..MASK_COUNT {
            assert_eq!(glyph(mask as u16) as u32, BASE + mask as u32);
        }
        assert_eq!(glyph(32767) as u32, 0xF7FFF);
    }

    #[test]
    fn shared_contour_boundaries_match() {
        let image = GrayImage {
            width: 5,
            height: 9,
            pixels: (0..45)
                .map(|i| if (i * 7 + i / 5) % 5 < 2 { 0 } else { 255 })
                .collect(),
        };
        let m = masks(&image, 2, 2, true, 127);
        for row in 0..2 {
            for y in 0..5 {
                assert_eq!(
                    (m[row * 2] >> (y * 3 + 2)) & 1,
                    (m[row * 2 + 1] >> (y * 3)) & 1
                );
            }
        }
        for col in 0..2 {
            for x in 0..3 {
                assert_eq!((m[col] >> (12 + x)) & 1, (m[2 + col] >> x) & 1);
            }
        }
    }

    #[test]
    fn blank_and_full_pixel_masks() {
        for (pixel, expected) in [(255, 0), (0, 32767)] {
            let image = GrayImage {
                width: 3,
                height: 5,
                pixels: vec![pixel; 15],
            };
            assert_eq!(masks(&image, 1, 1, false, 127), vec![expected]);
        }
    }
}
