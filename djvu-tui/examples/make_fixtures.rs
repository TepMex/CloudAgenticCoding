//! Generate original fixtures for local visual QA. Nothing is downloaded.
#[path = "../tests/support/mod.rs"]
mod support;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let directory = std::env::args_os()
        .nth(1)
        .map(std::path::PathBuf::from)
        .unwrap_or_else(|| std::path::PathBuf::from("target/fixtures"));
    std::fs::create_dir_all(&directory)?;
    for (name, bytes) in support::cases() {
        std::fs::write(directory.join(name), bytes)?;
    }
    let large =
        djvu_rs::djvu_encode::PageEncoder::from_bitmap(&support::bitmap(2400, 3500)).encode()?;
    std::fs::write(directory.join("large.djvu"), large)?;
    println!("Generated fixtures in {}", directory.display());
    Ok(())
}
