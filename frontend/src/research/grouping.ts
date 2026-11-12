import type { CompanySnapshot, StockGroup } from '../api/researchApi'

export interface Section {
  title: string
  snapshots: CompanySnapshot[]
}

const NO_GROUP = 'Ohne Gruppe'
const NO_COUNTRY = 'Ohne Land'

function byName(a: CompanySnapshot, b: CompanySnapshot): number {
  return a.companyName.localeCompare(b.companyName, 'de')
}

export function groupByStockGroup(snapshots: CompanySnapshot[], groups: StockGroup[]): Section[] {
  const known = new Set(groups.map((g) => g.key))
  const sections: Section[] = groups
    .map((g) => ({
      title: g.name,
      snapshots: snapshots.filter((s) => s.group?.key === g.key).sort(byName),
    }))
    .filter((s) => s.snapshots.length > 0)
  const ungrouped = snapshots.filter((s) => s.group === null || !known.has(s.group.key)).sort(byName)
  if (ungrouped.length > 0) sections.push({ title: NO_GROUP, snapshots: ungrouped })
  return sections
}

export function groupByCountry(snapshots: CompanySnapshot[]): Section[] {
  const byCountry = new Map<string, CompanySnapshot[]>()
  const none: CompanySnapshot[] = []
  for (const s of snapshots) {
    const country = s.country?.trim()
    if (!country) {
      none.push(s)
    } else {
      byCountry.set(country, [...(byCountry.get(country) ?? []), s])
    }
  }
  const sections: Section[] = [...byCountry.keys()]
    .sort((a, b) => a.localeCompare(b, 'de'))
    .map((title) => ({ title, snapshots: byCountry.get(title)!.sort(byName) }))
  if (none.length > 0) sections.push({ title: NO_COUNTRY, snapshots: none.sort(byName) })
  return sections
}

export function matchesSearch(snapshot: CompanySnapshot, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (q === '') return true
  return [snapshot.companyName, snapshot.ticker, snapshot.isin].some((v) => v.toLowerCase().includes(q))
}
