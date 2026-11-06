//! Navigation and physical page layout, expressed in terminal cells.

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PageSize {
    /// Display dimensions, with the document's rotation already applied.
    pub width: u32,
    pub height: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum LayoutMode {
    #[default]
    Auto,
    Single,
    Spread,
}

impl LayoutMode {
    pub fn cycle(&mut self) {
        *self = match self {
            Self::Auto => Self::Single,
            Self::Single => Self::Spread,
            Self::Spread => Self::Auto,
        };
    }

    pub fn name(self) -> &'static str {
        match self {
            Self::Auto => "auto",
            Self::Single => "single",
            Self::Spread => "spread",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Panel {
    pub page: usize,
    /// Whole scaled page in cells, before clipping.
    pub full_width: u32,
    pub full_height: u32,
    /// Cell-aligned crop of the scaled page.
    pub crop_x: u32,
    pub crop_y: u32,
    pub width: u32,
    pub height: u32,
    /// Position of the crop in the terminal's content area.
    pub x: u32,
    pub y: u32,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Layout {
    pub width: u32,
    pub height: u32,
    pub panels: Vec<Panel>,
}

#[derive(Debug, Clone)]
pub struct View {
    pub page: usize,
    pub mode: LayoutMode,
    pub zoom: f64,
    pub pan_x: u32,
    pub pan_y: u32,
    pub cell_aspect: f64,
}

impl View {
    pub fn new(cell_aspect: f64) -> Self {
        Self {
            page: 0,
            mode: LayoutMode::Auto,
            zoom: 1.0,
            pan_x: 0,
            pan_y: 0,
            cell_aspect,
        }
    }

    pub fn fit(&mut self) {
        self.zoom = 1.0;
        self.pan_x = 0;
        self.pan_y = 0;
    }

    pub fn go_to(&mut self, page: usize, count: usize) {
        self.page = page.min(count.saturating_sub(1));
        self.pan_x = 0;
        self.pan_y = 0;
    }

    pub fn navigate(&mut self, forward: bool, pages: &[PageSize], area: (u32, u32)) {
        let layout = self.layout(pages, area);
        if let (Some(first), Some(last)) = (layout.panels.first(), layout.panels.last()) {
            let target = if forward {
                last.page.saturating_add(1)
            } else {
                first.page.saturating_sub(1)
            };
            self.go_to(target, pages.len());
        }
    }

    pub fn zoom_by(&mut self, factor: f64, pages: &[PageSize], area: (u32, u32)) {
        // Zooming into a spread focuses the selected page; subsequent changes
        // retain the viewport centre rather than jumping back to the top.
        let mut single = self.clone();
        single.mode = LayoutMode::Single;
        let old = single.layout(pages, area);
        self.zoom = (self.zoom * factor).clamp(1.0, 16.0);
        let new = self.layout(pages, area);
        if self.zoom == 1.0 {
            self.fit();
            return;
        }
        if let (Some(a), Some(b)) = (old.panels.first(), new.panels.first()) {
            let cx = (a.crop_x as f64 + a.width as f64 / 2.0) / a.full_width as f64;
            let cy = (a.crop_y as f64 + a.height as f64 / 2.0) / a.full_height as f64;
            self.pan_x = (cx * b.full_width as f64 - b.width as f64 / 2.0)
                .max(0.0)
                .round() as u32;
            self.pan_y = (cy * b.full_height as f64 - b.height as f64 / 2.0)
                .max(0.0)
                .round() as u32;
            self.layout(pages, area);
        }
    }

    pub fn pan(&mut self, dx: i32, dy: i32, pages: &[PageSize], area: (u32, u32)) {
        if self.zoom <= 1.0 {
            return;
        }
        self.pan_x = self.pan_x.saturating_add_signed(dx);
        self.pan_y = self.pan_y.saturating_add_signed(dy);
        self.layout(pages, area);
    }

    pub fn layout(&mut self, pages: &[PageSize], (width, height): (u32, u32)) -> Layout {
        let mut layout = Layout {
            width,
            height,
            panels: vec![],
        };
        if pages.is_empty() || width == 0 || height == 0 {
            return layout;
        }
        self.page = self.page.min(pages.len() - 1);
        let mut indices = vec![self.page];
        if self.zoom == 1.0 && self.mode != LayoutMode::Single && self.page > 0 {
            let start = 1 + (self.page - 1) / 2 * 2;
            if start + 1 < pages.len() {
                let max_height = pages[start].height.max(pages[start + 1].height).max(1);
                let scale = height as f64 / max_height as f64;
                let cols =
                    |i: usize| (pages[i].width as f64 * scale / self.cell_aspect).ceil() as u32;
                if self.mode == LayoutMode::Spread || cols(start) + cols(start + 1) + 2 <= width {
                    indices = vec![start, start + 1];
                }
            }
        }
        let gap = if indices.len() == 2 { 2 } else { 0 };
        // Extremely narrow terminals always fall back to one page.
        if width <= gap {
            indices = vec![self.page];
        }
        let gap = if indices.len() == 2 { 2 } else { 0 };
        let total_width: u32 = indices.iter().map(|&i| pages[i].width).sum();
        let max_height = indices
            .iter()
            .map(|&i| pages[i].height)
            .max()
            .unwrap()
            .max(1);
        let scale = (height as f64 / max_height as f64)
            .min((width - gap) as f64 * self.cell_aspect / total_width.max(1) as f64)
            * self.zoom;
        let sizes: Vec<(u32, u32)> = indices
            .iter()
            .map(|&i| {
                // Floor avoids roundoff creating a page one cell too large to fit.
                (
                    (pages[i].width as f64 * scale / self.cell_aspect)
                        .floor()
                        .max(1.0) as u32,
                    (pages[i].height as f64 * scale).floor().max(1.0) as u32,
                )
            })
            .collect();
        let used = sizes.iter().map(|s| s.0).sum::<u32>() + gap;
        let mut x = width.saturating_sub(used) / 2;
        for (&page, &(full_width, full_height)) in indices.iter().zip(&sizes) {
            let visible_width = full_width.min(width);
            let visible_height = full_height.min(height);
            let (crop_x, crop_y) = if self.zoom > 1.0 {
                self.pan_x = self.pan_x.min(full_width - visible_width);
                self.pan_y = self.pan_y.min(full_height - visible_height);
                (self.pan_x, self.pan_y)
            } else {
                (0, 0)
            };
            layout.panels.push(Panel {
                page,
                full_width,
                full_height,
                crop_x,
                crop_y,
                width: visible_width,
                height: visible_height,
                x,
                y: height.saturating_sub(visible_height) / 2,
            });
            x += full_width + gap;
        }
        layout
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn pages() -> Vec<PageSize> {
        vec![
            PageSize {
                width: 100,
                height: 200
            };
            6
        ]
    }

    #[test]
    fn cover_spread_boundary_and_last_page() {
        let mut view = View::new(0.5);
        assert_eq!(view.layout(&pages(), (200, 50)).panels.len(), 1);
        view.page = 1;
        assert_eq!(view.layout(&pages(), (101, 50)).panels.len(), 1);
        let spread = view.layout(&pages(), (102, 50));
        assert_eq!(
            spread.panels.iter().map(|p| p.page).collect::<Vec<_>>(),
            [1, 2]
        );
        assert_eq!(spread.panels[1].x - spread.panels[0].x, 52);
        view.page = 5;
        assert_eq!(view.layout(&pages(), (200, 50)).panels.len(), 1);
    }

    #[test]
    fn proportions_and_forced_spread() {
        let mut view = View::new(0.5);
        let p = &view.layout(&pages(), (80, 30)).panels[0];
        assert_eq!((p.full_width, p.full_height), (30, 30));
        view.page = 2;
        view.mode = LayoutMode::Spread;
        let layout = view.layout(&pages(), (42, 30));
        assert_eq!(layout.panels.len(), 2);
        assert_eq!((layout.panels[0].width, layout.panels[0].height), (20, 20));
    }

    #[test]
    fn zoom_focus_and_pan_limits() {
        let mut view = View::new(0.5);
        view.page = 2;
        view.zoom_by(4.0, &pages(), (100, 40));
        assert_eq!(view.layout(&pages(), (100, 40)).panels[0].page, 2);
        view.pan(i32::MAX, i32::MAX, &pages(), (100, 40));
        let p = &view.layout(&pages(), (100, 40)).panels[0];
        assert_eq!((p.crop_x, p.crop_y), (60, 120));
        view.pan(i32::MIN, i32::MIN, &pages(), (100, 40));
        assert_eq!((view.pan_x, view.pan_y), (0, 0));
        view.fit();
        assert_eq!(view.zoom, 1.0);
    }

    #[test]
    fn navigation_and_resize_preserve_focus() {
        let mut view = View::new(0.5);
        view.navigate(true, &pages(), (200, 50));
        assert_eq!(view.page, 1);
        view.navigate(true, &pages(), (200, 50));
        assert_eq!(view.page, 3);
        view.navigate(false, &pages(), (200, 50));
        assert_eq!(view.page, 2);
        view.layout(&pages(), (20, 10));
        assert_eq!(view.page, 2);
        view.navigate(false, &pages(), (200, 50));
        assert_eq!(view.page, 0);
    }

    #[test]
    fn tiny_and_empty_viewports() {
        let mut view = View::new(0.5);
        assert!(view.layout(&[], (100, 40)).panels.is_empty());
        assert!(view.layout(&pages(), (0, 0)).panels.is_empty());
        view.page = 1;
        view.mode = LayoutMode::Spread;
        let layout = view.layout(&pages(), (1, 1));
        assert_eq!(layout.panels.len(), 1);
        assert_eq!(layout.panels[0].width, 1);
    }
}
