import { mockPlugin, type MockHandler } from './plugin'
import { handlers } from './handlers'

// Mock 演示模式入口：VITE_MOCK=true 时由 vite.config.ts 挂载
export { mockPlugin }
export type { MockHandler }
export const mockHandlers: MockHandler[] = handlers
