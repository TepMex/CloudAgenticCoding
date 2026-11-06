//! Two-colour geometric Unicode rendering. Coordinates are anchored to the page,
//! so dithering never changes its phase when the viewport moves.

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Default)]
pub enum Symbols {
    #[default]
    Braille,
    Blocks,
}

impl Symbols {
    pub fn samples(self) -> (u32, u32) {
        match self {
            Self::Braille => (2, 4),
            Self::Blocks => (2, 2),
        }
    }

    pub fn toggle(&mut self) {
        *self = match self {
            Self::Braille => Self::Blocks,
            Self::Blocks => Self::Braille,
        };
    }

    pub fn name(self) -> &'static str {
        match self {
            Self::Braille => "braille",
            Self::Blocks => "blocks",
        }
    }
}

#[derive(Debug, Clone)]
pub struct GrayImage {
    pub width: u32,
    pub height: u32,
    pub pixels: Vec<u8>,
}

impl GrayImage {
    pub(crate) fn from_rgba(width: u32, height: u32, rgba: &[u8]) -> Self {
        let pixels = rgba
            .chunks_exact(4)
            .map(|p| {
                let luma =
                    (u32::from(p[0]) * 54 + u32::from(p[1]) * 183 + u32::from(p[2]) * 19) >> 8;
                // Composite transparency on white paper.
                ((luma * u32::from(p[3]) + 255 * (255 - u32::from(p[3]))) / 255) as u8
            })
            .collect();
        Self {
            width,
            height,
            pixels,
        }
    }

    /// Otsu threshold, computed once per page on a small full-page render.
    /// A single-colour page uses mid-grey rather than making white paper ink.
    pub fn threshold(&self) -> u8 {
        let mut hist = [0u64; 256];
        for &p in &self.pixels {
            hist[p as usize] += 1;
        }
        let total = self.pixels.len() as u64;
        let sum: u64 = hist.iter().enumerate().map(|(i, n)| i as u64 * n).sum();
        let (mut count, mut partial, mut best, mut threshold) = (0u64, 0u64, 0.0, 127);
        for (i, n) in hist.into_iter().enumerate() {
            count += n;
            partial += i as u64 * n;
            if count == 0 || count == total {
                continue;
            }
            let delta =
                partial as f64 / count as f64 - (sum - partial) as f64 / (total - count) as f64;
            let score = count as f64 * (total - count) as f64 * delta * delta;
            if score > best {
                best = score;
                threshold = i as u8;
            }
        }
        threshold
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CellGrid {
    pub width: u32,
    pub height: u32,
    pub cells: Vec<char>,
}

impl CellGrid {
    pub fn bytes(&self) -> usize {
        self.cells.capacity() * size_of::<char>()
    }
}

const BRAILLE_BITS: [[u8; 2]; 4] = [[0, 3], [1, 4], [2, 5], [6, 7]];
const QUADRANTS: [char; 16] = [
    ' ', '▘', '▝', '▀', '▖', '▌', '▞', '▛', '▗', '▚', '▐', '▜', '▄', '▙', '▟', '█',
];
const BAYER: [[u8; 4]; 4] = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]];

/// Convert one cell-aligned viewport to glyphs. `origin` is in sample pixels.
pub fn render(
    image: &GrayImage,
    symbols: Symbols,
    threshold: u8,
    dither: bool,
    origin: (u32, u32),
) -> CellGrid {
    assert_eq!(
        image.pixels.len(),
        image.width as usize * image.height as usize
    );
    let (sx, sy) = symbols.samples();
    let width = image.width.div_ceil(sx);
    let height = image.height.div_ceil(sy);
    let mut cells = Vec::with_capacity(width as usize * height as usize);
    for row in 0..height {
        for col in 0..width {
            let mut mask = 0u8;
            for y in 0..sy {
                for x in 0..sx {
                    let (px, py) = (col * sx + x, row * sy + y);
                    if px >= image.width || py >= image.height {
                        continue;
                    }
                    let value = image.pixels[(py * image.width + px) as usize];
                    let ink = if dither {
                        let level =
                            BAYER[((origin.1 + py) % 4) as usize][((origin.0 + px) % 4) as usize];
                        u16::from(255 - value) > u16::from(level) * 16 + 8
                    } else {
                        value <= threshold
                    };
                    if ink {
                        let bit = match symbols {
                            Symbols::Braille => BRAILLE_BITS[y as usize][x as usize],
                            Symbols::Blocks => (y * 2 + x) as u8,
                        };
                        mask |= 1 << bit;
                    }
                }
            }
            cells.push(match symbols {
                Symbols::Braille if mask != 0 => char::from_u32(0x2800 + u32::from(mask)).unwrap(),
                Symbols::Braille => ' ',
                Symbols::Blocks => QUADRANTS[mask as usize],
            });
        }
    }
    CellGrid {
        width,
        height,
        cells,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn all_braille_masks_and_dot_positions() {
        for mask in 0..=255u32 {
            let pixels = BRAILLE_BITS
                .iter()
                .flatten()
                .map(|bit| if mask & (1 << bit) != 0 { 0 } else { 255 })
                .collect();
            let image = GrayImage {
                width: 2,
                height: 4,
                pixels,
            };
            let grid = render(&image, Symbols::Braille, 127, false, (0, 0));
            assert_eq!(
                grid.cells,
                vec![if mask == 0 {
                    ' '
                } else {
                    char::from_u32(0x2800 + mask).unwrap()
                }]
            );
        }
    }

    #[test]
    fn all_quadrants() {
        for (mask, &glyph) in QUADRANTS.iter().enumerate() {
            let image = GrayImage {
                width: 2,
                height: 2,
                pixels: (0..4)
                    .map(|bit| if mask & (1 << bit) != 0 { 0 } else { 255 })
                    .collect(),
            };
            assert_eq!(
                render(&image, Symbols::Blocks, 127, false, (0, 0)).cells[0],
                glyph
            );
        }
    }

    #[test]
    fn blank_solid_and_partial_cells() {
        for symbols in [Symbols::Braille, Symbols::Blocks] {
            let (width, height) = symbols.samples();
            for value in [0, 255] {
                let image = GrayImage {
                    width,
                    height,
                    pixels: vec![value; (width * height) as usize],
                };
                let glyph = render(&image, symbols, image.threshold(), true, (0, 0)).cells[0];
                assert_eq!(
                    glyph,
                    if value == 255 {
                        ' '
                    } else if symbols == Symbols::Braille {
                        '⣿'
                    } else {
                        '█'
                    }
                );
            }
        }
        let image = GrayImage {
            width: 1,
            height: 1,
            pixels: vec![0],
        };
        assert_eq!(
            render(&image, Symbols::Braille, 127, false, (0, 0)).cells,
            vec!['⠁']
        );
    }

    #[test]
    fn otsu_separates_paper_and_ink() {
        let image = GrayImage {
            width: 4,
            height: 1,
            pixels: vec![20, 20, 230, 250],
        };
        assert!(image.threshold() >= 20 && image.threshold() < 230);
    }

    #[test]
    fn dithering_is_anchored_to_page() {
        let image = GrayImage {
            width: 8,
            height: 8,
            pixels: vec![128; 64],
        };
        let full = render(&image, Symbols::Blocks, 127, true, (0, 0));
        let part = GrayImage {
            width: 4,
            height: 4,
            pixels: vec![128; 16],
        };
        let part = render(&part, Symbols::Blocks, 127, true, (2, 2));
        assert_eq!(
            part.cells,
            vec![full.cells[5], full.cells[6], full.cells[9], full.cells[10]]
        );
    }

    #[test]
    fn rgba_luminance_and_transparency() {
        let image = GrayImage::from_rgba(2, 1, &[0, 0, 0, 0, 255, 255, 255, 255]);
        assert_eq!(image.pixels, [255, 255]);
    }
}
