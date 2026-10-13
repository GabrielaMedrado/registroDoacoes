import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { ApiError } from '../api/client'
import * as usuariosApi from '../api/usuarios'
import type { Usuario } from '../api/usuarios'
import App from '../App'
import { prepararAuth, ADMIN } from '../test/mockAuth'
import { renderComProviders } from '../test/render'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)
vi.mock('../api/usuarios')

const ANA: Usuario = { id: 1, nome: 'Ana Admin', email: 'ana@igreja.org', papel: 'ADMIN', ativo: true, criadoEm: '' }
const VITOR: Usuario = {
  id: 2,
  nome: 'Vitor',
  email: 'vitor@igreja.org',
  papel: 'VOLUNTARIO',
  ativo: false,
  criadoEm: '',
}

const api = vi.mocked(usuariosApi)

beforeEach(() => {
  vi.clearAllMocks()
  prepararAuth(ADMIN)
  api.listarUsuarios.mockResolvedValue({ itens: [ANA, VITOR], pagina: 0, tamanho: 20, total: 2, totalPaginas: 1 })
  api.criarUsuario.mockResolvedValue(VITOR)
  api.atualizarUsuario.mockResolvedValue(ANA)
  api.redefinirSenhaUsuario.mockResolvedValue(undefined)
})

async function abrirPagina() {
  renderComProviders(<App />, { rota: '/usuarios' })
  return await screen.findByRole('table')
}

function linhaDe(tabela: HTMLElement, nome: string): HTMLElement {
  return within(tabela).getByText(nome).closest('tr')!
}

test('lista usuários com papel e situação', async () => {
  const tabela = await abrirPagina()

  expect(within(linhaDe(tabela, 'Ana Admin')).getByText('Administrador')).toBeInTheDocument()
  expect(within(linhaDe(tabela, 'Ana Admin')).getByText('Ativo')).toBeInTheDocument()
  expect(within(linhaDe(tabela, 'Vitor')).getByText('Voluntário')).toBeInTheDocument()
  expect(within(linhaDe(tabela, 'Vitor')).getByText('Inativo')).toBeInTheDocument()
})

async function preencherNovoUsuario(senha: string) {
  await userEvent.click(screen.getByRole('button', { name: 'Novo usuário' }))
  const dialogo = await screen.findByRole('dialog')
  await userEvent.type(within(dialogo).getByLabelText(/Nome/), 'Bia')
  await userEvent.type(within(dialogo).getByLabelText(/E-mail/), 'bia@igreja.org')
  await userEvent.type(within(dialogo).getByLabelText(/Senha/), senha)
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }))
  return dialogo
}

test('cria usuário e recarrega a lista', async () => {
  await abrirPagina()

  await preencherNovoUsuario('senha-da-bia')

  await waitFor(() =>
    expect(api.criarUsuario).toHaveBeenCalledWith({
      nome: 'Bia',
      email: 'bia@igreja.org',
      papel: 'VOLUNTARIO',
      senha: 'senha-da-bia',
    }),
  )
  await waitFor(() => expect(api.listarUsuarios).toHaveBeenCalledTimes(2))
})

test('erro de e-mail duplicado aparece no campo', async () => {
  api.criarUsuario.mockRejectedValue(
    new ApiError(400, {
      status: 400,
      erros: [{ campo: 'email', mensagem: 'Já existe um usuário com este e-mail.' }],
    }),
  )
  await abrirPagina()

  await preencherNovoUsuario('senha-da-bia')

  expect(await screen.findByText('Já existe um usuário com este e-mail.')).toBeInTheDocument()
})

test('senha curta é bloqueada no formulário de criação', async () => {
  await abrirPagina()

  await preencherNovoUsuario('1234567')

  expect(await screen.findByText('A senha deve ter ao menos 8 caracteres.')).toBeInTheDocument()
  expect(api.criarUsuario).not.toHaveBeenCalled()
})

test('409 ao desativar a si mesmo mostra a mensagem', async () => {
  const mensagem = 'Você não pode desativar a própria conta nem alterar o próprio papel.'
  api.atualizarUsuario.mockRejectedValue(new ApiError(409, { status: 409, detail: mensagem }))
  const tabela = await abrirPagina()

  await userEvent.click(within(linhaDe(tabela, 'Ana Admin')).getByRole('button', { name: 'Editar' }))
  const dialogo = await screen.findByRole('dialog')
  await userEvent.click(within(dialogo).getByRole('switch', { name: 'Ativo' }))
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }))

  expect(await within(dialogo).findByText(mensagem)).toBeInTheDocument()
  expect(api.atualizarUsuario).toHaveBeenCalledWith(1, {
    nome: 'Ana Admin',
    email: 'ana@igreja.org',
    papel: 'ADMIN',
    ativo: false,
  })
})

test('tocar fora dos formulários de usuário não os fecha', async () => {
  const tabela = await abrirPagina()

  await userEvent.click(screen.getByRole('button', { name: 'Novo usuário' }))
  await userEvent.type(within(await screen.findByRole('dialog')).getByLabelText(/Nome/), 'Bia')
  await userEvent.click(document.querySelector('.mantine-Modal-overlay')!)
  expect(within(screen.getByRole('dialog')).getByLabelText(/Nome/)).toHaveValue('Bia')
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }))

  await userEvent.click(within(linhaDe(tabela, 'Vitor')).getByRole('button', { name: 'Redefinir senha' }))
  await userEvent.type(within(await screen.findByRole('dialog')).getByLabelText(/Nova senha/), 'abc')
  await userEvent.click(document.querySelector('.mantine-Modal-overlay')!)
  expect(within(screen.getByRole('dialog')).getByLabelText(/Nova senha/)).toHaveValue('abc')
})

test('redefine a senha de um usuário', async () => {
  const tabela = await abrirPagina()

  await userEvent.click(within(linhaDe(tabela, 'Vitor')).getByRole('button', { name: 'Redefinir senha' }))
  const dialogo = await screen.findByRole('dialog')
  await userEvent.type(within(dialogo).getByLabelText(/Nova senha/), 'nova-senha-1')
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Redefinir' }))

  await waitFor(() => expect(api.redefinirSenhaUsuario).toHaveBeenCalledWith(2, 'nova-senha-1'))
})
