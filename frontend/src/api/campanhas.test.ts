import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { alterarStatusCampanha, excluirNecessidade, listarCampanhas } from './campanhas'
import { listarUsuarios } from './usuarios'

const fetchMock = vi.fn()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockImplementation(
    async () => new Response(JSON.stringify({ itens: [], pagina: 0, tamanho: 20, total: 0, totalPaginas: 0 })),
  )
})

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

test('listarCampanhas monta a query com status e página', async () => {
  await listarCampanhas({ status: 'ATIVA', pagina: 2 })

  expect(fetchMock.mock.calls[0][0]).toBe('/api/campanhas?status=ATIVA&pagina=2')
})

test('listarCampanhas sem status omite o parâmetro', async () => {
  await listarCampanhas({ pagina: 0 })

  expect(fetchMock.mock.calls[0][0]).toBe('/api/campanhas?pagina=0')
})

test('alterarStatusCampanha usa PATCH com o novo status', async () => {
  await alterarStatusCampanha(3, 'ENCERRADA')

  const [url, init] = fetchMock.mock.calls[0]
  expect(url).toBe('/api/campanhas/3/status')
  expect(init.method).toBe('PATCH')
  expect(JSON.parse(init.body)).toEqual({ status: 'ENCERRADA' })
})

test('excluirNecessidade usa DELETE', async () => {
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

  await excluirNecessidade(10)

  expect(fetchMock.mock.calls[0][0]).toBe('/api/necessidades/10')
  expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
})

test('listarUsuarios envia a página', async () => {
  await listarUsuarios(1)

  expect(fetchMock.mock.calls[0][0]).toBe('/api/usuarios?pagina=1')
})
