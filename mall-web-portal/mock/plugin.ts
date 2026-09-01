import type { Plugin } from 'vite'
import type { IncomingMessage } from 'node:http'

// ---------- Mock 演示模式插件（VITE_MOCK=true 时挂载） ----------
// dev server 中间件层拦截 /api 请求，按「方法 + 路径模式」返回本地模拟数据，
// 不启动任何后端服务即可浏览全部页面。未匹配的请求返回 404 JSON。

export interface MockContext {
  query: Record<string, string>
  params: Record<string, string>
  body: unknown
}

export interface MockHandler {
  method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  /** 路径模式，:param 为路径参数，如 /product/detail/:id */
  url: string
  /** 返回 data 部分，自动包装为 { code: 200, message: 'ok', data }；返回 { _raw } 则原样输出 */
  handler: (ctx: MockContext) => unknown
}

/** 读取 JSON 请求体（multipart 直接忽略） */
function readBody(req: IncomingMessage): Promise<unknown> {
  const ct = req.headers['content-type'] || ''
  if (ct.includes('multipart/form-data')) return Promise.resolve(undefined)
  return new Promise((resolve) => {
    let raw = ''
    req.on('data', (c) => (raw += c))
    req.on('end', () => {
      if (!raw) return resolve(undefined)
      try {
        resolve(JSON.parse(raw))
      } catch {
        resolve(raw)
      }
    })
    req.on('error', () => resolve(undefined))
  })
}

/** 路径匹配：:param 占位，段数必须一致 */
function matchPath(pattern: string, path: string): Record<string, string> | null {
  const pSegs = pattern.split('/').filter(Boolean)
  const aSegs = path.split('/').filter(Boolean)
  if (pSegs.length !== aSegs.length) return null
  const params: Record<string, string> = {}
  for (let i = 0; i < pSegs.length; i++) {
    if (pSegs[i].startsWith(':')) params[pSegs[i].slice(1)] = aSegs[i]
    else if (pSegs[i] !== aSegs[i]) return null
  }
  return params
}

export function mockPlugin(handlers: MockHandler[]): Plugin {
  return {
    name: 'mock-server',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        if (!req.url || !req.url.startsWith('/api/')) return next()
        const u = new URL(req.url, 'http://localhost')
        const path = u.pathname.replace(/^\/api/, '')
        const method = (req.method || 'GET').toUpperCase()
        // axios 数组参数序列化为 skuIds[]=1&skuIds[]=2，去掉 [] 后缀
        const query: Record<string, string> = {}
        u.searchParams.forEach((v, k) => {
          query[k.replace(/\[\]$/, '')] = v
        })
        const body = await readBody(req)
        for (const h of handlers) {
          if (h.method !== method) continue
          const params = matchPath(h.url, path)
          if (!params) continue
          const data = await h.handler({ query, params, body })
          const payload =
            data && typeof data === 'object' && '_raw' in (data as object)
              ? (data as { _raw: unknown })._raw
              : { code: 200, message: 'ok', data }
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify(payload))
          return
        }
        res.statusCode = 404
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ code: 404, message: `mock 未匹配: ${method} ${path}` }))
      })
    }
  }
}
