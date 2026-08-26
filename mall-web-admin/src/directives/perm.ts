import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令：v-perm="'system:user:add'" 或 v-perm="['a', 'b']"（任一命中即显示）
 * 无权限时移除元素；超级管理员（perms 含 *）全放行
 */
export const vPerm: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const perms = userStore.user?.perms ?? []
    const required = Array.isArray(binding.value) ? binding.value : [binding.value]
    const allowed = perms.includes('*') || required.some((p) => perms.includes(p))
    if (!allowed) {
      el.parentNode?.removeChild(el)
    }
  }
}
