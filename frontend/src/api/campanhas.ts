import { api } from './client'
import type { Pagina } from './tipos'

export type StatusCampanha = 'RASCUNHO' | 'ATIVA' | 'ENCERRADA'

export type Unidade = 'UNIDADE' | 'KG' | 'LITRO' | 'PACOTE' | 'CAIXA' | 'PAR'

export type Campanha = {
  id: number
  titulo: string
  descricao: string | null
  dataInicio: string
  dataFim: string | null
  status: StatusCampanha
  metaValor: number | null
}

export type CampanhaDados = {
  titulo: string
  descricao: string | null
  dataInicio: string
  dataFim: string | null
  metaValor: number | null
}

export type Necessidade = {
  id: number
  campanhaId: number
  item: string
  unidade: Unidade
  quantidadeMeta: number
  observacao: string | null
}

export type NecessidadeDados = Omit<Necessidade, 'id' | 'campanhaId'>

export function listarCampanhas(filtro: {
  status?: StatusCampanha
  pagina: number
  tamanho?: number
}): Promise<Pagina<Campanha>> {
  const params = new URLSearchParams()
  if (filtro.status) {
    params.set('status', filtro.status)
  }
  params.set('pagina', String(filtro.pagina))
  if (filtro.tamanho) {
    params.set('tamanho', String(filtro.tamanho))
  }
  return api<Pagina<Campanha>>(`/api/campanhas?${params}`)
}

export function buscarCampanha(id: number): Promise<Campanha> {
  return api<Campanha>(`/api/campanhas/${id}`)
}

export function criarCampanha(dados: CampanhaDados): Promise<Campanha> {
  return api<Campanha>('/api/campanhas', { method: 'POST', body: dados })
}

export function atualizarCampanha(id: number, dados: CampanhaDados): Promise<Campanha> {
  return api<Campanha>(`/api/campanhas/${id}`, { method: 'PUT', body: dados })
}

export function alterarStatusCampanha(id: number, status: StatusCampanha): Promise<Campanha> {
  return api<Campanha>(`/api/campanhas/${id}/status`, { method: 'PATCH', body: { status } })
}

export function listarNecessidades(campanhaId: number): Promise<Necessidade[]> {
  return api<Necessidade[]>(`/api/campanhas/${campanhaId}/necessidades`)
}

export function criarNecessidade(campanhaId: number, dados: NecessidadeDados): Promise<Necessidade> {
  return api<Necessidade>(`/api/campanhas/${campanhaId}/necessidades`, { method: 'POST', body: dados })
}

export function atualizarNecessidade(id: number, dados: NecessidadeDados): Promise<Necessidade> {
  return api<Necessidade>(`/api/necessidades/${id}`, { method: 'PUT', body: dados })
}

export function excluirNecessidade(id: number): Promise<void> {
  return api<void>(`/api/necessidades/${id}`, { method: 'DELETE' })
}
