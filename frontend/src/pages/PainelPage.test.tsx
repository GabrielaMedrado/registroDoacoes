import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import * as campanhasApi from '../api/campanhas'
import * as progressoApi from '../api/progresso'
import type { Painel, ProgressoCampanha } from '../api/progresso'
import App from '../App'
import { prepararAuth, VOLUNTARIO } from '../test/mockAuth'
import { renderComProviders } from '../test/render'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)
vi.mock('../api/campanhas')
vi.mock('../api/doacoes')
vi.mock('../api/progresso')

const AGASALHO: ProgressoCampanha = {
  campanhaId: 1,
  titulo: 'Agasalho',
  status: 'ATIVA',
  percentualGeral: 75,
  totalDinheiro: 75.5,
  metaValor: 200,
  percentualValor: 37,
  necessidades: [],
}
const NATAL: ProgressoCampanha = {
  ...AGASALHO,
  campanhaId: 2,
  titulo: 'Natal',
  percentualGeral: null,
  totalDinheiro: 30,
  metaValor: null,
  percentualValor: null,
}
const PAINEL: Painel = {
  campanhasAtivas: [AGASALHO, NATAL],
  maioresPendencias: [
    {
      campanhaId: 1,
      campanhaTitulo: 'Agasalho',
      necessidadeId: 10,
      item: 'Arroz',
      unidade: 'KG',
      pendente: 5,
      percentual: 50,
    },
  ],
}

const progresso = vi.mocked(progressoApi)

beforeEach(() => {
  vi.clearAllMocks()
  prepararAuth(VOLUNTARIO)
  progresso.buscarPainel.mockResolvedValue(PAINEL)
})

async function abrir() {
  renderComProviders(<App />, { rota: '/' })
  await screen.findByRole('heading', { name: 'Painel' })
}

function cardDe(titulo: string) {
  return screen.getByRole('link', { name: titulo }).closest('.mantine-Card-root') as HTMLElement
}

test('painel mostra cards das campanhas ativas com progresso', async () => {
  await abrir()
  await screen.findByRole('link', { name: 'Agasalho' })
  const card = cardDe('Agasalho')

  expect(within(card).getByRole('progressbar', { name: 'Progresso dos itens de Agasalho' })).toHaveAttribute(
    'aria-valuenow',
    '75',
  )
  expect(within(card).getByText('R$ 75,50 arrecadados de R$ 200,00')).toBeInTheDocument()
  expect(within(card).getByRole('progressbar', { name: 'Arrecadação de Agasalho' })).toHaveAttribute(
    'aria-valuenow',
    '37',
  )
})

test('campanha sem meta em dinheiro mostra só o arrecadado', async () => {
  await abrir()
  await screen.findByRole('link', { name: 'Natal' })
  const card = cardDe('Natal')

  expect(within(card).getByText('R$ 30,00 arrecadados')).toBeInTheDocument()
  expect(within(card).queryByRole('progressbar')).not.toBeInTheDocument()
})

test('lista as maiores pendências', async () => {
  await abrir()

  expect(await screen.findByText('Arroz: faltam 5 kg')).toBeInTheDocument()
})

test('painel vazio mostra mensagens', async () => {
  progresso.buscarPainel.mockResolvedValue({ campanhasAtivas: [], maioresPendencias: [] })
  await abrir()

  expect(await screen.findByText('Nenhuma campanha ativa no momento.')).toBeInTheDocument()
  expect(screen.getByText('Nenhuma pendência.')).toBeInTheDocument()
})

test('atalho "Registrar doação" leva ao formulário', async () => {
  await abrir()

  expect(screen.getByRole('link', { name: 'Registrar doação' })).toHaveAttribute('href', '/doacoes/nova')
})

test('card leva ao detalhe da campanha', async () => {
  vi.mocked(campanhasApi).buscarCampanha.mockResolvedValue({
    id: 1,
    titulo: 'Agasalho',
    descricao: null,
    dataInicio: '2026-10-01',
    dataFim: null,
    status: 'ATIVA',
    metaValor: 200,
  })
  progresso.buscarProgresso.mockResolvedValue(AGASALHO)
  await abrir()

  await userEvent.click(await screen.findByRole('link', { name: 'Agasalho' }))

  expect(await screen.findByRole('heading', { name: 'Agasalho' })).toBeInTheDocument()
})
