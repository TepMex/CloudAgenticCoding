//! In-process, headless PDF rendering.
use crate::{
    raster::{self, CellGrid, GrayImage, Symbols},
    view::{Layout, PageSize},
};
use std::{
    collections::VecDeque,
    path::Path,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::{Duration, Instant},
};
#[derive(Debug, Clone, Default)]
pub struct Cancellation(Arc<AtomicBool>);
impl Cancellation {
    pub fn cancel(&self) {
        self.0.store(true, Ordering::Relaxed);
    }
    pub fn is_cancelled(&self) -> bool {
        self.0.load(Ordering::Relaxed)
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
pub struct Document {
    pdf: hayro::hayro_syntax::Pdf,
    glyphs: VecDeque<(crate::view::Panel, Symbols, bool, Arc<CellGrid>)>,
    glyph_bytes: usize,
    pages: Vec<PageSize>,
    thresholds: Vec<Option<u8>>,
}
impl Document {
    pub fn open(path: &Path) -> Result<Self, String> {
        let meta = path
            .metadata()
            .map_err(|e| format!("{}: {e}", path.display()))?;
        if !meta.is_file() {
            return Err("Select a regular PDF file".into());
        }
        if meta.len() > 512 * 1024 * 1024 {
            return Err("File exceeds the 512 MiB input limit".into());
        }
        let bytes = std::fs::read(path).map_err(|e| format!("{}: {e}", path.display()))?;
        let pdf =
            hayro::hayro_syntax::Pdf::new(bytes).map_err(|e| format!("Cannot open PDF: {e:?}"))?;
        let count = pdf.pages().len();
        if count == 0 || count > 100_000 {
            return Err("PDF has no pages or exceeds the 100,000-page limit".into());
        }
        let mut pages = Vec::with_capacity(count);
        for page in pdf.pages().iter() {
            let (w, h) = page.render_dimensions();
            if !w.is_finite()
                || !h.is_finite()
                || w <= 0.0
                || h <= 0.0
                || w > 1_000_000.0
                || h > 1_000_000.0
            {
                return Err("Invalid PDF page dimensions".into());
            }
            pages.push(PageSize {
                width: (w * 100.0).round().max(1.0) as u32,
                height: (h * 100.0).round().max(1.0) as u32,
            });
        }
        Ok(Self {
            pdf,
            pages,
            thresholds: vec![None; count],
            glyphs: VecDeque::new(),
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
        let mut grids = Vec::new();
        let mut timings = Timings {
            cached: true,
            ..Default::default()
        };
        for p in &layout.panels {
            if cancel.is_cancelled() {
                return Err("Cancelled".into());
            }
            if p.page >= self.pages.len()
                || p.width == 0
                || p.height == 0
                || p.crop_x
                    .checked_add(p.width)
                    .is_none_or(|x| x > p.full_width)
                || p.crop_y
                    .checked_add(p.height)
                    .is_none_or(|y| y > p.full_height)
            {
                return Err("Invalid page or viewport dimensions".into());
            }
            if let Some(pos) = self.glyphs.iter().position(|(panel, mode, dith, _)| {
                panel == p && *mode == symbols && *dith == dither
            }) {
                let entry = self.glyphs.remove(pos).unwrap();
                grids.push(PlacedGrid {
                    x: p.x,
                    y: p.y,
                    grid: Arc::clone(&entry.3),
                });
                self.glyphs.push_front(entry);
                continue;
            }
            timings.cached = false;
            let start = Instant::now();
            let threshold = if let Some(t) = self.thresholds[p.page] {
                t
            } else {
                let size = self.pages[p.page];
                let scale = 256.0 / f64::from(size.width.max(size.height));
                let w = (f64::from(size.width) * scale).round().max(1.0) as u32;
                let h = (f64::from(size.height) * scale).round().max(1.0) as u32;
                let t = self.rasterize(p.page, (w, h), (0, 0, w, h))?.threshold();
                self.thresholds[p.page] = Some(t);
                t
            };
            if cancel.is_cancelled() {
                return Err("Cancelled".into());
            }
            let (sx, sy) = symbols.samples();
            let mul = |a: u32, b: u32| {
                a.checked_mul(b)
                    .ok_or_else(|| "Viewport too large".to_string())
            };
            let image = self.rasterize(
                p.page,
                (mul(p.full_width, sx)?, mul(p.full_height, sy)?),
                (
                    mul(p.crop_x, sx)?,
                    mul(p.crop_y, sy)?,
                    mul(p.width, sx)?,
                    mul(p.height, sy)?,
                ),
            )?;
            timings.decode += start.elapsed();
            if cancel.is_cancelled() {
                return Err("Cancelled".into());
            }
            let start = Instant::now();
            let grid = raster::render(
                &image,
                symbols,
                threshold,
                dither,
                (p.crop_x * sx, p.crop_y * sy),
            );
            timings.symbols += start.elapsed();
            let grid = Arc::new(grid);
            let size = grid.bytes();
            if size <= 16 * 1024 * 1024 {
                while self.glyph_bytes + size > 16 * 1024 * 1024 || self.glyphs.len() >= 256 {
                    if let Some((_, _, _, old)) = self.glyphs.pop_back() {
                        self.glyph_bytes -= old.bytes();
                    }
                }
                self.glyph_bytes += size;
                self.glyphs
                    .push_front((p.clone(), symbols, dither, Arc::clone(&grid)));
            }
            grids.push(PlacedGrid {
                x: p.x,
                y: p.y,
                grid,
            });
        }
        Ok(Screen {
            layout: layout.clone(),
            grids,
            timings,
        })
    }
    fn rasterize(
        &self,
        page: usize,
        full: (u32, u32),
        crop: (u32, u32, u32, u32),
    ) -> Result<GrayImage, String> {
        use hayro::{
            RenderCache, RenderSettings,
            hayro_interpret::{InterpreterSettings, util::TransformExt},
            kurbo::Affine,
            vello_cpu::{
                Pixmap, RasterizerSettings, RenderContext, Resources, TargetInit,
                color::palette::css::WHITE,
            },
        };
        let (fw, fh) = full;
        let (x, y, w, h) = crop;
        if w == 0
            || h == 0
            || w > u16::MAX as u32
            || h > u16::MAX as u32
            || u64::from(w) * u64::from(h) > 16_000_000
            || fw > 1_000_000
            || fh > 1_000_000
        {
            return Err("PDF viewport exceeds rendering limits".into());
        }
        let page = &self.pdf.pages()[page];
        let (width, height) = page.render_dimensions();
        let transform = Affine::translate((-(x as f64), -(y as f64)))
            * Affine::scale_non_uniform(fw as f64 / width as f64, fh as f64 / height as f64)
            * page.initial_transform(true).to_kurbo();
        let mut ctx = RenderContext::new(w as u16, h as u16);
        hayro::render_into(
            page,
            &RenderCache::new(),
            &InterpreterSettings::default(),
            &RenderSettings::default(),
            &mut ctx,
            transform,
        );
        ctx.flush();
        let mut pixmap = Pixmap::new(w as u16, h as u16);
        ctx.render_with(
            &mut pixmap,
            &mut Resources::default(),
            RasterizerSettings {
                target_init: TargetInit::Clear(WHITE),
                ..Default::default()
            },
        );
        Ok(GrayImage::from_rgba(w, h, pixmap.data_as_u8_slice()))
    }
}
