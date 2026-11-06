mod support;

use djvu_tui::{
    document::{Cancellation, Document},
    raster::Symbols,
    view::View,
};

#[test]
fn decode_bilevel_colour_shared_dictionary_and_rotation() {
    for (name, bytes) in support::cases() {
        if name == "indirect.djvu" {
            continue;
        }
        if name == "shared.djvu" {
            assert!(bytes.windows(4).any(|w| w == b"Djbz"));
            assert!(bytes.windows(4).any(|w| w == b"INCL"));
        }
        let mut doc = Document::from_bytes(bytes).unwrap();
        assert_eq!(doc.pages().len(), if name == "shared.djvu" { 6 } else { 1 });
        if name == "rotated.djvu" {
            assert_eq!((doc.pages()[0].width, doc.pages()[0].height), (480, 320));
        }
        let mut view = View::new(0.5);
        for symbols in [
            Symbols::Braille,
            Symbols::BrailleFilled,
            Symbols::Blocks,
            Symbols::Mosaic,
        ] {
            for dither in [false, true] {
                let layout = view.layout(doc.pages(), (100, 40));
                let first = doc
                    .render(&layout, symbols, dither, &Cancellation::default())
                    .unwrap();
                assert!(!first.grids.is_empty());
                assert!(
                    first.grids[0].grid.cells.iter().any(|&c| c != ' '),
                    "{name} must contain ink"
                );
                assert!(
                    first.grids[0].grid.cells.contains(&' '),
                    "{name} must contain paper"
                );
                let again = doc
                    .render(&layout, symbols, dither, &Cancellation::default())
                    .unwrap();
                assert!(again.timings.cached);
                assert_eq!(first.grids[0].grid.cells, again.grids[0].grid.cells);
            }
        }
        if doc.pages().len() > 1 {
            view.page = 2;
            let layout = view.layout(doc.pages(), (200, 40));
            assert_eq!(
                doc.render(&layout, Symbols::Braille, false, &Cancellation::default())
                    .unwrap()
                    .grids
                    .len(),
                2
            );
        }
    }
}

#[test]
fn region_render_matches_cell_crop_and_cancelled_work_is_reusable() {
    let mut doc = Document::from_bytes(support::bilevel()).unwrap();
    let mut view = View::new(0.5);
    let layout = view.layout(doc.pages(), (80, 40));
    for symbols in [
        Symbols::Braille,
        Symbols::BrailleFilled,
        Symbols::Blocks,
        Symbols::Mosaic,
    ] {
        for dither in [false, true] {
            let whole = doc
                .render(&layout, symbols, dither, &Cancellation::default())
                .unwrap();
            let mut crop = layout.clone();
            crop.panels[0].crop_x = 3;
            crop.panels[0].crop_y = 5;
            crop.panels[0].width = 9;
            crop.panels[0].height = 11;
            let part = doc
                .render(&crop, symbols, dither, &Cancellation::default())
                .unwrap();
            let grid = &whole.grids[0].grid;
            let expected: Vec<_> = (5..16)
                .flat_map(|row| {
                    (3..12).map(move |col| grid.cells[(row * grid.width + col) as usize])
                })
                .collect();
            assert_eq!(part.grids[0].grid.cells, expected);
        }
    }
    let cancel = Cancellation::default();
    cancel.cancel();
    assert!(
        doc.render(&layout, Symbols::Braille, false, &cancel)
            .is_err()
    );
    assert!(
        doc.render(&layout, Symbols::Braille, false, &Cancellation::default())
            .is_ok()
    );
}

#[test]
fn reports_invalid_indirect_and_unreadable_files() {
    assert!(Document::from_bytes(vec![]).is_err());
    assert!(Document::from_bytes(b"not a DjVu".to_vec()).is_err());
    let err = Document::from_bytes(support::indirect()).err().unwrap();
    assert!(
        err.contains("Indirect DjVu documents are not supported"),
        "{err}"
    );
    assert!(
        Document::open(std::path::Path::new(
            "/this-directory-does-not-exist/book.djvu"
        ))
        .is_err()
    );
    assert!(Document::open(std::path::Path::new(".")).is_err());
}

#[test]
fn invalid_viewport_is_an_error_instead_of_a_panic() {
    let mut doc = Document::from_bytes(support::bilevel()).unwrap();
    let mut view = View::new(0.5);
    let mut layout = view.layout(doc.pages(), (80, 40));
    layout.panels[0].page = usize::MAX;
    assert!(
        doc.render(&layout, Symbols::Braille, false, &Cancellation::default())
            .is_err()
    );
    layout.panels[0].page = 0;
    layout.panels[0].crop_x = u32::MAX;
    assert!(
        doc.render(&layout, Symbols::Braille, false, &Cancellation::default())
            .is_err()
    );
}
