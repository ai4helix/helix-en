const TokenKey = 'helix_console_token'

export function getToken(): string {
  return localStorage.getItem(TokenKey) || ''
}

export function setToken(token: string): void {
  localStorage.setItem(TokenKey, token)
}

export function removeToken(): void {
  localStorage.removeItem(TokenKey)
}
