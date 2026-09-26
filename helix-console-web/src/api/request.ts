import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types/flow'
import { getToken, removeToken } from '@/utils/auth'

const CODE_UNAUTHORIZED = 20001

export function createRequest(baseURL: string): AxiosInstance {
  const instance: AxiosInstance = axios.create({
    baseURL,
    timeout: 20000,
    headers: { 'Content-Type': 'application/json;charset=UTF-8' }
  })

  instance.interceptors.request.use((config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  instance.interceptors.response.use(
    (response: AxiosResponse<Result<any>>) => {
      const body = response.data
      if (body == null || typeof body.code === 'undefined') {
        return response.data
      }
      if (body.code === 0) {
        return body.data
      }
      if (body.code === CODE_UNAUTHORIZED) {
        removeToken()
        const base = import.meta.env.BASE_URL
        const routePath =
          base !== '/' && location.pathname.startsWith(base)
            ? location.pathname.slice(base.length - 1)
            : location.pathname
        if (routePath !== '/login') {
          location.href = `${base}login?redirect=${encodeURIComponent(routePath + location.search)}`
        }
        return Promise.reject(new Error(body.message || 'Not signed in'))
      }
      ElMessage.error(body.message || 'Operation failed')
      return Promise.reject(new Error(body.message || 'Operation failed'))
    },
    (error) => {
      const msg = error.response?.data?.message || error.message || 'Network error'
      ElMessage.error(msg)
      return Promise.reject(error)
    }
  )
  return instance
}

const request: AxiosInstance = createRequest('/api')

export default request
