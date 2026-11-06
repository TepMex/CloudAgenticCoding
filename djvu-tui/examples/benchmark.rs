//! Local rendering benchmark. ANSI output is measured into /dev/null, so this
//! separates encoding/writes from terminal emulator and SSH transport latency.
use crossterm::{
    cursor::MoveTo,
    queue,
    style::{Color, Print, SetBackgroundColor, SetForegroundColor},
};
use djvu_tui::{
    document::{Cancellation, Document, Screen},
    raster::Symbols,
    view::View,
};
use std::{
    io::{self, Write},
    path::Path,
    time::Instant,
};

fn output(screen: &Screen) -> io::Result<()> {
    let mut out = io::BufWriter::new(std::fs::OpenOptions::new().write(true).open("/dev/null")?);
    queue!(
        out,
        SetForegroundColor(Color::White),
        SetBackgroundColor(Color::Black)
    )?;
    for placed in &screen.grids {
        for row in 0..placed.grid.height {
            queue!(out, MoveTo(placed.x as u16, (placed.y + row) as u16))?;
            let text: String = placed.grid.cells
                [(row * placed.grid.width) as usize..((row + 1) * placed.grid.width) as usize]
                .iter()
                .collect();
            queue!(out, Print(text))?;
        }
    }
    out.flush()
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let path = std::env::args()
        .nth(1)
        .ok_or("usage: benchmark FILE.djvu")?;
    let mode = match std::env::args().nth(2).as_deref() {
        None | Some("braille") => Symbols::Braille,
        Some("mosaic") => Symbols::Mosaic,
        _ => return Err("mode must be braille or mosaic".into()),
    };
    let page: usize = std::env::args()
        .nth(3)
        .unwrap_or_else(|| "1".into())
        .parse()?;
    if page == 0 {
        return Err("page must be at least 1".into());
    }
    let mut samples = vec![];
    for _ in 0..30 {
        let start = Instant::now();
        let mut doc = Document::open(Path::new(&path))?;
        let open = start.elapsed();
        let mut view = View::new(0.5);
        view.page = page - 1;
        let layout = view.layout(doc.pages(), (160, 49));
        let screen = doc.render(&layout, mode, false, &Cancellation::default())?;
        let first = start.elapsed();
        let start = Instant::now();
        let cached = doc.render(&layout, mode, false, &Cancellation::default())?;
        assert!(cached.timings.cached);
        let cache = start.elapsed();
        let start = Instant::now();
        output(&screen)?;
        let out = start.elapsed();
        samples.push(
            [
                open,
                screen.timings.decode,
                screen.timings.symbols,
                cache,
                out,
                first,
            ]
            .map(|d| d.as_secs_f64() * 1000.0),
        );
    }
    println!(
        "{}; mode={}; page={}; 30 runs; 160x50 terminal; cell aspect 0.5; OS file cache warm",
        path,
        mode.name(),
        page
    );
    for (i, name) in [
        "open",
        "decode",
        "symbols",
        "cached",
        "ANSI /dev/null",
        "first screen (no output)",
    ]
    .iter()
    .enumerate()
    {
        let mut times: Vec<_> = samples.iter().map(|s| s[i]).collect();
        times.sort_by(f64::total_cmp);
        println!(
            "{name:25} median={:.3} ms p95={:.3} ms",
            times[15], times[28]
        );
    }
    Ok(())
}
