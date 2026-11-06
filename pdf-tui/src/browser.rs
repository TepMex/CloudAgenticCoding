use ratatui::widgets::ListState;
use std::{
    fs,
    path::{Path, PathBuf},
};

pub struct Entry {
    pub path: PathBuf,
    pub label: String,
    pub directory: bool,
}

pub struct Browser {
    pub directory: PathBuf,
    pub entries: Vec<Entry>,
    pub selection: ListState,
    pub warning: Option<String>,
}

pub fn printable(text: &str) -> String {
    text.chars()
        .map(|c| if c.is_control() { '�' } else { c })
        .collect()
}

impl Browser {
    pub fn empty(directory: PathBuf) -> Self {
        Self {
            directory,
            entries: vec![],
            selection: ListState::default(),
            warning: None,
        }
    }

    pub fn load(directory: &Path) -> Result<Self, String> {
        let mut entries = vec![];
        let mut warning = None;
        for entry in fs::read_dir(directory).map_err(|e| format!("{}: {e}", directory.display()))? {
            let entry = match entry {
                Ok(entry) => entry,
                Err(e) => {
                    warning = Some(e.to_string());
                    continue;
                }
            };
            let path = entry.path();
            let metadata = match fs::metadata(&path) {
                Ok(m) => m,
                Err(e) => {
                    warning = Some(e.to_string());
                    continue;
                }
            };
            let directory = metadata.is_dir();
            let pdf = path
                .extension()
                .is_some_and(|ext| ext.eq_ignore_ascii_case("pdf"));
            if directory || (metadata.is_file() && pdf) {
                let name = printable(&entry.file_name().to_string_lossy());
                entries.push(Entry {
                    path,
                    label: if directory { format!("{name}/") } else { name },
                    directory,
                });
            }
        }
        entries.sort_by(|a, b| {
            b.directory
                .cmp(&a.directory)
                .then_with(|| a.label.cmp(&b.label))
        });
        let selection =
            ListState::default().with_selected(if entries.is_empty() { None } else { Some(0) });
        Ok(Self {
            directory: directory.to_path_buf(),
            entries,
            selection,
            warning,
        })
    }

    pub fn step(&mut self, delta: isize) {
        if self.entries.is_empty() {
            return;
        }
        let next = self
            .selection
            .selected()
            .unwrap_or(0)
            .saturating_add_signed(delta)
            .min(self.entries.len() - 1);
        self.selection.select(Some(next));
    }

    pub fn selected(&self) -> Option<&Entry> {
        self.selection.selected().and_then(|i| self.entries.get(i))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn filenames_cannot_inject_terminal_controls() {
        assert_eq!(printable("a\x1b[31m\n.pdf"), "a�[31m�.pdf");
    }
}
