import type { Unidade } from './campanhas'
import { api } from './client'
import type { Pagina } from './tipos'

export type TipoDoacao = 'ITEM' | 'DINHEIRO'

export type FormaPagamento = 'PIX' | 'ESPECIE' | 'OUTRO'

export type ItemDoacao = { necessidadeId: number; item: string; unidade: Unidade; quantidade: number }

export type Doacao = {
  id: number
  campanhaId: number
  campanhaTitulo: string
  doadorId: number | null
  doadorNome: string | null
  tipo: TipoDoacao
  valor: number | null
  formaPagamento: FormaPagamento | null
  dataDoacao: string
  observacao: string | null
  registradoPorNome: string
  itens: ItemDoacao[]
  podeAlterar: boolean
}

export type DoacaoDados = {
  campanhaId: number
  doadorId: number | null
  tipo: TipoDoacao
  valor: number | null
  formaPagamento: FormaPagamento | null
  dataDoacao: string
  observacao: string | null
  itens: { necessidadeId: number; quantidade: number }[]
}

export type FiltroDoacoes = { campanhaId?: number; de?: string; ate?: string; tipo?: TipoDoacao; pagina: number }

export function listarDoacoes(filtro: FiltroDoacoes): Promise<Pagina<Doacao>> {
  const params = new URLSearchParams()
  if (filtro.campanhaId) params.set('campanhaId', String(filtro.campanhaId))
  if (filtro.de) params.set('de', filtro.de)
  if (filtro.ate) params.set('ate', filtro.ate)
  if (filtro.tipo) params.set('tipo', filtro.tipo)
  params.set('pagina', String(filtro.pagina))
  return api<Pagina<Doacao>>(`/api/doacoes?${params}`)
}

export function buscarDoacao(id: number): Promise<Doacao> {
  return api<Doacao>(`/api/doacoes/${id}`)
}

export function registrarDoacao(dados: DoacaoDados): Promise<Doacao> {
  return api<Doacao>('/api/doacoes', { method: 'POST', body: dados })
}

export function atualizarDoacao(id: number, dados: DoacaoDados): Promise<Doacao> {
  return api<Doacao>(`/api/doacoes/${id}`, { method: 'PUT', body: dados })
}

export function excluirDoacao(id: number): Promise<void> {
  return api<void>(`/api/doacoes/${id}`, { method: 'DELETE' })
}
