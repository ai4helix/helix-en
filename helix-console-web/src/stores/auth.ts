import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, currentUser, type LoginResult } from '@/api/system'
import { getToken, setToken, removeToken } from '@/utils/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(getToken())
  const userId = ref<number>()
  const account = ref('')
  const nickName = ref('')
  const organId = ref<number>()
  const organName = ref('')
  const userType = ref<number>()
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userType.value === 1 || roles.value.includes('ROLE_ADMIN'))

  function applyResult(data: LoginResult | any) {
    token.value = data.token ?? token.value
    userId.value = data.userId
    account.value = data.account
    nickName.value = data.nickName
    organId.value = data.organId
    organName.value = data.organName
    userType.value = data.userType
    roles.value = data.roles || []
    permissions.value = data.permissions || []
    if (data.token) {
      setToken(data.token)
    }
  }

  async function login(acc: string, pwd: string) {
    const res = await loginApi(acc, pwd)
    applyResult(res)
    return res
  }

  async function restore() {
    if (!token.value) return false
    try {
      const me = await currentUser()
      userId.value = me.userId
      account.value = me.account
      nickName.value = me.nickName
      organId.value = me.organId
      organName.value = me.organName || ''
      userType.value = me.userType
      roles.value = me.roleCodes || []
      permissions.value = me.permissions || []
      return true
    } catch {
      logout()
      return false
    }
  }

  function logout() {
    token.value = ''
    userId.value = undefined
    account.value = ''
    nickName.value = ''
    organId.value = undefined
    organName.value = ''
    roles.value = []
    permissions.value = []
    removeToken()
  }

  function has(code: string): boolean {
    if (isAdmin.value) return true
    return permissions.value.includes(code)
  }

  function hasAny(codes: string[]): boolean {
    if (isAdmin.value) return true
    return codes.some((c) => permissions.value.includes(c))
  }

  return {
    token, userId, account, nickName, organId, organName, userType, roles, permissions,
    isLoggedIn, isAdmin,
    login, restore, logout, has, hasAny
  }
})
