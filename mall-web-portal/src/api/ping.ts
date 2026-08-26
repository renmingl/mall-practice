import request from './request'

// 骨架验证接口：验证网关路由 / 统一返回 / traceId 全链路（后端 PingController）
export function ping() {
  return request.get<string>('/common/ping')
}

export function trace() {
  return request.get<string>('/common/trace')
}

export function error() {
  return request.get<string>('/common/error')
}
