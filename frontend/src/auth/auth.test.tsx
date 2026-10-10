import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { definirAoNaoAutenticado } from '../api/client'
import App from '../App'
import { ADMIN, authMock, prepararAuth, VOLUNTARIO } from '../test/mockAuth'
import { renderComProviders } from '../test/render'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)
vi.mock('../api/client', async (original) => ({
  ...(await original<typeof import('../api/client')>()),
  definirAoNaoAutenticado: vi.fn(),
}))

beforeEach(() => {
  vi.clearAllMocks()
})

test('sem usuário, rota protegida redireciona para /login', async () => {
  prepararAuth(null)
  renderComProviders(<App />, { rota: '/campanhas' })

  expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
})

test('enquanto carrega, mostra indicador e não redireciona', () => {
  prepararAuth(null)
  authMock.usuarioAtual.mockReturnValue(new Promise(() => {}))
  renderComProviders(<App />, { rota: '/campanhas' })

  expect(screen.getByTestId('carregando')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Entrar' })).not.toBeInTheDocument()
})

test('VOLUNTARIO em /usuarios é redirecionado para /', async () => {
  prepararAuth(VOLUNTARIO)
  renderComProviders(<App />, { rota: '/usuarios' })

  expect(await screen.findByRole('heading', { name: 'Painel' })).toBeInTheDocument()
})

test('ADMIN acessa /usuarios', async () => {
  prepararAuth(ADMIN)
  renderComProviders(<App />, { rota: '/usuarios' })

  expect(await screen.findByRole('heading', { name: 'Usuários' })).toBeInTheDocument()
})

test('entrar renova o token CSRF depois do login', async () => {
  prepararAuth(null)
  authMock.entrar.mockResolvedValue(VOLUNTARIO)
  renderComProviders(<App />, { rota: '/login' })

  await userEvent.type(await screen.findByLabelText(/E-mail/), 'vitor@igreja.org')
  await userEvent.type(screen.getByLabelText(/Senha/), 'segredo123')
  await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

  await screen.findByRole('heading', { name: 'Painel' })
  const ordemEntrar = authMock.entrar.mock.invocationCallOrder[0]
  const ordensCsrf = authMock.obterCsrf.mock.invocationCallOrder
  expect(ordensCsrf.some((ordem) => ordem > ordemEntrar)).toBe(true)
})

test('401 durante o uso leva ao login com aviso de sessão expirada', async () => {
  prepararAuth(VOLUNTARIO)
  renderComProviders(<App />, { rota: '/campanhas' })
  await screen.findByRole('heading', { name: 'Campanhas' })

  const callback = vi.mocked(definirAoNaoAutenticado).mock.calls.at(-1)![0]
  act(() => callback())

  await waitFor(() => expect(screen.getByText('Sua sessão expirou. Entre novamente.')).toBeInTheDocument())
  expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument()
})
