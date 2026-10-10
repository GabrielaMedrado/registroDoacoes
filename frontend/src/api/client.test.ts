import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { api, ApiError, definirAoNaoAutenticado } from './client'

const fetchMock = vi.fn()

function resposta(status: number, corpo?: unknown): Response {
  return new Response(corpo === undefined ? null : JSON.stringify(corpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
  definirAoNaoAutenticado(() => {})
})

test('envia X-XSRF-TOKEN lido do cookie em métodos que alteram dados', async () => {
  document.cookie = 'XSRF-TOKEN=abc123; path=/'
  fetchMock.mockResolvedValue(resposta(200, { ok: true }))

  await api('/api/x', { method: 'POST', body: { a: 1 } })

  const [url, init] = fetchMock.mock.calls[0]
  expect(url).toBe('/api/x')
  expect(init.method).toBe('POST')
  expect(init.credentials).toBe('same-origin')
  expect(init.headers['X-XSRF-TOKEN']).toBe('abc123')
  expect(init.headers['Content-Type']).toBe('application/json')
  expect(init.body).toBe('{"a":1}')
})

test('não envia X-XSRF-TOKEN em GET', async () => {
  document.cookie = 'XSRF-TOKEN=abc123; path=/'
  fetchMock.mockResolvedValue(resposta(200, []))

  await api('/api/x')

  const [, init] = fetchMock.mock.calls[0]
  expect(init.method).toBe('GET')
  expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
})

test('lança ApiError com problem e erros de campo', async () => {
  fetchMock.mockResolvedValue(
    resposta(400, { status: 400, detail: 'Dados inválidos', erros: [{ campo: 'email', mensagem: 'inválido' }] }),
  )

  const erro = await api('/api/x', { method: 'POST', body: {} }).catch((e: unknown) => e)

  expect(erro).toBeInstanceOf(ApiError)
  expect((erro as ApiError).status).toBe(400)
  expect((erro as ApiError).message).toBe('Dados inválidos')
  expect((erro as ApiError).problem?.erros?.[0].campo).toBe('email')
})

test('erro sem corpo usa mensagem padrão', async () => {
  fetchMock.mockResolvedValue(new Response(null, { status: 500 }))

  const erro = await api('/api/x').catch((e: unknown) => e)

  expect((erro as ApiError).message).toBe('Erro inesperado. Tente novamente.')
})

test('401 fora de /api/auth chama o callback de não autenticado', async () => {
  const callback = vi.fn()
  definirAoNaoAutenticado(callback)
  fetchMock.mockResolvedValue(resposta(401))

  await expect(api('/api/campanhas')).rejects.toBeInstanceOf(ApiError)
  expect(callback).toHaveBeenCalledTimes(1)
})

test('401 em /api/auth/login não chama o callback', async () => {
  const callback = vi.fn()
  definirAoNaoAutenticado(callback)
  fetchMock.mockResolvedValue(resposta(401, { status: 401, detail: 'E-mail ou senha inválidos.' }))

  await expect(api('/api/auth/login', { method: 'POST', body: {} })).rejects.toBeInstanceOf(ApiError)
  expect(callback).not.toHaveBeenCalled()
})

test('401 em /api/auth/minha-senha (sessão expirada) chama o callback', async () => {
  const callback = vi.fn()
  definirAoNaoAutenticado(callback)
  fetchMock.mockResolvedValue(new Response(null, { status: 401 }))

  await expect(api('/api/auth/minha-senha', { method: 'PUT', body: {} })).rejects.toBeInstanceOf(ApiError)
  expect(callback).toHaveBeenCalledTimes(1)
})

test('401 em /api/auth/me não chama o callback', async () => {
  const callback = vi.fn()
  definirAoNaoAutenticado(callback)
  fetchMock.mockResolvedValue(new Response(null, { status: 401 }))

  await expect(api('/api/auth/me')).rejects.toBeInstanceOf(ApiError)
  expect(callback).not.toHaveBeenCalled()
})

test('204 resolve undefined', async () => {
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

  await expect(api('/api/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
})
