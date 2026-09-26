import { useAuthStore } from '@/stores/auth'

export function hasPermi(permission: string): boolean {
  const auth = useAuthStore()
  return auth.isAdmin || auth.permissions.includes(permission)
}

export function hasPermiOr(permissions: string[]): boolean {
  const auth = useAuthStore()
  return auth.isAdmin || permissions.some((p) => auth.permissions.includes(p))
}

export function hasPermiAnd(permissions: string[]): boolean {
  const auth = useAuthStore()
  return auth.isAdmin || permissions.every((p) => auth.permissions.includes(p))
}

export function hasRole(role: string): boolean {
  const auth = useAuthStore()
  return auth.isAdmin || auth.roles.includes(role)
}

export function hasRoleOr(roles: string[]): boolean {
  const auth = useAuthStore()
  return auth.isAdmin || roles.some((r) => auth.roles.includes(r))
}
