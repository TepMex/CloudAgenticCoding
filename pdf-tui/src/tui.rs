use crate::{
    browser::{Browser, printable},
    worker::{Content, Work, Worker},
};
use crossterm::{
    cursor::{Hide, Show},
    event::{self, Event, KeyCode, KeyEvent, KeyEventKind, KeyModifiers},
    execute,
    style::{Attribute, ResetColor, SetAttribute},
    terminal::{self, EnterAlternateScreen, LeaveAlternateScreen},
};
use pdf_tui::{
    document::Screen,
    raster::Symbols,
    view::{Layout, PageSize, View},
};
use ratatui::{
    Frame, Terminal,
    backend::CrosstermBackend,
    layout::{Alignment, Rect},
    style::{Color, Modifier, Style},
    widgets::{Block, Borders, Clear, List, ListItem, Paragraph, Wrap},
};
use std::{
    io::{self, Stdout},
    path::PathBuf,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
        mpsc,
    },
    thread,
    time::{Duration, Instant},
};

enum Message {
    Input(Event),
    InputError(String),
    Wake,
}

fn restore_terminal() {
    let _ = terminal::disable_raw_mode();
    let _ = execute!(
        io::stdout(),
        SetAttribute(Attribute::Reset),
        ResetColor,
        Show,
        LeaveAlternateScreen
    );
}

struct TerminalSession(Arc<AtomicBool>);
impl Drop for TerminalSession {
    fn drop(&mut self) {
        self.0.store(true, Ordering::Relaxed);
        restore_terminal();
    }
}

struct SignalGuard {
    ids: Vec<signal_hook::SigId>,
    stop: Arc<AtomicBool>,
}
impl Drop for SignalGuard {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
        for id in self.ids.drain(..) {
            signal_hook::low_level::unregister(id);
        }
    }
}

fn measured_aspect() -> f64 {
    terminal::window_size()
        .ok()
        .and_then(|size| {
            if size.width == 0 || size.height == 0 || size.columns == 0 || size.rows == 0 {
                return None;
            }
            let ratio = (f64::from(size.width) / f64::from(size.columns))
                / (f64::from(size.height) / f64::from(size.rows));
            (ratio.is_finite() && (0.1..=2.0).contains(&ratio)).then_some(ratio)
        })
        .unwrap_or(0.5)
}

struct App {
    browser: Browser,
    browsing: bool,
    file: Option<PathBuf>,
    pages: Vec<PageSize>,
    view: View,
    symbols: Symbols,
    dither: bool,
    inverted: bool,
    help: bool,
    goto: Option<String>,
    error: Option<String>,
    loading: bool,
    generation: u64,
    area: (u32, u32),
    screen: Option<Screen>,
    requested: Option<(Layout, Symbols, bool)>,
}

impl App {
    fn new(path: &std::path::Path, aspect: f64, area: (u32, u32)) -> Self {
        let directory = if path.is_dir() {
            path.to_path_buf()
        } else {
            path.parent()
                .filter(|p| !p.as_os_str().is_empty())
                .map(PathBuf::from)
                .unwrap_or_else(|| PathBuf::from("."))
        };
        let (browser, error) = match Browser::load(&directory) {
            Ok(browser) => (browser, None),
            Err(error) => (Browser::empty(directory), Some(error)),
        };
        Self {
            browser,
            error,
            browsing: true,
            file: None,
            pages: vec![],
            view: View::new(aspect),
            symbols: Symbols::Braille,
            dither: false,
            inverted: false,
            help: false,
            goto: None,
            loading: false,
            generation: 0,
            area,
            screen: None,
            requested: None,
        }
    }

    fn open(&mut self, path: PathBuf, worker: &Worker) {
        self.generation += 1;
        self.pages.clear();
        self.screen = None;
        self.requested = None;
        self.error = None;
        self.browsing = false;
        self.loading = true;
        self.view.page = 0;
        self.view.fit();
        self.file = Some(path.clone());
        worker.submit(self.generation, Work::Open(path));
    }

    fn schedule(&mut self, worker: &Worker) {
        if self.browsing || self.pages.is_empty() {
            return;
        }
        let layout = self.view.layout(&self.pages, self.area);
        let key = (layout.clone(), self.symbols, self.dither);
        if self.requested.as_ref() == Some(&key) {
            return;
        }
        self.generation += 1;
        self.screen = None;
        self.error = None;
        self.requested = Some(key);
        self.loading = !layout.panels.is_empty();
        if !self.loading {
            worker.cancel();
            return;
        }
        let prefetch = if self.view.zoom == 1.0 {
            let mut next = self.view.clone();
            next.navigate(true, &self.pages, self.area);
            let next = next.layout(&self.pages, self.area);
            (next != layout).then_some(next)
        } else {
            None
        };
        worker.submit(
            self.generation,
            Work::Render {
                layout,
                symbols: self.symbols,
                dither: self.dither,
                prefetch,
            },
        );
    }

    fn browse_to(&mut self, path: &std::path::Path) {
        match Browser::load(path) {
            Ok(browser) => {
                self.browser = browser;
                self.error = None;
            }
            Err(error) => self.error = Some(error),
        }
    }

    /// Returns true only when the user requests exit.
    fn key(&mut self, key: KeyEvent, worker: &Worker) -> bool {
        if key.kind == KeyEventKind::Release {
            return false;
        }
        if key.code == KeyCode::Char('c') && key.modifiers.contains(KeyModifiers::CONTROL) {
            return true;
        }
        if self.goto.is_some() {
            match key.code {
                KeyCode::Esc => self.goto = None,
                KeyCode::Backspace => {
                    self.goto.as_mut().unwrap().pop();
                }
                KeyCode::Char(c) if c.is_ascii_digit() => {
                    if self.goto.as_ref().unwrap().len() < 10 {
                        self.goto.as_mut().unwrap().push(c);
                    }
                }
                KeyCode::Enter => {
                    let value = self.goto.take().unwrap();
                    match value.parse::<usize>() {
                        Ok(n) if n > 0 && n <= self.pages.len() => {
                            self.view.go_to(n - 1, self.pages.len());
                            self.schedule(worker);
                        }
                        _ => {
                            self.error =
                                Some(format!("Enter a page from 1 to {}", self.pages.len()))
                        }
                    }
                }
                _ => {}
            }
            return false;
        }
        if key.code == KeyCode::Char('q') {
            return true;
        }
        if self.help {
            if matches!(key.code, KeyCode::Esc | KeyCode::Char('?')) {
                self.help = false;
            }
            return false;
        }
        if key.code == KeyCode::Char('?') {
            self.help = true;
            return false;
        }
        if self.browsing {
            match key.code {
                KeyCode::Up => self.browser.step(-1),
                KeyCode::Down => self.browser.step(1),
                KeyCode::PageUp => self.browser.step(-(self.area.1 as isize).max(1)),
                KeyCode::PageDown => self.browser.step((self.area.1 as isize).max(1)),
                KeyCode::Home => self.browser.step(isize::MIN),
                KeyCode::End => self.browser.step(isize::MAX),
                KeyCode::Enter => {
                    if let Some(entry) = self.browser.selected() {
                        let path = entry.path.clone();
                        if entry.directory {
                            self.browse_to(&path);
                        } else {
                            self.open(path, worker);
                        }
                    }
                }
                KeyCode::Backspace => {
                    if let Some(parent) = self.browser.directory.parent() {
                        // Relative CLI paths such as "." have no useful parent;
                        // canonicalising just the navigation path resolves that.
                        let parent = if parent.as_os_str().is_empty() {
                            self.browser
                                .directory
                                .canonicalize()
                                .ok()
                                .and_then(|p| p.parent().map(PathBuf::from))
                        } else {
                            Some(parent.to_path_buf())
                        };
                        if let Some(parent) = parent {
                            self.browse_to(&parent);
                        }
                    }
                }
                KeyCode::Esc if !self.pages.is_empty() => {
                    self.browsing = false;
                    self.error = None;
                    self.requested = None;
                    self.schedule(worker);
                }
                KeyCode::Char('i') => self.inverted = !self.inverted,
                _ => {}
            }
            return false;
        }
        match key.code {
            KeyCode::Char('o') | KeyCode::Esc => {
                worker.cancel();
                self.generation += 1;
                self.loading = false;
                self.browsing = true;
                let directory = self
                    .file
                    .as_ref()
                    .and_then(|p| p.parent())
                    .filter(|p| !p.as_os_str().is_empty())
                    .map(PathBuf::from)
                    .unwrap_or_else(|| self.browser.directory.clone());
                self.browse_to(&directory);
            }
            KeyCode::Char('i') => self.inverted = !self.inverted,
            KeyCode::Char('g') if !self.pages.is_empty() => {
                self.goto = Some(String::new());
                self.error = None;
            }
            KeyCode::PageDown | KeyCode::Char(' ') => {
                self.view.navigate(true, &self.pages, self.area)
            }
            KeyCode::PageUp => self.view.navigate(false, &self.pages, self.area),
            KeyCode::Char('+') | KeyCode::Char('=') => {
                self.view.zoom_by(1.25, &self.pages, self.area)
            }
            KeyCode::Char('-') => self.view.zoom_by(1.0 / 1.25, &self.pages, self.area),
            KeyCode::Char('0') => self.view.fit(),
            KeyCode::Left => self.view.pan(-4, 0, &self.pages, self.area),
            KeyCode::Right => self.view.pan(4, 0, &self.pages, self.area),
            KeyCode::Up => self.view.pan(0, -2, &self.pages, self.area),
            KeyCode::Down => self.view.pan(0, 2, &self.pages, self.area),
            KeyCode::Char('v') => {
                self.view.mode.cycle();
                self.view.pan_x = 0;
                self.view.pan_y = 0;
            }
            KeyCode::Char('r') => self.symbols.toggle(),
            KeyCode::Char('d') => self.dither = !self.dither,
            _ => {}
        }
        self.schedule(worker);
        false
    }

    fn draw(&mut self, frame: &mut Frame) {
        let area = frame.area();
        let style = palette(self.inverted);
        frame.render_widget(Block::default().style(style), area);
        if area.width == 0 || area.height == 0 {
            return;
        }
        let content = Rect::new(area.x, area.y, area.width, area.height.saturating_sub(1));
        if self.browsing {
            let title = printable(&self.browser.directory.display().to_string());
            frame.render_widget(
                Paragraph::new(title).style(style.add_modifier(Modifier::BOLD)),
                Rect {
                    height: content.height.min(1),
                    ..content
                },
            );
            let list_area = Rect {
                y: content.y.saturating_add(1),
                height: content.height.saturating_sub(1),
                ..content
            };
            if self.browser.entries.is_empty() {
                frame.render_widget(
                    Paragraph::new("No PDF files. Backspace: parent directory")
                        .wrap(Wrap { trim: false }),
                    list_area,
                );
            } else {
                let entries: Vec<_> = self
                    .browser
                    .entries
                    .iter()
                    .map(|e| ListItem::new(e.label.as_str()))
                    .collect();
                frame.render_stateful_widget(
                    List::new(entries)
                        .style(style)
                        .highlight_style(style.add_modifier(Modifier::REVERSED))
                        .highlight_symbol("> "),
                    list_area,
                    &mut self.browser.selection,
                );
            }
        } else if let Some(screen) = &self.screen {
            for placed in &screen.grids {
                for row in 0..placed.grid.height {
                    for col in 0..placed.grid.width {
                        let (x, y) = (placed.x + col, placed.y + row);
                        if x < u32::from(content.width)
                            && y < u32::from(content.height)
                            && let Some(cell) = frame
                                .buffer_mut()
                                .cell_mut((content.x + x as u16, content.y + y as u16))
                        {
                            cell.set_char(
                                placed.grid.cells[(row * placed.grid.width + col) as usize],
                            )
                            .set_style(style);
                        }
                    }
                }
            }
        } else {
            let text = self.error.clone().unwrap_or_else(|| {
                if self.loading {
                    "Loading…  o: files  q: quit".into()
                } else {
                    "o: open a PDF file  ?: help".into()
                }
            });
            frame.render_widget(
                Paragraph::new(printable(&text))
                    .wrap(Wrap { trim: false })
                    .alignment(Alignment::Center),
                content,
            );
        }
        let status = if let Some(number) = &self.goto {
            format!("Go to page: {number}_  Enter: confirm  Esc: cancel")
        } else if let Some(error) =
            self.error
                .as_ref()
                .or(self.browser.warning.as_ref().filter(|_| self.browsing))
        {
            printable(error)
        } else if self.browsing {
            "↑↓ select  Enter open  Backspace parent  Esc return  ? help  q quit".into()
        } else {
            let name = self
                .file
                .as_ref()
                .and_then(|p| p.file_name())
                .map(|s| printable(&s.to_string_lossy()))
                .unwrap_or_default();
            let pages = self
                .requested
                .as_ref()
                .and_then(|(l, _, _)| {
                    let first = l.panels.first()?.page + 1;
                    let last = l.panels.last()?.page + 1;
                    Some(if first == last {
                        first.to_string()
                    } else {
                        format!("{first}–{last}")
                    })
                })
                .unwrap_or_else(|| "…".into());
            format!(
                "{name}  {pages}/{}  {:.0}%  {} {}{}  {}  ? help",
                self.pages.len(),
                self.view.zoom * 100.0,
                self.view.mode.name(),
                self.symbols.name(),
                if self.dither { "+dither" } else { "" },
                if self.loading { "loading" } else { "ready" }
            )
        };
        frame.render_widget(
            Paragraph::new(status).style(style.add_modifier(Modifier::REVERSED)),
            Rect::new(area.x, area.bottom() - 1, area.width, 1),
        );
        if self.help {
            let popup = centred(area, 76, 22);
            frame.render_widget(Clear, popup);
            frame.render_widget(Paragraph::new(
                "Files\n  ↑↓ select · Enter open · Backspace parent\n  o / Esc file browser · Esc return to document\n\nReading\n  Space / PgDown next · PgUp previous\n  g jump to page · + / - zoom · 0 fit\n  ↑↓←→ pan when zoomed\n  v auto / single / spread\n  r Braille / fill / blocks / mosaic · d Bayer dithering\n  i invert black and white\n\nFirst page is a cover. Spreads: 2–3, 4–5, …\nZoom focuses the selected page. UTF-8 required.\n\n? / Esc close help · q / Ctrl-C quit")
                .style(style).wrap(Wrap { trim: false }).block(Block::default().borders(Borders::ALL).title(" pdf-tui ")), popup);
        }
    }
}

fn palette(inverted: bool) -> Style {
    if inverted {
        Style::default().fg(Color::Black).bg(Color::White)
    } else {
        Style::default().fg(Color::White).bg(Color::Black)
    }
}

fn centred(area: Rect, width: u16, height: u16) -> Rect {
    let (width, height) = (width.min(area.width), height.min(area.height));
    Rect::new(
        area.x + (area.width - width) / 2,
        area.y + (area.height - height) / 2,
        width,
        height,
    )
}

pub fn run(path: PathBuf, aspect_override: Option<f64>) -> io::Result<()> {
    let stopped = Arc::new(AtomicBool::new(false));
    let interrupted = Arc::new(AtomicBool::new(false));
    let mut signals = SignalGuard {
        ids: vec![],
        stop: Arc::clone(&stopped),
    };
    for signal in [signal_hook::consts::SIGINT, signal_hook::consts::SIGTERM] {
        signals.ids.push(signal_hook::flag::register(
            signal,
            Arc::clone(&interrupted),
        )?);
    }
    let previous_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        if thread::current().name() == Some("pdf-decoder") {
            return;
        }
        restore_terminal();
        previous_hook(info);
    }));
    terminal::enable_raw_mode()?;
    let session = TerminalSession(Arc::clone(&stopped));
    execute!(io::stdout(), EnterAlternateScreen, Hide)?;
    let mut terminal: Terminal<CrosstermBackend<Stdout>> =
        Terminal::new(CrosstermBackend::new(io::stdout()))?;
    terminal.clear()?;
    let (tx, rx) = mpsc::sync_channel(32);
    let wake = tx.clone();
    let worker = Worker::start(move || {
        let _ = wake.try_send(Message::Wake);
    })?;
    let stop = Arc::clone(&stopped);
    thread::Builder::new()
        .name("terminal-input".into())
        .spawn(move || {
            while !stop.load(Ordering::Relaxed) {
                let result = event::poll(Duration::from_millis(50)).and_then(|ready| {
                    if ready {
                        event::read().map(Some)
                    } else {
                        Ok(None)
                    }
                });
                match result {
                    Ok(Some(event)) => {
                        if tx.send(Message::Input(event)).is_err() {
                            break;
                        }
                    }
                    Ok(None) => {}
                    Err(e) => {
                        let _ = tx.send(Message::InputError(e.to_string()));
                        break;
                    }
                }
            }
        })?;
    let size = terminal.size()?;
    let area = (
        u32::from(size.width),
        u32::from(size.height.saturating_sub(1)),
    );
    let mut app = App::new(&path, aspect_override.unwrap_or_else(measured_aspect), area);
    if !path.is_dir() {
        app.open(path, &worker);
    }
    let mut dirty = true;
    let mut last_draw = Duration::ZERO;
    let mut last_open = Duration::ZERO;
    let mut last_decode = Duration::ZERO;
    let mut last_symbols = Duration::ZERO;
    let mut last_cached = false;
    while !interrupted.load(Ordering::Relaxed) {
        if let Some(reply) = worker.take_reply()
            && reply.generation == app.generation
        {
            app.loading = false;
            match reply.result {
                Ok(Content::Opened { pages, elapsed }) => {
                    last_open = elapsed;
                    app.pages = pages;
                    app.schedule(&worker);
                }
                Ok(Content::Rendered(screen)) => {
                    last_decode = screen.timings.decode;
                    last_symbols = screen.timings.symbols;
                    last_cached = screen.timings.cached;
                    app.screen = Some(screen);
                }
                Err(error) => app.error = Some(error),
            }
            dirty = true;
        }
        if dirty {
            let start = Instant::now();
            terminal.draw(|frame| app.draw(frame))?;
            last_draw = start.elapsed();
            dirty = false;
        }
        let message = match rx.recv_timeout(Duration::from_millis(50)) {
            Ok(message) => message,
            Err(mpsc::RecvTimeoutError::Timeout) => continue,
            Err(mpsc::RecvTimeoutError::Disconnected) => break,
        };
        match message {
            Message::Wake => {}
            Message::Input(Event::Key(key)) => {
                if app.key(key, &worker) {
                    break;
                }
                dirty = true;
            }
            Message::Input(Event::Resize(width, height)) => {
                app.area = (u32::from(width), u32::from(height.saturating_sub(1)));
                if aspect_override.is_none() {
                    app.view.cell_aspect = measured_aspect();
                }
                app.schedule(&worker);
                dirty = true;
            }
            Message::InputError(error) => return Err(io::Error::other(error)),
            _ => {}
        }
    }
    drop(worker);
    drop(terminal);
    drop(session);
    if std::env::var_os("PDF_TUI_TIMINGS").is_some() {
        eprintln!(
            "open_ms={:.3} decode_ms={:.3} symbols_ms={:.3} output_ms={:.3} cached={last_cached}",
            last_open.as_secs_f64() * 1000.0,
            last_decode.as_secs_f64() * 1000.0,
            last_symbols.as_secs_f64() * 1000.0,
            last_draw.as_secs_f64() * 1000.0
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use ratatui::backend::TestBackend;

    #[test]
    fn both_polarities_and_tiny_screens_render_without_panics() {
        for (width, height) in [(1, 1), (2, 2), (20, 8), (100, 40)] {
            for inverted in [false, true] {
                let mut app = App::new(
                    std::path::Path::new("."),
                    0.5,
                    (width.into(), height.into()),
                );
                app.inverted = inverted;
                app.help = width > 2;
                let mut terminal = Terminal::new(TestBackend::new(width, height)).unwrap();
                terminal.draw(|frame| app.draw(frame)).unwrap();
                let buffer = terminal.backend().buffer();
                assert!(
                    buffer
                        .content
                        .iter()
                        .all(|c| [Color::Black, Color::White].contains(&c.fg)
                            && [Color::Black, Color::White].contains(&c.bg))
                );
                assert_eq!(
                    buffer[(0, height - 1)].fg,
                    if inverted { Color::Black } else { Color::White }
                );
            }
        }
    }
}
