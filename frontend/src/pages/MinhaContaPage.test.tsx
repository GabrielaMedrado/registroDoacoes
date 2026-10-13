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
  prepararAuth(VOLUNTARIO)
})

async function preencher(atual: string, nova: string, confirmacao: string) {
  renderComProviders(<App />, { rota: '/minha-conta' })
  await userEvent.type(await screen.findByLabelText(/Senha atual/), atual)
  await userEvent.type(screen.getByLabelText(/^Nova senha/), nova)
  await userEvent.type(screen.getByLabelText(/Confirmar nova senha/), confirmacao)
  await userEvent.click(screen.getByRole('button', { name: 'Alterar senha' }))
}

test('nova senha com menos de 8 caracteres é bloqueada no formulário', async () => {
  await preencher('antiga123', '1234567', '1234567')

  expect(await screen.findByText('A nova senha deve ter ao menos 8 caracteres.')).toBeInTheDocument()
  expect(authMock.alterarSenha).not.toHaveBeenCalled()
})

test('confirmação diferente da nova senha é bloqueada', async () => {
  await preencher('antiga123', 'nova-senha-1', 'outra-senha-1')

  expect(await screen.findByText('As senhas não conferem.')).toBeInTheDocument()
  expect(authMock.alterarSenha).not.toHaveBeenCalled()
})

test('erro de campo senhaAtual da API aparece no campo', async () => {
  authMock.alterarSenha.mockRejectedValue(
    new ApiError(400, {
      status: 400,
      detail: 'Dados inválidos.',
      erros: [{ campo: 'senhaAtual', mensagem: 'Senha atual incorreta.' }],
    }),
  )

  await preencher('errada123', 'nova-senha-1', 'nova-senha-1')

  expect(await screen.findByText('Senha atual incorreta.')).toBeInTheDocument()
})

test('sucesso mostra "Senha alterada com sucesso." e limpa o formulário', async () => {
  await preencher('antiga123', 'nova-senha-1', 'nova-senha-1')

  expect(await screen.findByText('Senha alterada com sucesso.')).toBeInTheDocument()
  expect(authMock.alterarSenha).toHaveBeenCalledWith('antiga123', 'nova-senha-1')
  expect(screen.getByLabelText(/Senha atual/)).toHaveValue('')
})
