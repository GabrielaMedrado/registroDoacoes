import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import * as campanhasApi from '../api/campanhas'
import type { ProgressoCampanha } from '../api/progresso'
import * as relatoriosApi from '../api/relatorios'
import type { RelatorioDoacoes } from '../api/relatorios'
import App from '../App'
import { prepararAuth, VOLUNTARIO } from '../test/mockAuth'
import { renderComProviders } from '../test/render'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)
vi.mock('../api/campanhas')
// Só as buscas são substituídas; os montadores de URL continuam os reais.
vi.mock('../api/relatorios', async (original) => ({
  ...(await original<typeof import('../api/relatorios')>()),
  buscarRelatorioCampanha: vi.fn(),
  buscarRelatorioDoacoes: vi.fn(),
  buscarPendencias: vi.fn(),
}))

const PROGRESSO: ProgressoCampanha = {
  campanhaId: 1,
  titulo: 'Agasalho',
  status: 'ATIVA',
  percentualGeral: 50,
  totalDinheiro: 75.5,
  metaValor: null,
  percentualValor: null,
  necessidades: [
    {
      id: 10,
      item: 'Arroz',
      unidade: 'KG',
      quantidadeMeta: 10,
      observacao: null,
      arrecadado: 5,
      pendente: 5,
      excedente: 0,
      percentual: 50,
    },
  ],
}
const RELATORIO: RelatorioDoacoes = {
  de: '2026-10-01',
  ate: '2026-10-15',
  quantidade: 2,
  totalDinheiro: 50,
  totaisItens: [{ item: 'Arroz', unidade: 'KG', quantidade: 5 }],
  doacoes: [
    {
      id: 1,
      dataDoacao: '2026-10-05',
      campanhaTitulo: 'Agasalho',
      doadorNome: 'Anônimo',
      tipo: 'ITEM',
      descricao: 'Arroz 5 kg',
      valor: null,
    },
    {
      id: 2,
      dataDoacao: '2026-10-10',
      campanhaTitulo: 'Agasalho',
      doadorNome: 'Maria',
      tipo: 'DINHEIRO',
      descricao: 'PIX',
      valor: 50,
    },
  ],
}
const TEXTO = '*Agasalho*\nAinda precisamos de:\n• Arroz: 5 kg'

const relatorios = vi.mocked(relatoriosApi)

beforeEach(() => {
  vi.clearAllMocks()
  // Só o relógio é falso: 15/10/2026 às 23h30 no horário local (em UTC já seria dia 16).
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 15, 23, 30))
  prepararAuth(VOLUNTARIO)
  vi.mocked(campanhasApi).listarCampanhas.mockResolvedValue({
    itens: [
      {
        id: 1,
        titulo: 'Agasalho',
        descricao: null,
        dataInicio: '2026-10-01',
        dataFim: null,
        status: 'ATIVA',
        metaValor: null,
      },
    ],
    pagina: 0,
    tamanho: 100,
    total: 1,
    totalPaginas: 1,
  })
  relatorios.buscarRelatorioCampanha.mockResolvedValue(PROGRESSO)
  relatorios.buscarRelatorioDoacoes.mockResolvedValue(RELATORIO)
  relatorios.buscarPendencias.mockResolvedValue(TEXTO)
})

afterEach(() => {
  vi.useRealTimers()
})

async function abrir(aba?: string) {
  renderComProviders(<App />, { rota: '/relatorios' })
  await screen.findByRole('heading', { name: 'Relatórios' })
  if (aba) await userEvent.click(screen.getByRole('tab', { name: aba }))
}

async function escolherCampanha(nome: string) {
  await userEvent.click(screen.getByRole('combobox', { name: 'Campanha' }))
  await userEvent.click(await screen.findByRole('option', { name: nome, hidden: true }))
}

test('relatório por campanha mostra a tabela e o link do CSV', async () => {
  await abrir()

  await escolherCampanha('Agasalho')

  expect(await screen.findByText('Arroz')).toBeInTheDocument()
  expect(relatorios.buscarRelatorioCampanha).toHaveBeenCalledWith(1)
  expect(screen.getByText('Total em dinheiro: R$ 75,50')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Baixar CSV' })).toHaveAttribute(
    'href',
    '/api/relatorios/campanha/1?formato=csv',
  )
})

test('doações por período usa o mês atual como padrão', async () => {
  await abrir('Doações por período')

  expect(screen.getByLabelText('De')).toHaveValue('2026-10-01')
  expect(screen.getByLabelText('Até')).toHaveValue('2026-10-15')
  await waitFor(() =>
    expect(relatorios.buscarRelatorioDoacoes).toHaveBeenCalledWith({ de: '2026-10-01', ate: '2026-10-15' }),
  )
})

test('período invertido mostra erro e não busca', async () => {
  await abrir('Doações por período')
  await screen.findByText('Maria')

  fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-10-20' } })

  expect(await screen.findByText('A data final não pode ser anterior à inicial.')).toBeInTheDocument()
  expect(relatorios.buscarRelatorioDoacoes).not.toHaveBeenCalledWith(expect.objectContaining({ de: '2026-10-20' }))
  expect(screen.queryByRole('link', { name: 'Baixar CSV' })).not.toBeInTheDocument()
})

test('mostra totais e link do CSV com os filtros', async () => {
  await abrir('Doações por período')

  expect(await screen.findByText('2 doações · Total em dinheiro: R$ 50,00')).toBeInTheDocument()
  expect(screen.getByText('Arroz: 5 kg')).toBeInTheDocument()
  expect(screen.getByText('Maria')).toBeInTheDocument()
  await escolherCampanha('Agasalho')

  await waitFor(() =>
    expect(screen.getByRole('link', { name: 'Baixar CSV' })).toHaveAttribute(
      'href',
      '/api/relatorios/doacoes?de=2026-10-01&ate=2026-10-15&campanhaId=1&formato=csv',
    ),
  )
})

test('período sem doações mostra mensagem', async () => {
  relatorios.buscarRelatorioDoacoes.mockResolvedValue({
    ...RELATORIO,
    quantidade: 0,
    totalDinheiro: 0,
    totaisItens: [],
    doacoes: [],
  })
  await abrir('Doações por período')

  expect(await screen.findByText('Nenhuma doação no período.')).toBeInTheDocument()
})

test('pendências: copiar texto', async () => {
  const user = userEvent.setup()
  const copiar = vi.spyOn(navigator.clipboard, 'writeText')
  renderComProviders(<App />, { rota: '/relatorios' })
  await user.click(await screen.findByRole('tab', { name: 'Pendências' }))
  expect(await screen.findByDisplayValue(/Ainda precisamos de/)).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: 'Copiar texto' }))

  expect(copiar).toHaveBeenCalledWith(TEXTO)
  expect(await screen.findByText('Texto copiado.')).toBeInTheDocument()
})

test('pendências: link do WhatsApp leva o texto codificado', async () => {
  await abrir('Pendências')
  await screen.findByDisplayValue(/Ainda precisamos de/)

  const link = screen.getByRole('link', { name: 'Abrir no WhatsApp' })
  expect(link).toHaveAttribute('href', `https://wa.me/?text=${encodeURIComponent(TEXTO)}`)
  expect(link).toHaveAttribute('target', '_blank')
})

test('pendências filtradas por campanha', async () => {
  await abrir('Pendências')
  await screen.findByDisplayValue(/Ainda precisamos de/)

  await escolherCampanha('Agasalho')

  await waitFor(() => expect(relatorios.buscarPendencias).toHaveBeenCalledWith(1))
})

test('Imprimir chama window.print', async () => {
  const imprimir = vi.spyOn(window, 'print').mockImplementation(() => {})
  await abrir('Doações por período')
  await screen.findByText('Maria')

  await userEvent.click(screen.getByRole('button', { name: 'Imprimir' }))

  expect(imprimir).toHaveBeenCalled()
})
