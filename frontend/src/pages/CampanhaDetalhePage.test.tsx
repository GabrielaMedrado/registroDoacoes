import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import * as campanhasApi from '../api/campanhas'
import type { Campanha, Necessidade } from '../api/campanhas'
import { ApiError } from '../api/client'
import App from '../App'
import { ADMIN, prepararAuth, VOLUNTARIO } from '../test/mockAuth'
import { renderComProviders } from '../test/render'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)
vi.mock('../api/campanhas')

const CAMPANHA: Campanha = {
  id: 1,
  titulo: 'Cesta básica',
  descricao: 'Alimentos para 30 famílias',
  dataInicio: '2026-10-20',
  dataFim: null,
  status: 'ATIVA',
  metaValor: null,
}
const ARROZ: Necessidade = { id: 10, campanhaId: 1, item: 'Arroz', unidade: 'KG', quantidadeMeta: 50, observacao: null }

const api = vi.mocked(campanhasApi)

beforeEach(() => {
  vi.clearAllMocks()
  prepararAuth(ADMIN)
  api.buscarCampanha.mockResolvedValue(CAMPANHA)
  api.listarNecessidades.mockResolvedValue([ARROZ])
  api.criarNecessidade.mockResolvedValue({ ...ARROZ, id: 11, item: 'Feijão' })
  api.alterarStatusCampanha.mockResolvedValue({ ...CAMPANHA, status: 'ENCERRADA' })
  api.excluirNecessidade.mockResolvedValue(undefined)
})

async function abrir() {
  renderComProviders(<App />, { rota: '/campanhas/1' })
  await screen.findByRole('heading', { name: 'Cesta básica' })
}

test('mostra dados da campanha e necessidades', async () => {
  await abrir()

  expect(screen.getByText('Alimentos para 30 famílias')).toBeInTheDocument()
  expect(await screen.findByText('Arroz')).toBeInTheDocument()
  expect(screen.getByText('50 kg')).toBeInTheDocument()
  expect(api.buscarCampanha).toHaveBeenCalledWith(1)
})

test('voluntário não vê botões de edição', async () => {
  prepararAuth(VOLUNTARIO)
  await abrir()
  await screen.findByText('Arroz')

  for (const nome of ['Editar campanha', 'Alterar status', 'Adicionar necessidade', 'Ações de Arroz']) {
    expect(screen.queryByRole('button', { name: nome })).not.toBeInTheDocument()
  }
})

test('admin adiciona necessidade com quantidade decimal digitada com vírgula', async () => {
  await abrir()

  await userEvent.click(screen.getByRole('button', { name: 'Adicionar necessidade' }))
  const dialogo = await screen.findByRole('dialog')
  await userEvent.type(within(dialogo).getByLabelText(/Item/), 'Feijão')
  await userEvent.click(within(dialogo).getByRole('combobox', { name: /Unidade/ }))
  await userEvent.click(await screen.findByRole('option', { name: 'kg', hidden: true }))
  await userEvent.type(within(dialogo).getByLabelText(/Quantidade/), '2,5')
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }))

  await waitFor(() =>
    expect(api.criarNecessidade).toHaveBeenCalledWith(1, {
      item: 'Feijão',
      unidade: 'KG',
      quantidadeMeta: 2.5,
      observacao: null,
    }),
  )
})

test('encerrar pede confirmação antes de chamar a API', async () => {
  await abrir()

  await userEvent.click(screen.getByRole('button', { name: 'Alterar status' }))
  await userEvent.click(await screen.findByRole('menuitem', { name: 'Encerrar', hidden: true }))
  const dialogo = await screen.findByRole('dialog')
  expect(within(dialogo).getByText('Encerrar a campanha? Novas doações ficarão bloqueadas.')).toBeInTheDocument()
  expect(api.alterarStatusCampanha).not.toHaveBeenCalled()
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar' }))

  await waitFor(() => expect(api.alterarStatusCampanha).toHaveBeenCalledWith(1, 'ENCERRADA'))
})

test('ativar não pede confirmação', async () => {
  api.buscarCampanha.mockResolvedValue({ ...CAMPANHA, status: 'RASCUNHO' })
  await abrir()

  await userEvent.click(screen.getByRole('button', { name: 'Alterar status' }))
  await userEvent.click(await screen.findByRole('menuitem', { name: 'Ativar', hidden: true }))

  await waitFor(() => expect(api.alterarStatusCampanha).toHaveBeenCalledWith(1, 'ATIVA'))
})

test('campanha ativa mostra "Registrar doação" com a campanha pré-selecionada', async () => {
  prepararAuth(VOLUNTARIO)
  await abrir()

  expect(screen.getByRole('link', { name: 'Registrar doação' })).toHaveAttribute('href', '/doacoes/nova?campanha=1')
})

test('campanha em rascunho não mostra "Registrar doação"', async () => {
  api.buscarCampanha.mockResolvedValue({ ...CAMPANHA, status: 'RASCUNHO' })
  await abrir()

  expect(screen.queryByRole('link', { name: 'Registrar doação' })).not.toBeInTheDocument()
})

test('no celular, as ações ficam junto ao nome do item (sem precisar arrastar a tabela)', async () => {
  await abrir()
  const celulaDoItem = (await screen.findByText('Arroz')).closest('td')!

  await userEvent.click(within(celulaDoItem).getByRole('button', { name: 'Ações de Arroz' }))

  expect(await screen.findByRole('menuitem', { name: 'Editar', hidden: true })).toBeInTheDocument()
  expect(await screen.findByRole('menuitem', { name: 'Excluir', hidden: true })).toBeInTheDocument()
})

test('tocar fora do formulário não fecha nem perde o que foi digitado', async () => {
  await abrir()
  await userEvent.click(screen.getByRole('button', { name: 'Adicionar necessidade' }))
  const dialogo = await screen.findByRole('dialog')
  await userEvent.type(within(dialogo).getByLabelText(/Item/), 'Feijão')

  await userEvent.click(document.querySelector('.mantine-Modal-overlay')!)

  expect(screen.getByRole('dialog')).toBeInTheDocument()
  expect(within(screen.getByRole('dialog')).getByLabelText(/Item/)).toHaveValue('Feijão')
})

async function pedirExclusao() {
  await abrir()
  await userEvent.click(await screen.findByRole('button', { name: 'Ações de Arroz' }))
  await userEvent.click(await screen.findByRole('menuitem', { name: 'Excluir', hidden: true }))
  return await screen.findByRole('dialog')
}

test('excluir pede confirmação e chama a API', async () => {
  const dialogo = await pedirExclusao()

  expect(within(dialogo).getByText('Excluir "Arroz"?')).toBeInTheDocument()
  expect(api.excluirNecessidade).not.toHaveBeenCalled()
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Excluir' }))

  await waitFor(() => expect(api.excluirNecessidade).toHaveBeenCalledWith(10))
})

test('409 ao excluir mostra a mensagem da regra', async () => {
  const mensagem = 'Esta necessidade já recebeu doações e não pode ser excluída.'
  api.excluirNecessidade.mockRejectedValue(new ApiError(409, { status: 409, detail: mensagem }))
  const dialogo = await pedirExclusao()

  await userEvent.click(within(dialogo).getByRole('button', { name: 'Excluir' }))

  expect(await screen.findByText(mensagem)).toBeInTheDocument()
})

test('campanha inexistente mostra mensagem e link de volta', async () => {
  api.buscarCampanha.mockRejectedValue(new ApiError(404, { status: 404, detail: 'Campanha não encontrada.' }))
  renderComProviders(<App />, { rota: '/campanhas/999' })

  expect(await screen.findByText('Campanha não encontrada.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Voltar para campanhas' })).toHaveAttribute('href', '/campanhas')
})
