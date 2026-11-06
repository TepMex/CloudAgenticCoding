//! One decoder thread, one replaceable request, one replaceable reply.

use pdf_tui::{
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
            .name("pdf-decoder".into())
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
