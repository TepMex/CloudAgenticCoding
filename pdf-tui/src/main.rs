mod browser;
mod tui;
mod worker;

use std::{
    io::{self, IsTerminal},
    path::PathBuf,
    process::ExitCode,
};

struct Args {
    path: PathBuf,
    cell_aspect: Option<f64>,
}

fn arguments(args: impl IntoIterator<Item = std::ffi::OsString>) -> Result<Option<Args>, String> {
    let mut args = args.into_iter();
    let mut path = None;
    let mut cell_aspect = None;
    let mut positional = false;
    while let Some(arg) = args.next() {
        if !positional {
            if arg == "--help" || arg == "-h" {
                println!(
                    "pdf-tui [--cell-aspect NUMBER] [PATH]\n\nOpen a PDF file or browse a directory (default: current directory).\n--cell-aspect NUMBER  Cell width / height; default: detected, or 0.5\n--                   Treat remaining arguments as paths\n\nUse ? inside the viewer for keyboard controls."
                );
                return Ok(None);
            }
            if arg == "--version" || arg == "-V" {
                println!("pdf-tui {}", env!("CARGO_PKG_VERSION"));
                return Ok(None);
            }
            if arg == "--" {
                positional = true;
                continue;
            }
            if arg == "--cell-aspect" {
                let value = args.next().ok_or("--cell-aspect requires a number")?;
                let value = value
                    .to_str()
                    .and_then(|s| s.parse::<f64>().ok())
                    .filter(|n| n.is_finite() && (0.1..=2.0).contains(n))
                    .ok_or("--cell-aspect must be a finite number between 0.1 and 2.0")?;
                cell_aspect = Some(value);
                continue;
            }
            if arg.to_string_lossy().starts_with('-') {
                return Err(format!("Unknown option: {}", arg.to_string_lossy()));
            }
        }
        if path.replace(PathBuf::from(arg)).is_some() {
            return Err("Expected at most one PATH".into());
        }
    }
    let path = match path {
        Some(path) => path,
        None => std::env::current_dir().map_err(|e| e.to_string())?,
    };
    Ok(Some(Args { path, cell_aspect }))
}

fn main() -> ExitCode {
    let result = (|| {
        let Some(args) = arguments(std::env::args_os().skip(1))? else {
            return Ok(());
        };
        if !io::stdin().is_terminal() || !io::stdout().is_terminal() {
            return Err("pdf-tui requires an interactive terminal on stdin and stdout".into());
        }
        tui::run(args.path, args.cell_aspect).map_err(|e| e.to_string())
    })();
    match result {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("pdf-tui: {error}");
            ExitCode::FAILURE
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn parse(args: &[&str]) -> Result<Option<Args>, String> {
        arguments(args.iter().map(std::ffi::OsString::from))
    }

    #[test]
    fn validates_options_before_entering_raw_mode() {
        for value in ["0", "NaN", "inf", "-1", "3", "no"] {
            assert!(parse(&["--cell-aspect", value]).is_err());
        }
        assert!(parse(&["--cell-aspect"]).is_err());
        assert!(parse(&["a", "b"]).is_err());
        assert!(parse(&["--unknown"]).is_err());
        let args = parse(&["--cell-aspect", "0.6", "--", "-book.pdf"])
            .unwrap()
            .unwrap();
        assert_eq!(args.cell_aspect, Some(0.6));
        assert_eq!(args.path, PathBuf::from("-book.pdf"));
    }
}
