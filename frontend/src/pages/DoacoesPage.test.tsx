import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import * as campanhasApi from '../api/campanhas'
import { ApiError } from '../api/client'
import * as doacoesApi from '../api/doacoes'
import type { Doacao } from '../api/doacoes'
import * as progressoApi from '../api/progresso'
import App from '../App'
import { prepararAuth, VOLUNTARIO } from '../test/mockAuth'
import { renderComProviders } from '../test/render'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)
vi.mock('../api/campanhas')
vi.mock('../api/doacoes')
vi.mock('../api/doadores')
vi.mock('../api/progresso')

const DOACAO: Doacao = {
  id: 50,
  campanhaId: 1,
  campanhaTitulo: 'Cesta básica',
  doadorId: null,
  doadorNome: null,
  tipo: 'ITEM',
  valor: null,
  formaPagamento: null,
  dataDoacao: '2026-10-11',
  observacao: null,
  registradoPorNome: 'Vitor',
  itens: [{ necessidadeId: 10, item: 'Arroz', unidade: 'KG', quantidade: 5 }],
  podeAlterar: true,
}

const campanhas = vi.mocked(campanhasApi)
const doacoes = vi.mocked(doacoesApi)

beforeEach(() => {
  vi.clearAllMocks()
  prepararAuth(VOLUNTARIO)
  campanhas.listarCampanhas.mockResolvedValue({ itens: [], pagina: 0, tamanho: 100, total: 0, totalPaginas: 0 })
  doacoes.listarDoacoes.mockResolvedValue({ itens: [DOACAO], pagina: 0, tamanho: 20, total: 1, totalPaginas: 1 })
  doacoes.excluirDoacao.mockResolvedValue(undefined)
  doacoes.buscarDoacao.mockResolvedValue(DOACAO)
  campanhas.listarNecessidades.mockResolvedValue([])
})

async function abrir() {
  renderComProviders(<App />, { rota: '/doacoes' })
  await screen.findByText('Arroz 5 kg')
}

async function escolherAcao(acao: string) {
  await userEvent.click(screen.getByRole('button', { name: 'Ações da doação de 11/10/2026' }))
  await userEvent.click(await screen.findByRole('menuitem', { name: acao, hidden: true }))
}

test('lista doações com resumo, doador e autor', async () => {
  await abrir()

  expect(screen.getByText('Anônimo')).toBeInTheDocument()
  expect(screen.getByText('Cesta básica')).toBeInTheDocument()
  expect(screen.getByText('11/10/2026')).toBeInTheDocument()
  expect(screen.getByText('Registrado por Vitor')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Registrar doação' })).toHaveAttribute('href', '/doacoes/nova')
})

test('filtrar por tipo Dinheiro recarrega com tipo', async () => {
  await abrir()

  await userEvent.click(screen.getByRole('combobox', { name: 'Tipo' }))
  await userEvent.click(await screen.findByRole('option', { name: 'Dinheiro', hidden: true }))

  await waitFor(() => expect(doacoes.listarDoacoes).toHaveBeenLastCalledWith({ tipo: 'DINHEIRO', pagina: 0 }))
})

test('lista vazia mostra mensagem', async () => {
  doacoes.listarDoacoes.mockResolvedValue({ itens: [], pagina: 0, tamanho: 20, total: 0, totalPaginas: 0 })
  renderComProviders(<App />, { rota: '/doacoes' })

  expect(await screen.findByText('Nenhuma doação encontrada.')).toBeInTheDocument()
})

test('sem podeAlterar não há menu de ações', async () => {
  doacoes.listarDoacoes.mockResolvedValue({
    itens: [{ ...DOACAO, podeAlterar: false }],
    pagina: 0,
    tamanho: 20,
    total: 1,
    totalPaginas: 1,
  })
  await abrir()

  expect(screen.queryByRole('button', { name: /Ações da doação/ })).not.toBeInTheDocument()
})

test('editar leva ao formulário da doação', async () => {
  await abrir()

  await escolherAcao('Editar')

  expect(await screen.findByRole('heading', { name: 'Editar doação' })).toBeInTheDocument()
})

test('excluir pede confirmação e chama a API', async () => {
  await abrir()

  await escolherAcao('Excluir')
  const dialogo = await screen.findByRole('dialog')
  expect(within(dialogo).getByText('Excluir esta doação?')).toBeInTheDocument()
  expect(doacoes.excluirDoacao).not.toHaveBeenCalled()
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Excluir' }))

  await waitFor(() => expect(doacoes.excluirDoacao).toHaveBeenCalledWith(50))
  await waitFor(() => expect(doacoes.listarDoacoes).toHaveBeenCalledTimes(2))
})

test('excluir doação atualiza progresso, painel e relatórios', async () => {
  await abrir()

  await escolherAcao('Excluir')
  await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Excluir' }))

  await waitFor(() => expect(vi.mocked(progressoApi).invalidarTotais).toHaveBeenCalled())
})

test('403 ao excluir mostra a mensagem da API', async () => {
  const mensagem = 'Você só pode alterar doações que você registrou.'
  doacoes.excluirDoacao.mockRejectedValue(new ApiError(403, { status: 403, detail: mensagem }))
  await abrir()

  await escolherAcao('Excluir')
  await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Excluir' }))

  expect(await screen.findByText(mensagem)).toBeInTheDocument()
})
