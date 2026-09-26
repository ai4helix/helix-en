export interface FieldGroup<T> {
  name: string
  items: T[]
}

export function groupByCatalog<T extends { catalogName?: string }>(fields: T[]): FieldGroup<T>[] {
  const groups: FieldGroup<T>[] = []
  const index = new Map<string, number>()
  for (const f of fields) {
    const name = f.catalogName || 'Uncategorized'
    let gi = index.get(name)
    if (gi === undefined) {
      gi = groups.length
      index.set(name, gi)
      groups.push({ name, items: [] })
    }
    groups[gi].items.push(f)
  }
  return groups
}
