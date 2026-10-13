import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { listarCampanhas } from './campanhas'
import { listarDoacoes } from './doacoes'
import { listarDoadores } from './doadores'

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

test('listarDoacoes envia só os filtros preenchidos', async () => {
  await listarDoacoes({ campanhaId: 1, tipo: 'ITEM', pagina: 0 })

  expect(fetchMock.mock.calls[0][0]).toBe('/api/doacoes?campanhaId=1&tipo=ITEM&pagina=0')
})

test('listarDoacoes com período', async () => {
  await listarDoacoes({ de: '2026-10-01', ate: '2026-10-11', pagina: 1 })

  expect(fetchMock.mock.calls[0][0]).toBe('/api/doacoes?de=2026-10-01&ate=2026-10-11&pagina=1')
})

test('listarDoadores codifica a busca', async () => {
  await listarDoadores('José (47)', 0)

  expect(fetchMock.mock.calls[0][0]).toBe('/api/doadores?busca=Jos%C3%A9+%2847%29&pagina=0')
})

test('listarCampanhas aceita tamanho', async () => {
  await listarCampanhas({ status: 'ATIVA', pagina: 0, tamanho: 100 })

  expect(fetchMock.mock.calls[0][0]).toBe('/api/campanhas?status=ATIVA&pagina=0&tamanho=100')
})
