import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import * as campanhasApi from '../api/campanhas'
import type { Campanha } from '../api/campanhas'
import App from '../App'
import { ADMIN, prepararAuth, VOLUNTARIO } from '../test/mockAuth'
import { renderComProviders } from '../test/render'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)
vi.mock('../api/campanhas')

const INVERNO: Campanha = {
  id: 1,
  titulo: 'Inverno solidário',
  descricao: 'Agasalhos',
  dataInicio: '2026-10-20',
  dataFim: '2026-11-30',
  status: 'ATIVA',
  metaValor: 1500.5,
}

const api = vi.mocked(campanhasApi)

beforeEach(() => {
  vi.clearAllMocks()
  prepararAuth(ADMIN)
  api.listarCampanhas.mockResolvedValue({ itens: [INVERNO], pagina: 0, tamanho: 20, total: 1, totalPaginas: 1 })
  api.criarCampanha.mockResolvedValue({ ...INVERNO, id: 2, status: 'RASCUNHO' })
})

test('mostra campanhas ativas por padrão', async () => {
  renderComProviders(<App />, { rota: '/campanhas' })

  expect(await screen.findByText('Inverno solidário')).toBeInTheDocument()
  expect(api.listarCampanhas).toHaveBeenCalledWith({ status: 'ATIVA', pagina: 0 })
  expect(screen.getByText('20/10/2026 – 30/11/2026')).toBeInTheDocument()
  expect(screen.getByText('Ativa')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /Inverno solidário/ })).toHaveAttribute('href', '/campanhas/1')
})

test('trocar o filtro para Todas recarrega sem status', async () => {
  renderComProviders(<App />, { rota: '/campanhas' })
  await screen.findByText('Inverno solidário')

  await userEvent.click(screen.getByRole('radio', { name: 'Todas' }))

  await waitFor(() => expect(api.listarCampanhas).toHaveBeenLastCalledWith({ status: undefined, pagina: 0 }))
})

test('lista vazia mostra mensagem', async () => {
  api.listarCampanhas.mockResolvedValue({ itens: [], pagina: 0, tamanho: 20, total: 0, totalPaginas: 0 })
  renderComProviders(<App />, { rota: '/campanhas' })

  expect(await screen.findByText('Nenhuma campanha encontrada.')).toBeInTheDocument()
})

test('voluntário não vê "Nova campanha"', async () => {
  prepararAuth(VOLUNTARIO)
  renderComProviders(<App />, { rota: '/campanhas' })
  await screen.findByText('Inverno solidário')

  expect(screen.queryByRole('button', { name: 'Nova campanha' })).not.toBeInTheDocument()
})

async function abrirFormulario() {
  renderComProviders(<App />, { rota: '/campanhas' })
  await userEvent.click(await screen.findByRole('button', { name: 'Nova campanha' }))
  return await screen.findByRole('dialog')
}

function definirData(dialogo: HTMLElement, rotulo: RegExp, valor: string) {
  fireEvent.change(within(dialogo).getByLabelText(rotulo), { target: { value: valor } })
}

test('admin cria campanha com meta em reais', async () => {
  const dialogo = await abrirFormulario()

  await userEvent.type(within(dialogo).getByLabelText(/Título/), 'Natal feliz')
  definirData(dialogo, /Data de início/, '2026-10-20')
  await userEvent.type(within(dialogo).getByLabelText(/Meta/), '1500,50')
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }))

  await waitFor(() =>
    expect(api.criarCampanha).toHaveBeenCalledWith({
      titulo: 'Natal feliz',
      descricao: null,
      dataInicio: '2026-10-20',
      dataFim: null,
      metaValor: 1500.5,
    }),
  )
})

test('término antes do início é bloqueado no formulário', async () => {
  const dialogo = await abrirFormulario()

  await userEvent.type(within(dialogo).getByLabelText(/Título/), 'Natal feliz')
  definirData(dialogo, /Data de início/, '2026-10-20')
  definirData(dialogo, /Data de término/, '2026-10-19')
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }))

  expect(await screen.findByText('A data de término deve ser igual ou posterior à de início.')).toBeInTheDocument()
  expect(api.criarCampanha).not.toHaveBeenCalled()
})
