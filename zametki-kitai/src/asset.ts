export function journalAssetUrl(): string {
  const base = import.meta.env.BASE_URL || './'
  return `${base}${base.endsWith('/') ? '' : '/'}journal.bin`
}
