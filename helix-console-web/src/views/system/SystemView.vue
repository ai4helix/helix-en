<template>
  <div class="sys">
    <header class="sys__top">

      <div class="sys__actions">
        <div class="seg-tabs">
          <button
            v-for="t in tabs"
            :key="t.value"
            class="seg-tabs__btn"
            :class="{ 'is-on': tab === t.value }"
            @click="tab = t.value"
          >
            {{ t.label }}
          </button>
        </div>
      </div>
    </header>

    <main class="sys__main">
      <template v-if="tab === 'user'">
        <div class="bar">
          <el-input v-model="userKeyword" placeholder="Search account / name / employee ID" clearable class="bar__search" @keyup.enter="loadUsers" @clear="loadUsers">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <button class="btn btn--primary" @click="openUserEdit()">
            <el-icon><Plus /></el-icon><span>Add User</span>
          </button>
        </div>

        <div class="table-card">
          <table class="tbl">
            <thead>
              <tr>
                <th>Account</th><th>Name</th><th>Employee ID</th><th>Organization</th><th>Roles</th><th>Status</th><th class="tbl__op">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in users" :key="u.userId">
                <td class="mono">{{ u.account }}</td>
                <td>{{ u.nickName }}</td>
                <td class="mono">{{ u.employeeId || '—' }}</td>
                <td>{{ u.organName || '—' }}</td>
                <td>
                  <span v-for="r in u.roleNames" :key="r" class="pill">{{ r }}</span>
                  <span v-if="!u.roleNames.length" class="muted">Not assigned</span>
                </td>
                <td>
                  <span class="status" :class="u.status === 1 ? 'is-on' : 'is-off'">
                    {{ u.status === 1 ? 'Enabled' : 'Disabled' }}
                  </span>
                </td>
                <td class="tbl__op">
                  <button class="op" title="Edit" @click="openUserEdit(u)"><el-icon :size="13"><EditPen /></el-icon></button>
                  <button class="op" :title="u.status === 1 ? 'Disable' : 'Enable'" @click="toggleUser(u)">
                    <el-icon :size="13"><component :is="u.status === 1 ? 'VideoPause' : 'VideoPlay'" /></el-icon>
                  </button>
                  <button class="op op--danger" title="Delete" @click="removeUser(u)"><el-icon :size="13"><Delete /></el-icon></button>
                </td>
              </tr>
              <tr v-if="!users.length"><td colspan="7" class="empty">No data</td></tr>
            </tbody>
          </table>
        </div>
      </template>

      <template v-else-if="tab === 'role'">
        <div class="bar">
          <div />
          <button class="btn btn--primary" @click="openRoleEdit()">
            <el-icon><Plus /></el-icon><span>Add Role</span>
          </button>
        </div>

        <div class="table-card">
          <table class="tbl">
            <thead>
              <tr><th>Role Name</th><th>Code</th><th>Description</th><th>Status</th><th class="tbl__op">Actions</th></tr>
            </thead>
            <tbody>
              <tr v-for="r in roles" :key="r.roleId">
                <td>{{ r.roleName }}</td>
                <td class="mono">{{ r.roleCode || '—' }}</td>
                <td class="muted">{{ r.roleDesc || '—' }}</td>
                <td>
                  <span class="status" :class="r.status === 1 ? 'is-on' : 'is-off'">
                    {{ r.status === 1 ? 'Enabled' : 'Disabled' }}
                  </span>
                </td>
                <td class="tbl__op">
                  <button class="op" title="Edit and authorize" @click="openRoleEdit(r)"><el-icon :size="13"><EditPen /></el-icon></button>
                  <button class="op op--danger" title="Delete" @click="removeRole(r)"><el-icon :size="13"><Delete /></el-icon></button>
                </td>
              </tr>
              <tr v-if="!roles.length"><td colspan="5" class="empty">No data</td></tr>
            </tbody>
          </table>
        </div>
      </template>

      <template v-else>
        <div class="bar">
          <div />
          <button class="btn btn--primary" @click="openOrgEdit()">
            <el-icon><Plus /></el-icon><span>Add Organization</span>
          </button>
        </div>

        <div class="table-card">
          <table class="tbl">
            <thead>
              <tr><th>Organization Name</th><th>Code</th><th>Email</th><th>Phone</th><th>Status</th><th class="tbl__op">Actions</th></tr>
            </thead>
            <tbody>
              <tr v-for="o in orgs" :key="o.organId">
                <td>{{ o.name }}</td>
                <td class="mono">{{ o.code }}</td>
                <td class="muted">{{ o.email || '—' }}</td>
                <td class="muted">{{ o.telephone || '—' }}</td>
                <td>
                  <span class="status" :class="o.status === 1 ? 'is-on' : 'is-off'">
                    {{ o.status === 1 ? 'Enabled' : 'Disabled' }}
                  </span>
                </td>
                <td class="tbl__op">
                  <button class="op" title="Edit" @click="openOrgEdit(o)"><el-icon :size="13"><EditPen /></el-icon></button>
                  <button class="op op--danger" title="Delete" @click="removeOrg(o)"><el-icon :size="13"><Delete /></el-icon></button>
                </td>
              </tr>
              <tr v-if="!orgs.length"><td colspan="6" class="empty">No data</td></tr>
            </tbody>
          </table>
        </div>
      </template>
    </main>

    <el-drawer v-model="userVisible" :title="userForm.userId ? 'Edit User' : 'Add User'" size="520px" destroy-on-close>
      <div class="form">
        <div class="grid">
          <div class="field">
            <label class="field__label">Account <i>*</i></label>
            <el-input v-model="userForm.account" placeholder="Starts with a letter, 4-16 characters" />
          </div>
          <div class="field">
            <label class="field__label">Name <i>*</i></label>
            <el-input v-model="userForm.nickName" />
          </div>
        </div>
        <div class="grid">
          <div class="field">
            <label class="field__label">Employee ID</label>
            <el-input v-model="userForm.employeeId" />
          </div>
          <div class="field">
            <label class="field__label">Organization</label>
            <el-select v-model="userForm.organId" style="width: 100%">
              <el-option v-for="o in orgs" :key="o.organId" :label="o.name" :value="o.organId" />
            </el-select>
          </div>
        </div>
        <div class="grid">
          <div class="field">
            <label class="field__label">Email</label>
            <el-input v-model="userForm.email" />
          </div>
          <div class="field">
            <label class="field__label">Phone</label>
            <el-input v-model="userForm.cellphone" />
          </div>
        </div>
        <div class="field">
          <label class="field__label">Roles</label>
          <el-select v-model="userForm.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roles" :key="r.roleId" :label="r.roleName" :value="r.roleId" />
          </el-select>
        </div>
        <div class="field">
          <label class="field__label">{{ userForm.userId ? 'Reset Password (leave blank to keep)' : 'Initial Password (blank for default)' }}</label>
          <el-input v-model="userForm.password" type="password" show-password placeholder="At least 8 characters with letters and digits" />
        </div>
      </div>
      <template #footer>
        <div class="drawer-foot">
          <button class="btn" @click="userVisible = false">Cancel</button>
          <button class="btn btn--primary" :disabled="submitting" @click="submitUser">Save</button>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="roleVisible" :title="roleForm.roleId ? 'Edit Role and Authorize' : 'Add Role'" size="560px" destroy-on-close>
      <div class="form">
        <div class="grid">
          <div class="field">
            <label class="field__label">Role Name <i>*</i></label>
            <el-input v-model="roleForm.roleName" />
          </div>
          <div class="field">
            <label class="field__label">Role Code</label>
            <el-input v-model="roleForm.roleCode" placeholder="e.g. ROLE_TENANT_OPERATOR" />
          </div>
        </div>
        <div class="field">
          <label class="field__label">Description</label>
          <el-input v-model="roleForm.roleDesc" />
        </div>
        <div class="field">
          <label class="field__label">Resource Authorization</label>
          <div class="perm">
            <div v-for="node in resourceTree" :key="node.id" class="perm__group">
              <label class="perm__parent">
                <el-checkbox
                  :model-value="isChecked(node.id)"
                  @change="(v: boolean) => toggleNode(node, v)"
                >
                  {{ node.name }}
                </el-checkbox>
              </label>
              <div v-if="node.children?.length" class="perm__children">
                <label v-for="c in node.children" :key="c.id" class="perm__child">
                  <el-checkbox
                    :model-value="checkedResources.includes(c.id!)"
                    @change="(v: boolean) => toggleNode(c, v)"
                  >
                    {{ c.name }}
                  </el-checkbox>
                </label>
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="drawer-foot">
          <button class="btn" @click="roleVisible = false">Cancel</button>
          <button class="btn btn--primary" :disabled="submitting" @click="submitRole">Save</button>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="orgVisible" :title="orgForm.organId ? 'Edit Organization' : 'Add Organization'" size="460px" destroy-on-close>
      <div class="form">
        <div class="field">
          <label class="field__label">Organization Name <i>*</i></label>
          <el-input v-model="orgForm.name" />
        </div>
        <div class="field">
          <label class="field__label">Organization Code <i>*</i></label>
          <el-input v-model="orgForm.code" placeholder="e.g. SH" />
        </div>
        <div class="grid">
          <div class="field">
            <label class="field__label">Email</label>
            <el-input v-model="orgForm.email" />
          </div>
          <div class="field">
            <label class="field__label">Phone</label>
            <el-input v-model="orgForm.telephone" />
          </div>
        </div>
      </div>
      <template #footer>
        <div class="drawer-foot">
          <button class="btn" @click="orgVisible = false">Cancel</button>
          <button class="btn btn--primary" :disabled="submitting" @click="submitOrg">Save</button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageUsers, createUser, updateUser, deleteUsers, changeUserStatus,
  pageRoles, createRole, updateRole, deleteRoles, getRole,
  resourceTree as fetchResourceTree, listOrganizations, createOrganization, updateOrganization, deleteOrganization,
  type SysUserVO, type SysRole, type SysTreeNode, type SysOrganization
} from '@/api/system'

const tabs = [
  { value: 'user' as const, label: 'Users' },
  { value: 'role' as const, label: 'Roles' },
  { value: 'org' as const, label: 'Organizations' }
]
const tab = ref<'user' | 'role' | 'org'>('user')

const users = ref<SysUserVO[]>([])
const roles = ref<SysRole[]>([])
const orgs = ref<SysOrganization[]>([])
const resourceTree = ref<SysTreeNode[]>([])
const userKeyword = ref('')
const submitting = ref(false)

const userVisible = ref(false)
const roleVisible = ref(false)
const orgVisible = ref(false)

const userForm = reactive<Partial<SysUserVO> & { password?: string }>({ roleIds: [] })
const roleForm = reactive<Partial<SysRole>>({})
const orgForm = reactive<Partial<SysOrganization>>({})
const checkedResources = ref<number[]>([])

onMounted(loadAll)

async function loadAll() {
  await Promise.all([loadUsers(), loadRoles(), loadOrgs(), loadResourceTree()])
  tab.value = tab.value
}

async function loadUsers() {
  const res = await pageUsers({ keyword: userKeyword.value || undefined, pageSize: 100 })
  users.value = res.records
}

async function loadRoles() {
  const res = await pageRoles({ pageSize: 100 })
  roles.value = res.records
}

async function loadOrgs() {
  orgs.value = await listOrganizations()
}

async function loadResourceTree() {
  resourceTree.value = await fetchResourceTree()
}

function openUserEdit(u?: SysUserVO) {
  Object.keys(userForm).forEach((k) => delete (userForm as any)[k])
  Object.assign(userForm, u ? { ...u, password: '' } : { roleIds: [] })
  userVisible.value = true
}

async function submitUser() {
  if (!userForm.account?.trim() || !userForm.nickName?.trim()) {
    ElMessage.warning('Account and name are required')
    return
  }
  submitting.value = true
  try {
    if (userForm.userId) {
      await updateUser(userForm)
    } else {
      await createUser(userForm)
    }
    ElMessage.success('Saved')
    userVisible.value = false
    await loadUsers()
  } finally {
    submitting.value = false
  }
}

async function toggleUser(u: SysUserVO) {
  await changeUserStatus([u.userId], u.status === 1 ? 0 : 1)
  ElMessage.success('Status updated')
  await loadUsers()
}

async function removeUser(u: SysUserVO) {
  await ElMessageBox.confirm(`Delete user "${u.nickName}"?`, 'Delete Confirmation', {
    type: 'warning', confirmButtonText: 'Delete', cancelButtonText: 'Cancel'
  })
  await deleteUsers([u.userId])
  ElMessage.success('Deleted')
  await loadUsers()
}

async function openRoleEdit(r?: SysRole) {
  Object.keys(roleForm).forEach((k) => delete (roleForm as any)[k])
  checkedResources.value = []
  if (r) {
    Object.assign(roleForm, r)
    const detail = await getRole(r.roleId)
    checkedResources.value = detail.resourceIds || []
  }
  roleVisible.value = true
}

function isChecked(id: number) {
  return checkedResources.value.includes(id)
}

function toggleNode(node: SysTreeNode, checked: boolean) {
  const ids = [node.id, ...(node.children || []).map((c) => c.id)]
  if (checked) {
    ids.forEach((id) => {
      if (!checkedResources.value.includes(id)) checkedResources.value.push(id)
    })
  } else {
    checkedResources.value = checkedResources.value.filter((id) => !ids.includes(id))
  }
}

async function submitRole() {
  if (!roleForm.roleName?.trim()) {
    ElMessage.warning('Role name is required')
    return
  }
  submitting.value = true
  try {
    const payload = { ...roleForm, resourceIds: checkedResources.value }
    if (roleForm.roleId) {
      await updateRole(payload)
    } else {
      await createRole(payload)
    }
    ElMessage.success('Saved')
    roleVisible.value = false
    await loadRoles()
  } finally {
    submitting.value = false
  }
}

async function removeRole(r: SysRole) {
  await ElMessageBox.confirm(`Delete role "${r.roleName}"?`, 'Delete Confirmation', {
    type: 'warning', confirmButtonText: 'Delete', cancelButtonText: 'Cancel'
  })
  await deleteRoles([r.roleId])
  ElMessage.success('Deleted')
  await loadRoles()
}

function openOrgEdit(o?: SysOrganization) {
  Object.keys(orgForm).forEach((k) => delete (orgForm as any)[k])
  if (o) Object.assign(orgForm, o)
  orgVisible.value = true
}

async function submitOrg() {
  if (!orgForm.name?.trim() || !orgForm.code?.trim()) {
    ElMessage.warning('Name and code are required')
    return
  }
  submitting.value = true
  try {
    if (orgForm.organId) {
      await updateOrganization(orgForm)
    } else {
      await createOrganization(orgForm)
    }
    ElMessage.success('Saved')
    orgVisible.value = false
    await loadOrgs()
  } finally {
    submitting.value = false
  }
}

async function removeOrg(o: SysOrganization) {
  await ElMessageBox.confirm(`Delete organization "${o.name}"?`, 'Delete Confirmation', {
    type: 'warning', confirmButtonText: 'Delete', cancelButtonText: 'Cancel'
  })
  await deleteOrganization(o.organId)
  ElMessage.success('Deleted')
  await loadOrgs()
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.sys {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--c-bg);
  overflow: hidden;

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: var(--h-header);
    flex-shrink: 0;
    padding: 0 var(--sp-4);
    gap: var(--sp-4);
    background: var(--c-surface-raised);
    border-bottom: 1px solid var(--c-border);
  }

  &__main {
    flex: 1;
    overflow-y: auto;
    padding: var(--sp-5);
  }
}

.seg-tabs {
  display: flex;
  background: var(--c-fill-quaternary);
  border-radius: var(--r-sm);
  padding: 2px;

  &__btn {
    height: 26px;
    padding: 0 var(--sp-4);
    border: none;
    background: transparent;
    color: var(--c-text-secondary);
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    border-radius: var(--r-xs);
    cursor: pointer;
    transition: all var(--dur-fast) var(--ease-standard);

    &.is-on {
      background: var(--c-surface);
      color: var(--c-text);
      box-shadow: var(--sh-xs);
    }
  }
}

.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sp-4);

  &__search {
    width: 260px;
  }
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-sm);
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  color: var(--c-text);
  font-size: var(--fs-sm);
  font-weight: var(--fw-medium);
  cursor: pointer;
  box-shadow: var(--sh-xs);
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover:not(:disabled) {
    border-color: var(--c-border-strong);
    box-shadow: var(--sh-sm);
  }

  &:active:not(:disabled) {
    transform: scale(0.97);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &--primary {
    background: var(--c-primary);
    border-color: transparent;
    color: var(--c-text-inverse);

    &:hover:not(:disabled) {
      background: var(--c-primary-hover);
    }
  }
}

.table-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  overflow: hidden;
  box-shadow: var(--sh-xs);
}

.tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--fs-sm);

  th {
    text-align: left;
    padding: 11px var(--sp-4);
    font-size: var(--fs-2xs);
    font-weight: var(--fw-semibold);
    color: var(--c-text-tertiary);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    background: var(--c-surface-sunken);
    border-bottom: 1px solid var(--c-separator);
    white-space: nowrap;
  }

  td {
    padding: 11px var(--sp-4);
    border-bottom: 1px solid var(--c-separator);
    color: var(--c-text);
    vertical-align: middle;
  }

  tbody tr {
    transition: background-color var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-fill-quaternary);
    }

    &:last-child td {
      border-bottom: none;
    }
  }

  &__op {
    width: 120px;
    text-align: right;
    white-space: nowrap;
  }

  .empty {
    text-align: center;
    padding: var(--sp-10);
    color: var(--c-text-tertiary);
  }
}

.mono {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
}

.muted {
  color: var(--c-text-tertiary);
}

.pill {
  display: inline-block;
  padding: 1px 7px;
  margin-right: 4px;
  border-radius: var(--r-full);
  background: var(--c-primary-soft);
  color: var(--c-primary);
  font-size: var(--fs-2xs);
  font-weight: var(--fw-medium);
}

.status {
  display: inline-block;
  padding: 1px 7px;
  border-radius: var(--r-full);
  font-size: var(--fs-2xs);
  font-weight: var(--fw-medium);

  &.is-on {
    background: var(--c-success-soft);
    color: var(--c-success);
  }

  &.is-off {
    background: var(--c-fill-tertiary);
    color: var(--c-text-tertiary);
  }
}

.op {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin-left: 2px;
  border: none;
  border-radius: var(--r-xs);
  background: transparent;
  color: var(--c-text-tertiary);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    background: var(--c-fill-tertiary);
    color: var(--c-text);
  }

  &--danger:hover {
    background: var(--c-danger-soft);
    color: var(--c-danger);
  }
}

.form {
  padding-bottom: var(--sp-4);
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
}

.field {
  margin-bottom: var(--sp-3);

  &__label {
    display: block;
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    color: var(--c-text-secondary);
    margin-bottom: 6px;

    i {
      color: var(--c-danger);
      font-style: normal;
    }
  }
}

.perm {
  max-height: 340px;
  overflow-y: auto;
  padding: var(--sp-3);
  border-radius: var(--r-sm);
  background: var(--c-surface-sunken);
  border: 1px solid var(--c-border);

  &__group {
    padding: var(--sp-2) 0;

    &:not(:last-child) {
      border-bottom: 1px solid var(--c-separator);
    }
  }

  &__children {
    display: flex;
    flex-wrap: wrap;
    gap: 4px var(--sp-4);
    padding-left: 24px;
    margin-top: 4px;
  }

  &__child,
  &__parent {
    font-size: var(--fs-sm);
  }
}

.drawer-foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}
</style>
