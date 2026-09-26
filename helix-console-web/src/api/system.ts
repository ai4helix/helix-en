import request from './request'
import type { PageResult } from '@/types/flow'


export interface LoginResult {
  token: string
  userId: number
  account: string
  nickName: string
  organId?: number
  organName?: string
  roles: string[]
  permissions: string[]
}

export interface CurrentUser {
  userId: number
  account: string
  nickName: string
  organId?: number
  organName?: string
  userType?: number
  roleCodes: string[]
  permissions: string[]
}

export function login(account: string, password: string): Promise<LoginResult> {
  return request.post('/auth/login', { account, password })
}

export function currentUser(): Promise<CurrentUser> {
  return request.get('/auth/current-user')
}

export function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  return request.post('/auth/change-password', { oldPassword, newPassword })
}


export interface RegisterCaptchaRsp {
  sent: boolean
  code?: string
  cooldown: number
}

export interface RegisterRsp {
  userId: number
  organId: number
  organName: string
  account: string
  roleCode: string
}

export function sendRegisterCaptcha(phone: string): Promise<RegisterCaptchaRsp> {
  return request.post('/auth/register/captcha', { phone })
}

export function registerTenant(data: {
  phone: string
  code: string
  password: string
  orgName: string
  nickName?: string
}): Promise<RegisterRsp> {
  return request.post('/auth/register', data)
}


export interface SysUserVO {
  userId: number
  organId?: number
  organName?: string
  employeeId?: string
  account: string
  nickName: string
  email?: string
  cellphone?: string
  qq?: string
  status?: number
  latestTime?: string
  latestIp?: string
  birth?: string
  author?: string
  roleIds: number[]
  roleNames: string[]
}

export function pageUsers(params: {
  keyword?: string
  organId?: number
  status?: number
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<SysUserVO>> {
  return request.get('/system/user/page', { params })
}

export function getUser(userId: number): Promise<SysUserVO> {
  return request.get(`/system/user/${userId}`)
}

export function createUser(data: Partial<SysUserVO> & { password?: string }): Promise<number> {
  return request.post('/system/user', data)
}

export function updateUser(data: Partial<SysUserVO> & { password?: string }): Promise<void> {
  return request.put('/system/user', data)
}

export function deleteUsers(userIds: number[]): Promise<void> {
  return request.delete('/system/user', { data: userIds })
}

export function changeUserStatus(userIds: number[], status: number): Promise<void> {
  return request.post('/system/user/status', null, { params: { userIds: userIds.join(','), status } })
}

export function resetPassword(userId: number, newPassword: string): Promise<void> {
  return request.post(`/system/user/${userId}/reset-password`, { newPassword })
}


export interface SysRole {
  roleId: number
  organId?: number
  roleName: string
  roleCode?: string
  roleDesc?: string
  author?: string
  birth?: string
  status?: number
}

export function pageRoles(params: {
  keyword?: string
  organId?: number
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<SysRole>> {
  return request.get('/system/role/page', { params })
}

export function listRoles(): Promise<SysRole[]> {
  return request.get('/system/role/list')
}

export function getRole(roleId: number): Promise<{ role: SysRole; resourceIds: number[] }> {
  return request.get(`/system/role/${roleId}`)
}

export function createRole(data: Partial<SysRole> & { resourceIds?: number[] }): Promise<number> {
  return request.post('/system/role', data)
}

export function updateRole(data: Partial<SysRole> & { resourceIds?: number[] }): Promise<void> {
  return request.put('/system/role', data)
}

export function deleteRoles(roleIds: number[]): Promise<void> {
  return request.delete('/system/role', { data: roleIds })
}

export function bindRoleResources(roleId: number, resourceIds: number[]): Promise<void> {
  return request.post(`/system/role/${roleId}/resources`, resourceIds)
}


export interface SysTreeNode {
  id: number
  name: string
  code?: string
  parentId?: number
  url?: string
  icon?: string
  description?: string
  status?: number
  children?: SysTreeNode[]
}

export function resourceTree(): Promise<SysTreeNode[]> {
  return request.get('/system/resource/tree')
}

export function menuTree(): Promise<SysTreeNode[]> {
  return request.get('/system/resource/menu')
}

export function getResource(id: number): Promise<any> {
  return request.get(`/system/resource/${id}`)
}

export function createResource(data: Partial<SysTreeNode>): Promise<number> {
  return request.post('/system/resource', data)
}

export function updateResource(data: Partial<SysTreeNode>): Promise<void> {
  return request.put('/system/resource', data)
}

export function deleteResource(id: number): Promise<void> {
  return request.delete(`/system/resource/${id}`)
}


export interface SysOrganization {
  organId: number
  name: string
  code: string
  email?: string
  telephone?: string
  status?: number
  author?: string
  birth?: string
}

export function listOrganizations(): Promise<SysOrganization[]> {
  return request.get('/system/organization/list')
}

export function createOrganization(data: Partial<SysOrganization>): Promise<number> {
  return request.post('/system/organization', data)
}

export function updateOrganization(data: Partial<SysOrganization>): Promise<void> {
  return request.put('/system/organization', data)
}

export function deleteOrganization(organId: number): Promise<void> {
  return request.delete(`/system/organization/${organId}`)
}
