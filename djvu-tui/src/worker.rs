//! One decoder thread, one replaceable request, one replaceable reply.

use djvu_tui::{
    document::{Cancellation, Document, Screen},
    raster::Symbols,
    view::{Layout, PageSize},
};
use std::{
    path::PathBuf,
    sync::{Arc, Condvar, Mutex},
    thread,
    time::{Duration, Instant},
};

pub enum Work {
    Open(PathBuf),
    Render {
        layout: Layout,
        symbols: Symbols,
        dither: bool,
        prefetch: Option<Layout>,
    },
}

pub enum Content {
    Opened {
        pages: Vec<PageSize>,
        elapsed: Duration,
    },
    Rendered(Screen),
}

pub struct Reply {
    pub generation: u64,
    pub result: Result<Content, String>,
}

struct Request {
    generation: u64,
    work: Work,
    cancel: Cancellation,
}

#[derive(Default)]
struct State {
    pending: Option<Request>,
    active: Cancellation,
    stopped: bool,
}

#[derive(Default)]
struct Shared {
    state: Mutex<State>,
    ready: Condvar,
    reply: Mutex<Option<Reply>>,
}

pub struct Worker(Arc<Shared>);

impl Worker {
    pub fn start(wake: impl Fn() + Send + 'static) -> std::io::Result<Self> {
        let shared = Arc::new(Shared::default());
        let child = Arc::clone(&shared);
        thread::Builder::new()
            .name("djvu-decoder".into())
            .spawn(move || {
                let mut document = None;
                loop {
                    let request = {
                        let mut state = child.state.lock().unwrap();
                        while state.pending.is_none() && !state.stopped {
                            state = child.ready.wait(state).unwrap();
                        }
                        if state.stopped {
                            break;
                        }
                        state.pending.take().unwrap()
                    };
                    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                        match &request.work {
                            Work::Open(path) => {
                                // Release the previous document before allocating the next.
                                document = None;
                                let start = Instant::now();
                                let opened = Document::open(path)?;
                                let pages = opened.pages().to_vec();
                                document = Some(opened);
                                Ok(Content::Opened {
                                    pages,
                                    elapsed: start.elapsed(),
                                })
                            }
                            Work::Render {
                                layout,
                                symbols,
                                dither,
                                ..
                            } => document
                                .as_mut()
                                .ok_or("No document is open")?
                                .render(layout, *symbols, *dither, &request.cancel)
                                .map(Content::Rendered),
                        }
                    }))
                    .unwrap_or_else(|_| {
                        document = None;
                        Err(
                            "The decoder could not process this file. Open another document."
                                .into(),
                        )
                    });
                    djvu_rs::render_cache::enforce();
                    if request.cancel.is_cancelled() {
                        continue;
                    }
                    let success = result.is_ok();
                    *child.reply.lock().unwrap() = Some(Reply {
                        generation: request.generation,
                        result,
                    });
                    wake();
                    if success
                        && let Work::Render {
                            prefetch: Some(layout),
                            symbols,
                            dither,
                            ..
                        } = &request.work
                        && let Some(doc) = &mut document
                        && !request.cancel.is_cancelled()
                    {
                        // Speculative work never publishes a frame or an error.
                        let _ = doc.render(layout, *symbols, *dither, &request.cancel);
                        djvu_rs::render_cache::enforce();
                    }
                }
            })?;
        Ok(Self(shared))
    }

    pub fn submit(&self, generation: u64, work: Work) {
        let mut state = self.0.state.lock().unwrap();
        state.active.cancel();
        let cancel = Cancellation::default();
        state.active = cancel.clone();
        state.pending = Some(Request {
            generation,
            work,
            cancel,
        });
        self.0.ready.notify_one();
    }

    pub fn take_reply(&self) -> Option<Reply> {
        self.0.reply.lock().unwrap().take()
    }

    pub fn cancel(&self) {
        let mut state = self.0.state.lock().unwrap();
        state.active.cancel();
        state.pending = None;
        self.0.reply.lock().unwrap().take();
    }
}

impl Drop for Worker {
    fn drop(&mut self) {
        let mut state = self.0.state.lock().unwrap();
        state.stopped = true;
        state.active.cancel();
        state.pending = None;
        self.0.ready.notify_one();
        // Do not hold terminal teardown hostage to a codec's cancellation
        // checkpoint. The detached thread owns all of its memory.
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use djvu_tui::view::View;
    use std::sync::mpsc;

    #[test]
    fn replacing_pending_opens_and_renders_eventually_publishes_only_the_requested_generation() {
        let bitmap = djvu_rs::Bitmap::new(120, 200);
        let bytes = djvu_rs::djvu_encode::PageEncoder::from_bitmap(&bitmap)
            .encode()
            .unwrap();
        let path =
            std::env::temp_dir().join(format!("djvu-tui-worker-{}.djvu", std::process::id()));
        std::fs::write(&path, bytes).unwrap();
        let (tx, rx) = mpsc::sync_channel(1);
        let worker = Worker::start(move || {
            let _ = tx.try_send(());
        })
        .unwrap();
        for generation in 0..100 {
            worker.submit(
                generation,
                Work::Open(PathBuf::from("/nonexistent/old.djvu")),
            );
        }
        worker.submit(100, Work::Open(path.clone()));
        let wait = |generation| {
            let deadline = Instant::now() + Duration::from_secs(5);
            loop {
                if let Some(reply) = worker.take_reply()
                    && reply.generation == generation
                {
                    break reply.result.unwrap();
                }
                assert!(
                    Instant::now() < deadline,
                    "worker failed to finish generation {generation}"
                );
                let _ = rx.recv_timeout(Duration::from_millis(10));
            }
        };
        let Content::Opened { pages, .. } = wait(100) else {
            panic!("expected document metadata")
        };
        let mut view = View::new(0.5);
        for generation in 101..200 {
            let layout = view.layout(&pages, (generation as u32, 40));
            worker.submit(
                generation,
                Work::Render {
                    layout,
                    symbols: Symbols::Braille,
                    dither: false,
                    prefetch: None,
                },
            );
        }
        let layout = view.layout(&pages, (60, 20));
        worker.submit(
            200,
            Work::Render {
                layout: layout.clone(),
                symbols: Symbols::Blocks,
                dither: true,
                prefetch: None,
            },
        );
        let Content::Rendered(screen) = wait(200) else {
            panic!("expected rendered screen")
        };
        assert_eq!(screen.layout, layout);
        assert!(screen.grids[0].grid.cells.iter().all(|&c| c == ' '));
        worker.cancel();
        assert!(worker.take_reply().is_none());
        std::fs::remove_file(path).unwrap();
    }
}
