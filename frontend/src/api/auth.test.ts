import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { alterarSenha, entrar, usuarioAtual } from './auth'

const fetchMock = vi.fn()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

test('usuarioAtual devolve null em 401', async () => {
  fetchMock.mockResolvedValue(new Response(null, { status: 401 }))

  await expect(usuarioAtual()).resolves.toBeNull()
})

test('usuarioAtual devolve o usuário logado', async () => {
  const usuario = { id: 1, nome: 'Maria', email: 'maria@igreja.org', papel: 'VOLUNTARIO' }
  fetchMock.mockResolvedValue(new Response(JSON.stringify(usuario), { status: 200 }))

  await expect(usuarioAtual()).resolves.toEqual(usuario)
})

test('entrar envia e-mail e senha para /api/auth/login', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ id: 1 }), { status: 200 }))

  await entrar('maria@igreja.org', 'segredo123')

  const [url, init] = fetchMock.mock.calls[0]
  expect(url).toBe('/api/auth/login')
  expect(JSON.parse(init.body)).toEqual({ email: 'maria@igreja.org', senha: 'segredo123' })
})

test('alterarSenha usa PUT /api/auth/minha-senha', async () => {
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

  await alterarSenha('antiga123', 'nova-senha-1')

  const [url, init] = fetchMock.mock.calls[0]
  expect(url).toBe('/api/auth/minha-senha')
  expect(init.method).toBe('PUT')
  expect(JSON.parse(init.body)).toEqual({ senhaAtual: 'antiga123', novaSenha: 'nova-senha-1' })
})
