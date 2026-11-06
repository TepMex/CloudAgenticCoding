//! Decoder adapter and bounded page caches. No terminal or UI types cross this
//! interface; the worker and headless checks exercise exactly the same path.

use crate::{
    raster::{self, CellGrid, GrayImage, Symbols},
    view::{Layout, PageSize, Panel},
};
use djvu_rs::{
    Document as Decoder, ParseOptions, ResourceLimits, Rotation,
    djvu_render::{CancelToken, RenderOptions, RenderRect, RenderRequest},
};
use std::{
    collections::VecDeque,
    path::Path,
    sync::Arc,
    time::{Duration, Instant},
};

// Reserve 64 KiB for the fixed-size key table and Arc/grid headers.
const GLYPH_BUDGET: usize = 16 * 1024 * 1024 - 64 * 1024;
const DECODE_BUDGET: usize = 48 * 1024 * 1024;
const MAX_GLYPH_ENTRIES: usize = 256;

#[derive(Debug, Clone, Default)]
pub struct Cancellation(CancelToken);

impl Cancellation {
    pub fn cancel(&self) {
        self.0.cancel();
    }
    pub fn is_cancelled(&self) -> bool {
        self.0.is_cancelled()
    }
}

#[derive(Debug, Clone)]
pub struct PlacedGrid {
    pub x: u32,
    pub y: u32,
    pub grid: Arc<CellGrid>,
}

#[derive(Debug, Clone, Default)]
pub struct Timings {
    pub decode: Duration,
    pub symbols: Duration,
    pub cached: bool,
}

#[derive(Debug, Clone)]
pub struct Screen {
    pub layout: Layout,
    pub grids: Vec<PlacedGrid>,
    pub timings: Timings,
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct Key {
    page: usize,
    full_width: u32,
    full_height: u32,
    crop_x: u32,
    crop_y: u32,
    width: u32,
    height: u32,
    symbols: Symbols,
    dither: bool,
}

impl Key {
    fn new(p: &Panel, symbols: Symbols, dither: bool) -> Self {
        Self {
            page: p.page,
            full_width: p.full_width,
            full_height: p.full_height,
            crop_x: p.crop_x,
            crop_y: p.crop_y,
            width: p.width,
            height: p.height,
            symbols,
            dither,
        }
    }
}

pub struct Document {
    decoder: Decoder,
    pages: Vec<PageSize>,
    thresholds: Vec<Option<u8>>,
    glyphs: VecDeque<(Key, Arc<CellGrid>)>,
    glyph_bytes: usize,
}

impl Document {
    pub fn open(path: &Path) -> Result<Self, String> {
        let metadata = path
            .metadata()
            .map_err(|e| format!("{}: {e}", path.display()))?;
        if !metadata.is_file() {
            return Err("Select a regular DjVu file".into());
        }
        // Check before read_to_end, not only after allocating the input buffer.
        if metadata.len() > 512 * 1024 * 1024 {
            return Err("File exceeds the 512 MiB input limit".into());
        }
        let bytes = std::fs::read(path).map_err(|e| format!("{}: {e}", path.display()))?;
        Self::from_bytes(bytes)
    }

    pub fn from_bytes(bytes: Vec<u8>) -> Result<Self, String> {
        djvu_rs::render_cache::set_budget(DECODE_BUDGET);
        let decoder = Decoder::from_bytes_with_options(
            bytes,
            &ParseOptions {
                limits: Some(ResourceLimits {
                    max_file_bytes: Some(512 * 1024 * 1024),
                    max_pages: Some(100_000),
                    max_page_pixels: Some(128_000_000),
                    max_decoded_bytes: Some(512 * 1024 * 1024),
                    max_render_pixels: Some(16_000_000),
                    ..Default::default()
                }),
            },
        )
        .map_err(|e| {
            let message = e.to_string();
            if message.contains("indirect") && message.contains("resolver") {
                "Indirect DjVu documents are not supported. Open a single-file (bundled) DjVu."
                    .into()
            } else {
                format!("Cannot open DjVu: {message}")
            }
        })?;
        let mut pages = Vec::with_capacity(decoder.page_count());
        for i in 0..decoder.page_count() {
            let page = decoder.inner().page(i).map_err(|e| e.to_string())?;
            let (mut width, mut height) = (u32::from(page.width()), u32::from(page.height()));
            if matches!(page.rotation(), Rotation::Ccw90 | Rotation::Cw90) {
                std::mem::swap(&mut width, &mut height);
            }
            if width == 0 || height == 0 {
                return Err(format!("Page {} has zero dimensions", i + 1));
            }
            pages.push(PageSize { width, height });
        }
        if pages.is_empty() {
            return Err("The document has no pages".into());
        }
        let count = pages.len();
        Ok(Self {
            decoder,
            pages,
            thresholds: vec![None; count],
            glyphs: VecDeque::with_capacity(MAX_GLYPH_ENTRIES),
            glyph_bytes: 0,
        })
    }

    pub fn pages(&self) -> &[PageSize] {
        &self.pages
    }

    pub fn render(
        &mut self,
        layout: &Layout,
        symbols: Symbols,
        dither: bool,
        cancel: &Cancellation,
    ) -> Result<Screen, String> {
        let mut grids = Vec::with_capacity(layout.panels.len());
        let mut timings = Timings {
            cached: true,
            ..Default::default()
        };
        for panel in &layout.panels {
            let (sx, sy) = symbols.samples();
            if panel.page >= self.pages.len()
                || panel.width == 0
                || panel.height == 0
                || panel.full_width.checked_mul(sx).is_none()
                || panel.full_height.checked_mul(sy).is_none()
                || panel
                    .crop_x
                    .checked_add(panel.width)
                    .is_none_or(|end| end > panel.full_width)
                || panel
                    .crop_y
                    .checked_add(panel.height)
                    .is_none_or(|end| end > panel.full_height)
            {
                return Err("Invalid page or viewport dimensions".into());
            }
            if cancel.is_cancelled() {
                return Err("Cancelled".into());
            }
            let key = Key::new(panel, symbols, dither);
            let grid = if let Some(pos) = self.glyphs.iter().position(|(k, _)| *k == key) {
                let entry = self.glyphs.remove(pos).unwrap();
                let grid = Arc::clone(&entry.1);
                self.glyphs.push_front(entry);
                grid
            } else {
                timings.cached = false;
                let start = Instant::now();
                let threshold = self.threshold(panel.page, cancel)?;
                let image = self.region(panel, symbols, cancel)?;
                timings.decode += start.elapsed();
                if cancel.is_cancelled() {
                    return Err("Cancelled".into());
                }
                let start = Instant::now();
                let (sx, sy) = symbols.samples();
                let grid = Arc::new(raster::render(
                    &image,
                    symbols,
                    threshold,
                    dither,
                    (panel.crop_x * sx, panel.crop_y * sy),
                ));
                timings.symbols += start.elapsed();
                let size = grid.bytes();
                if size <= GLYPH_BUDGET {
                    while self.glyph_bytes + size > GLYPH_BUDGET
                        || self.glyphs.len() >= MAX_GLYPH_ENTRIES
                    {
                        if let Some((_, old)) = self.glyphs.pop_back() {
                            self.glyph_bytes -= old.bytes();
                        }
                    }
                    self.glyph_bytes += size;
                    self.glyphs.push_front((key, Arc::clone(&grid)));
                }
                grid
            };
            grids.push(PlacedGrid {
                x: panel.x,
                y: panel.y,
                grid,
            });
        }
        // The decoder can temporarily exceed its budget by one protected
        // layer. After a render there is no protected layer, so enforce again.
        djvu_rs::render_cache::enforce();
        Ok(Screen {
            layout: layout.clone(),
            grids,
            timings,
        })
    }

    fn threshold(&mut self, index: usize, cancel: &Cancellation) -> Result<u8, String> {
        if let Some(threshold) = self.thresholds[index] {
            return Ok(threshold);
        }
        let page = self
            .decoder
            .inner()
            .page(index)
            .map_err(|e| e.to_string())?;
        let options = RenderOptions::fit_to_box(page, 256, 256);
        let pixmap = RenderRequest::new(options)
            .cancel(cancel.0.clone())
            .pixmap(page)
            .map_err(|e| e.to_string())?;
        let threshold = GrayImage::from_rgba(pixmap.width, pixmap.height, &pixmap.data).threshold();
        self.thresholds[index] = Some(threshold);
        Ok(threshold)
    }

    fn region(
        &self,
        panel: &Panel,
        symbols: Symbols,
        cancel: &Cancellation,
    ) -> Result<GrayImage, String> {
        let page = self
            .decoder
            .inner()
            .page(panel.page)
            .map_err(|e| e.to_string())?;
        let (sx, sy) = symbols.samples();
        let (width, height) = (panel.full_width * sx, panel.full_height * sy);
        // Cell geometry may require non-square samples. The compositor accepts
        // independent dimensions, which prevents aspect distortion on screen.
        let rotated = matches!(page.rotation(), Rotation::Ccw90 | Rotation::Cw90);
        let options = RenderOptions {
            width: if rotated { height } else { width },
            height: if rotated { width } else { height },
            ..Default::default()
        };
        let pixmap = RenderRequest::new(options)
            .region(RenderRect {
                x: panel.crop_x * sx,
                y: panel.crop_y * sy,
                width: panel.width * sx,
                height: panel.height * sy,
            })
            .cached(true)
            .cancel(cancel.0.clone())
            .pixmap(page)
            .map_err(|e| format!("Page {}: {e}", panel.page + 1))?;
        Ok(GrayImage::from_rgba(
            pixmap.width,
            pixmap.height,
            &pixmap.data,
        ))
    }
}
