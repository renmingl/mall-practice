import { defineStore } from 'pinia'

// 应用级状态（阶段 0 占位：traceId 展示用；登录态 / 用户信息随阶段 1 账号体系补充）
export const useAppStore = defineStore('app', {
  state: () => ({
    traceId: ''
  }),
  actions: {
    setTraceId(traceId: string) {
      this.traceId = traceId
    }
  }
})
