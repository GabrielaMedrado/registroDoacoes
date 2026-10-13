import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import type { Doacao } from '../api/doacoes'
import * as doadoresApi from '../api/doadores'
import App from '../App'
import { MARIA } from '../test/fixtures'
import { ADMIN, prepararAuth, VOLUNTARIO } from '../test/mockAuth'
import { renderComProviders } from '../test/render'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)
vi.mock('../api/doadores')

const DOACAO: Doacao = {
  id: 50,
  campanhaId: 1,
  campanhaTitulo: 'Cesta básica',
  doadorId: 3,
  doadorNome: 'Maria Souza',
  tipo: 'ITEM',
  valor: null,
  formaPagamento: null,
  dataDoacao: '2026-10-11',
  observacao: null,
  registradoPorNome: 'Vitor',
  itens: [{ necessidadeId: 10, item: 'Arroz', unidade: 'KG', quantidade: 5 }],
  campanhaAtiva: true,
  podeAlterar: true,
}

const api = vi.mocked(doadoresApi)

beforeEach(() => {
  vi.clearAllMocks()
  prepararAuth(ADMIN)
  api.buscarDoador.mockResolvedValue(MARIA)
  api.listarDoacoesDoDoador.mockResolvedValue({ itens: [DOACAO], pagina: 0, tamanho: 20, total: 1, totalPaginas: 1 })
  api.anonimizarDoador.mockResolvedValue({ ...MARIA, nome: 'Anônimo', telefone: null, email: null })
})

async function abrir() {
  renderComProviders(<App />, { rota: '/doadores/3' })
  await screen.findByRole('heading', { name: 'Maria Souza' })
}

test('detalhe mostra o histórico do doador', async () => {
  await abrir()

  expect(await screen.findByText('Arroz 5 kg')).toBeInTheDocument()
  expect(screen.getByText('Cesta básica')).toBeInTheDocument()
  expect(screen.getByText('11/10/2026')).toBeInTheDocument()
})

test('histórico vazio mostra mensagem', async () => {
  api.listarDoacoesDoDoador.mockResolvedValue({ itens: [], pagina: 0, tamanho: 20, total: 0, totalPaginas: 0 })
  await abrir()

  expect(await screen.findByText('Nenhuma doação registrada.')).toBeInTheDocument()
})

test('admin anonimiza após confirmar', async () => {
  await abrir()

  await userEvent.click(screen.getByRole('button', { name: 'Anonimizar' }))
  const dialogo = await screen.findByRole('dialog')
  expect(
    within(dialogo).getByText(
      'Apagar os dados pessoais de Maria Souza? As doações continuam registradas como anônimas.',
    ),
  ).toBeInTheDocument()
  expect(api.anonimizarDoador).not.toHaveBeenCalled()
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Anonimizar' }))

  await waitFor(() => expect(api.anonimizarDoador).toHaveBeenCalledWith(3))
})

test('voluntário não vê "Anonimizar"', async () => {
  prepararAuth(VOLUNTARIO)
  await abrir()

  expect(screen.queryByRole('button', { name: 'Anonimizar' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Editar' })).toBeInTheDocument()
})

test('doador anonimizado não pode ser editado nem anonimizado de novo', async () => {
  api.buscarDoador.mockResolvedValue({ ...MARIA, nome: 'Anônimo', telefone: null, email: null, anonimizado: true })
  renderComProviders(<App />, { rota: '/doadores/3' })
  await screen.findByRole('heading', { name: 'Anônimo' })

  expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Anonimizar' })).not.toBeInTheDocument()
})
