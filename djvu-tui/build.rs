#[path = "src/mosaic_templates.rs"]
mod mosaic_templates;
use std::{env, fs, path::PathBuf};

fn main() {
    println!("cargo:rerun-if-changed=src/mosaic_templates.rs");
    let lookup: Vec<u8> = (0..=u16::MAX)
        .map(|mask| {
            mosaic_templates::TEMPLATES
                .iter()
                .enumerate()
                .min_by_key(|&(_, &(_, pattern))| (mask ^ pattern).count_ones())
                .unwrap()
                .0 as u8
        })
        .collect();
    fs::write(
        PathBuf::from(env::var_os("OUT_DIR").unwrap()).join("mosaic_lookup.bin"),
        lookup,
    )
    .unwrap();
}
