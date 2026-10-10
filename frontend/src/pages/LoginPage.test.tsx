import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { ApiError } from '../api/client'
import App from '../App'
import { authMock, prepararAuth, VOLUNTARIO } from '../test/mockAuth'
import { renderComProviders } from '../test/render'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)

beforeEach(() => {
  vi.clearAllMocks()
  prepararAuth(null)
})

async function preencherEEntrar(email: string, senha: string) {
  renderComProviders(<App />, { rota: '/login' })
  if (email) {
    await userEvent.type(await screen.findByLabelText(/E-mail/), email)
  }
  if (senha) {
    await userEvent.type(screen.getByLabelText(/Senha/), senha)
  }
  await userEvent.click(await screen.findByRole('button', { name: 'Entrar' }))
}

test('login válido navega para o painel', async () => {
  authMock.entrar.mockResolvedValue(VOLUNTARIO)

  await preencherEEntrar('vitor@igreja.org', 'segredo123')

  expect(await screen.findByRole('heading', { name: 'Painel' })).toBeInTheDocument()
  expect(authMock.entrar).toHaveBeenCalledWith('vitor@igreja.org', 'segredo123')
})

test('401 mostra "E-mail ou senha inválidos."', async () => {
  authMock.entrar.mockRejectedValue(new ApiError(401, { status: 401, detail: 'E-mail ou senha inválidos.' }))

  await preencherEEntrar('vitor@igreja.org', 'errada123')

  expect(await screen.findByText('E-mail ou senha inválidos.')).toBeInTheDocument()
})

test('429 mostra a mensagem de bloqueio vinda da API', async () => {
  const detail = 'Muitas tentativas. Tente novamente em 15 minutos.'
  authMock.entrar.mockRejectedValue(new ApiError(429, { status: 429, detail }))

  await preencherEEntrar('vitor@igreja.org', 'errada123')

  expect(await screen.findByText(detail)).toBeInTheDocument()
})

test('campos vazios mostram validação sem chamar a API', async () => {
  await preencherEEntrar('', '')

  expect(await screen.findByText('Informe um e-mail válido.')).toBeInTheDocument()
  expect(screen.getByText('Informe a senha.')).toBeInTheDocument()
  expect(authMock.entrar).not.toHaveBeenCalled()
})
