use pdf_tui::{
    document::{Cancellation, Document},
    raster::Symbols,
    view::View,
};
use std::{fmt::Write, fs};

fn fixture() -> Vec<u8> {
    let objects = [
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] /Contents 4 0 R /Resources << >> >>",
        "<< /Length 21 >>\nstream\n0 0 100 150 re f\nendstream",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] /Rotate 90 /Contents 4 0 R /Resources << >> >>",
    ];
    let mut pdf = String::from("%PDF-1.4\n");
    let mut offsets = Vec::new();
    for (i, obj) in objects.iter().enumerate() {
        offsets.push(pdf.len());
        write!(pdf, "{} 0 obj\n{}\nendobj\n", i + 1, obj).unwrap();
    }
    let xref = pdf.len();
    write!(pdf, "xref\n0 {}\n0000000000 65535 f \n", objects.len() + 1).unwrap();
    for offset in offsets {
        writeln!(pdf, "{offset:010} 00000 n ").unwrap();
    }
    write!(
        pdf,
        "trailer\n<< /Size {} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n",
        objects.len() + 1
    )
    .unwrap();
    pdf.into_bytes()
}
#[test]
fn opens_renders_and_crops_pdf_pages() {
    let dir = std::env::temp_dir().join(format!("pdf-tui-test-{}", std::process::id()));
    fs::create_dir_all(&dir).unwrap();
    let path = dir.join("-book.pdf");
    fs::write(&path, fixture()).unwrap();
    let mut doc = Document::open(&path).unwrap();
    assert_eq!(doc.pages().len(), 2);
    assert!(doc.pages()[0].height > doc.pages()[0].width);
    assert!(doc.pages()[1].width > doc.pages()[1].height);
    let mut view = View::new(0.5);
    let layout = view.layout(doc.pages(), (80, 30));
    let screen = doc
        .render(&layout, Symbols::Braille, false, &Cancellation::default())
        .unwrap();
    assert!(screen.grids[0].grid.cells.iter().any(|&c| c != ' '));
    view.zoom_by(3.0, doc.pages(), (80, 30));
    view.pan(8, 4, doc.pages(), (80, 30));
    let layout = view.layout(doc.pages(), (80, 30));
    assert!(layout.panels[0].crop_x > 0 || layout.panels[0].crop_y > 0);
    doc.render(&layout, Symbols::Blocks, true, &Cancellation::default())
        .unwrap();
    fs::remove_dir_all(dir).unwrap();
}
