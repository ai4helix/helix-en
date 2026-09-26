export interface OperatorOption {
  label: string
  value: string
}

export const OPERATORS: OperatorOption[] = [
  { value: '==', label: 'Equal' },
  { value: '!=', label: 'Not equal' },
  { value: '>', label: 'Greater than' },
  { value: '>=', label: 'Greater than or equal' },
  { value: '<', label: 'Less than' },
  { value: '<=', label: 'Less than or equal' },
  { value: 'in', label: 'In' },
  { value: 'notIn', label: 'Not in' },
  { value: 'contains', label: 'Contains' },
  { value: 'notContains', label: 'Not contains' },
  { value: 'startsWith', label: 'Starts with' },
  { value: 'endsWith', label: 'Ends with' },
  { value: 'between', label: 'Between' },
  { value: 'isNull', label: 'Is null' },
  { value: 'notNull', label: 'Not null' }
]

export function opLabel(raw?: string): string {
  if (!raw) return ''
  const hit = OPERATORS.find((o) => o.value === raw)
  if (hit) return hit.label
  const norm = raw.replace(/[_-]/g, '').toLowerCase()
  const hit2 = OPERATORS.find((o) => o.value.replace(/[_-]/g, '').toLowerCase() === norm)
  return hit2 ? hit2.label : raw
}
