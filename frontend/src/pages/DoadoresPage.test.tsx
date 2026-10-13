import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import * as doadoresApi from '../api/doadores'
import App from '../App'
import { MARIA } from '../test/fixtures'
import { prepararAuth, VOLUNTARIO } from '../test/mockAuth'
import { renderComProviders } from '../test/render'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)
vi.mock('../api/doadores')

const api = vi.mocked(doadoresApi)

beforeEach(() => {
  vi.clearAllMocks()
  prepararAuth(VOLUNTARIO)
  api.listarDoadores.mockResolvedValue({ itens: [MARIA], pagina: 0, tamanho: 20, total: 1, totalPaginas: 1 })
  api.criarDoador.mockResolvedValue({ ...MARIA, id: 4, nome: 'José' })
})

test('busca chama a API com o termo digitado', async () => {
  renderComProviders(<App />, { rota: '/doadores' })
  await screen.findByText('Maria Souza')

  await userEvent.type(screen.getByLabelText('Buscar por nome ou telefone'), 'maria')

  await waitFor(() => expect(api.listarDoadores).toHaveBeenLastCalledWith('maria', 0))
})

test('mostra os dados e leva ao detalhe do doador', async () => {
  renderComProviders(<App />, { rota: '/doadores' })

  expect(await screen.findByText('(47) 99999-1234')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /Maria Souza/ })).toHaveAttribute('href', '/doadores/3')
})

test('cadastra doador e recarrega a lista', async () => {
  renderComProviders(<App />, { rota: '/doadores' })
  await screen.findByText('Maria Souza')

  await userEvent.click(screen.getByRole('button', { name: 'Novo doador' }))
  const dialogo = await screen.findByRole('dialog')
  await userEvent.type(within(dialogo).getByLabelText(/Nome/), 'José')
  await userEvent.type(within(dialogo).getByLabelText(/Telefone/), '47 98888-0000')
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }))

  await waitFor(() =>
    expect(api.criarDoador).toHaveBeenCalledWith({
      nome: 'José',
      telefone: '47 98888-0000',
      email: null,
      observacao: null,
    }),
  )
  await waitFor(() => expect(api.listarDoadores).toHaveBeenCalledTimes(2))
})

test('lista vazia mostra mensagem', async () => {
  api.listarDoadores.mockResolvedValue({ itens: [], pagina: 0, tamanho: 20, total: 0, totalPaginas: 0 })
  renderComProviders(<App />, { rota: '/doadores' })

  expect(await screen.findByText('Nenhum doador encontrado.')).toBeInTheDocument()
})
