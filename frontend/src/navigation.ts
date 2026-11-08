export type View =
  | { name: 'home' }
  | { name: 'portfolio' }
  | { name: 'research' }
  | { name: 'researchDetail'; isin: string }
  | { name: 'impressum' }
  | { name: 'datenschutz' }

export type Navigate = (view: View) => void
