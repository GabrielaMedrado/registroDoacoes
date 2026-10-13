import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { act } from '@testing-library/react'
import { focusManager } from '@tanstack/react-query'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import * as campanhasApi from '../api/campanhas'
import type { Campanha, Necessidade } from '../api/campanhas'
import { ApiError } from '../api/client'
import * as doacoesApi from '../api/doacoes'
import type { Doacao } from '../api/doacoes'
import * as doadoresApi from '../api/doadores'
import * as progressoApi from '../api/progresso'
import App from '../App'
import { MARIA } from '../test/fixtures'
import { prepararAuth, VOLUNTARIO } from '../test/mockAuth'
import { renderComProviders } from '../test/render'
import { hojeISO } from '../util/formatos'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)
vi.mock('../api/campanhas')
vi.mock('../api/doacoes')
vi.mock('../api/doadores')
vi.mock('../api/progresso')

const campanha = (id: number, titulo: string): Campanha => ({
  id,
  titulo,
  descricao: null,
  dataInicio: '2026-10-01',
  dataFim: null,
  status: 'ATIVA',
  metaValor: null,
})

const necessidade = (id: number, campanhaId: number, item: string): Necessidade => ({
  id,
  campanhaId,
  item,
  unidade: 'KG',
  quantidadeMeta: 100,
  observacao: null,
})

const DOACAO: Doacao = {
  id: 50,
  campanhaId: 1,
  campanhaTitulo: 'Cesta básica',
  doadorId: 3,
  doadorNome: 'Maria Souza',
  tipo: 'ITEM',
  valor: null,
  formaPagamento: null,
  dataDoacao: '2026-10-10',
  observacao: null,
  registradoPorNome: 'Vitor',
  itens: [{ necessidadeId: 10, item: 'Arroz', unidade: 'KG', quantidade: 5 }],
  campanhaAtiva: true,
  podeAlterar: true,
  versao: 0,
}

const campanhas = vi.mocked(campanhasApi)
const doacoes = vi.mocked(doacoesApi)
const doadores = vi.mocked(doadoresApi)

beforeEach(() => {
  vi.clearAllMocks()
  prepararAuth(VOLUNTARIO)
  campanhas.listarCampanhas.mockResolvedValue({
    itens: [campanha(1, 'Cesta básica'), campanha(2, 'Agasalho')],
    pagina: 0,
    tamanho: 100,
    total: 2,
    totalPaginas: 1,
  })
  campanhas.listarNecessidades.mockImplementation(async (id) =>
    id === 1 ? [necessidade(10, 1, 'Arroz'), necessidade(11, 1, 'Feijão')] : [necessidade(20, 2, 'Casaco')],
  )
  doadores.listarDoadores.mockResolvedValue({ itens: [MARIA], pagina: 0, tamanho: 20, total: 1, totalPaginas: 1 })
  doadores.buscarDoador.mockResolvedValue(MARIA)
  doacoes.registrarDoacao.mockResolvedValue(DOACAO)
  doacoes.atualizarDoacao.mockResolvedValue(DOACAO)
  doacoes.buscarDoacao.mockResolvedValue(DOACAO)
})

async function escolher(campo: string, opcao: string) {
  await userEvent.click(screen.getByRole('combobox', { name: campo }))
  const opcoes = await screen.findAllByRole('option', { name: opcao, hidden: true })
  await userEvent.click(opcoes[opcoes.length - 1])
}

async function abrir(rota = '/doacoes/nova') {
  renderComProviders(<App />, { rota })
  await screen.findByRole('heading', { name: /doação/ })
  await waitFor(() => expect(campanhas.listarCampanhas).toHaveBeenCalled())
}

test('data vem preenchida com hoje (local)', async () => {
  await abrir()

  expect(screen.getByLabelText('Data')).toHaveValue(hojeISO())
})

test('registra doação anônima de dois itens', async () => {
  await abrir()

  await escolher('Campanha', 'Cesta básica')
  await escolher('Item 1', 'Arroz')
  await userEvent.type(screen.getByLabelText('Quantidade 1'), '5')
  await userEvent.click(screen.getByRole('button', { name: '+ Adicionar item' }))
  await escolher('Item 2', 'Feijão')
  await userEvent.type(screen.getByLabelText('Quantidade 2'), '2,5')
  await userEvent.click(screen.getByRole('button', { name: 'Registrar' }))

  await waitFor(() =>
    expect(doacoes.registrarDoacao).toHaveBeenCalledWith({
      campanhaId: 1,
      doadorId: null,
      tipo: 'ITEM',
      valor: null,
      formaPagamento: null,
      dataDoacao: hojeISO(),
      observacao: null,
      itens: [
        { necessidadeId: 10, quantidade: 5 },
        { necessidadeId: 11, quantidade: 2.5 },
      ],
    }),
  )
})

test('registra doação em dinheiro', async () => {
  await abrir()

  await escolher('Campanha', 'Cesta básica')
  await userEvent.click(screen.getByText('Dinheiro'))
  await userEvent.type(screen.getByLabelText('Valor'), '50,00')
  await escolher('Forma de pagamento', 'PIX')
  await userEvent.click(screen.getByRole('button', { name: 'Registrar' }))

  await waitFor(() =>
    expect(doacoes.registrarDoacao).toHaveBeenCalledWith(
      expect.objectContaining({ tipo: 'DINHEIRO', valor: 50, formaPagamento: 'PIX', itens: [] }),
    ),
  )
})

test('após registrar mostra sucesso e limpa itens mantendo a campanha', async () => {
  await abrir()

  await escolher('Campanha', 'Cesta básica')
  await escolher('Item 1', 'Arroz')
  await userEvent.type(screen.getByLabelText('Quantidade 1'), '5')
  await userEvent.click(screen.getByRole('button', { name: '+ Adicionar item' }))
  await userEvent.click(screen.getByRole('button', { name: 'Registrar' }))

  expect(await screen.findByText('Doação registrada.')).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: 'Campanha' })).toHaveValue('Cesta básica')
  expect(screen.getByRole('combobox', { name: 'Item 1' })).toHaveValue('')
  expect(screen.queryByRole('combobox', { name: 'Item 2' })).not.toBeInTheDocument()
})

test('registrar doação atualiza progresso, painel e relatórios', async () => {
  await abrir()

  await escolher('Campanha', 'Cesta básica')
  await escolher('Item 1', 'Arroz')
  await userEvent.type(screen.getByLabelText('Quantidade 1'), '5')
  await userEvent.click(screen.getByRole('button', { name: 'Registrar' }))

  await screen.findByText('Doação registrada.')
  expect(vi.mocked(progressoApi).invalidarTotais).toHaveBeenCalled()
})

test('trocar a campanha limpa os itens escolhidos', async () => {
  await abrir()

  await escolher('Campanha', 'Cesta básica')
  await escolher('Item 1', 'Arroz')
  await escolher('Campanha', 'Agasalho')

  expect(screen.getByRole('combobox', { name: 'Item 1' })).toHaveValue('')
})

test('toque duplo em Registrar cria uma única doação', async () => {
  doacoes.registrarDoacao.mockReturnValue(new Promise(() => {}))
  await abrir()

  await escolher('Campanha', 'Cesta básica')
  await escolher('Item 1', 'Arroz')
  await userEvent.type(screen.getByLabelText('Quantidade 1'), '5')
  // Dois toques seguidos, antes de o botão ser redesenhado como "carregando".
  const registrar = screen.getByRole('button', { name: 'Registrar' })
  fireEvent.click(registrar)
  fireEvent.click(registrar)

  await waitFor(() => expect(doacoes.registrarDoacao).toHaveBeenCalled())
  expect(doacoes.registrarDoacao).toHaveBeenCalledTimes(1)
})

test('sem itens válidos mostra "Adicione ao menos um item." e não chama a API', async () => {
  await abrir()

  await escolher('Campanha', 'Cesta básica')
  await userEvent.click(screen.getByRole('button', { name: 'Registrar' }))

  expect(await screen.findByText('Adicione ao menos um item.')).toBeInTheDocument()
  expect(doacoes.registrarDoacao).not.toHaveBeenCalled()
})

test('409 de campanha não ativa aparece em alerta', async () => {
  const mensagem = 'Só é possível registrar ou alterar doações em campanhas ativas.'
  doacoes.registrarDoacao.mockRejectedValue(new ApiError(409, { status: 409, detail: mensagem }))
  await abrir()

  await escolher('Campanha', 'Cesta básica')
  await escolher('Item 1', 'Arroz')
  await userEvent.type(screen.getByLabelText('Quantidade 1'), '5')
  await userEvent.click(screen.getByRole('button', { name: 'Registrar' }))

  expect(await screen.findByText(mensagem)).toBeInTheDocument()
})

test('pré-seleciona a campanha vinda de ?campanha=1', async () => {
  await abrir('/doacoes/nova?campanha=1')

  await waitFor(() => expect(screen.getByRole('combobox', { name: 'Campanha' })).toHaveValue('Cesta básica'))
})

test('editar carrega a doação e salva com atualizarDoacao', async () => {
  await abrir('/doacoes/50/editar')

  const quantidade = await screen.findByLabelText('Quantidade 1')
  expect(quantidade).toHaveValue('5')
  await userEvent.clear(quantidade)
  await userEvent.type(quantidade, '7')
  await userEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }))

  await waitFor(() =>
    expect(doacoes.atualizarDoacao).toHaveBeenCalledWith(50, {
      campanhaId: 1,
      doadorId: 3,
      tipo: 'ITEM',
      valor: null,
      formaPagamento: null,
      dataDoacao: '2026-10-10',
      observacao: null,
      itens: [{ necessidadeId: 10, quantidade: 7 }],
      versao: 0,
    }),
  )
})

test('doação de campanha não ativa mostra aviso em vez do formulário', async () => {
  doacoes.buscarDoacao.mockResolvedValue({ ...DOACAO, campanhaAtiva: false, podeAlterar: false })
  renderComProviders(<App />, { rota: '/doacoes/50/editar' })

  expect(await screen.findByText('Só é possível alterar doações de campanhas ativas.')).toBeInTheDocument()
  expect(screen.queryByText('Você só pode alterar doações que você registrou.')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Salvar alterações' })).not.toBeInTheDocument()
})

test('editar envia a versão carregada', async () => {
  doacoes.buscarDoacao.mockResolvedValue({ ...DOACAO, versao: 4 })
  await abrir('/doacoes/50/editar')

  await screen.findByLabelText('Quantidade 1')
  await userEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }))

  await waitFor(() => expect(doacoes.atualizarDoacao).toHaveBeenCalledWith(50, expect.objectContaining({ versao: 4 })))
})

test('recarga em segundo plano não troca a versão dos valores na tela', async () => {
  doacoes.buscarDoacao.mockResolvedValue({ ...DOACAO, versao: 0 })
  await abrir('/doacoes/50/editar')
  expect(await screen.findByLabelText('Quantidade 1')).toHaveValue('5')

  // Outra pessoa salvou: a próxima leitura traz a versão 1 com outra quantidade.
  doacoes.buscarDoacao.mockResolvedValue({ ...DOACAO, versao: 1, itens: [{ ...DOACAO.itens[0], quantidade: 9 }] })
  act(() => {
    focusManager.setFocused(false)
    focusManager.setFocused(true)
  })
  await waitFor(() => expect(doacoes.buscarDoacao).toHaveBeenCalledTimes(2))

  await userEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }))
  // A tela ainda mostra os valores da versão 0: é ela que vai, para o servidor recusar com 409.
  await waitFor(() => expect(doacoes.atualizarDoacao).toHaveBeenCalledWith(50, expect.objectContaining({ versao: 0 })))
})

test('reabrir a edição pelo app mostra os dados atuais, não a cópia antiga', async () => {
  doacoes.listarDoacoes.mockResolvedValue({ itens: [DOACAO], pagina: 0, tamanho: 20, total: 1, totalPaginas: 1 })
  doacoes.buscarDoacao.mockResolvedValue({ ...DOACAO, versao: 0 })
  await abrir('/doacoes/50/editar')
  expect(await screen.findByLabelText('Quantidade 1')).toHaveValue('5')
  await userEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }))
  await screen.findByRole('button', { name: /Ações da doação/ })

  // Enquanto isso, outra pessoa salvou a versão 1.
  doacoes.buscarDoacao.mockResolvedValue({ ...DOACAO, versao: 1, itens: [{ ...DOACAO.itens[0], quantidade: 9 }] })
  await userEvent.click(screen.getByRole('button', { name: /Ações da doação/ }))
  await userEvent.click(await screen.findByRole('menuitem', { name: 'Editar', hidden: true }))

  expect(await screen.findByLabelText('Quantidade 1')).toHaveValue('9')
})

test('cadastrar novo doador no seletor já o seleciona', async () => {
  doadores.criarDoador.mockResolvedValue({ id: 4, nome: 'José', telefone: null, email: null, observacao: null, anonimizado: false })
  await abrir()

  await userEvent.click(screen.getByRole('button', { name: 'Cadastrar novo doador' }))
  const dialogo = await screen.findByRole('dialog')
  await userEvent.type(within(dialogo).getByLabelText(/Nome/), 'José')
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }))
  await waitFor(() => expect(screen.getByRole('combobox', { name: 'Doador' })).toHaveValue('José'))

  await escolher('Campanha', 'Cesta básica')
  await escolher('Item 1', 'Arroz')
  await userEvent.type(screen.getByLabelText('Quantidade 1'), '1')
  await userEvent.click(screen.getByRole('button', { name: 'Registrar' }))

  await waitFor(() => expect(doacoes.registrarDoacao).toHaveBeenCalledWith(expect.objectContaining({ doadorId: 4 })))
})

test('salvar o novo doador não envia a doação junto', async () => {
  doadores.criarDoador.mockResolvedValue({ id: 4, nome: 'José', telefone: null, email: null, observacao: null, anonimizado: false })
  await abrir()
  await escolher('Campanha', 'Cesta básica')
  await escolher('Item 1', 'Arroz')
  await userEvent.type(screen.getByLabelText('Quantidade 1'), '5')

  await userEvent.click(screen.getByRole('button', { name: 'Cadastrar novo doador' }))
  const dialogo = await screen.findByRole('dialog')
  await userEvent.type(within(dialogo).getByLabelText(/Nome/), 'José{Enter}')

  await waitFor(() => expect(doadores.criarDoador).toHaveBeenCalled())
  await waitFor(() => expect(screen.getByRole('combobox', { name: 'Doador' })).toHaveValue('José'))
  expect(doacoes.registrarDoacao).not.toHaveBeenCalled()
})

test('erro da API numa linha de item aparece junto da quantidade', async () => {
  doacoes.registrarDoacao.mockRejectedValue(
    new ApiError(400, { status: 400, erros: [{ campo: 'itens[0].quantidade', mensagem: 'Quantidade grande demais.' }] }),
  )
  await abrir()
  await escolher('Campanha', 'Cesta básica')
  await escolher('Item 1', 'Arroz')
  await userEvent.type(screen.getByLabelText('Quantidade 1'), '5')
  await userEvent.click(screen.getByRole('button', { name: 'Registrar' }))

  expect(await screen.findByText('Quantidade grande demais.')).toBeInTheDocument()
})
