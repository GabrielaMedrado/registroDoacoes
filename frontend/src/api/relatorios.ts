import type { Unidade } from './campanhas'
import { api, apiTexto } from './client'
import type { TipoDoacao } from './doacoes'
import type { ProgressoCampanha } from './progresso'

export type LinhaRelatorioDoacao = {
  id: number
  dataDoacao: string
  campanhaTitulo: string
  doadorNome: string
  tipo: TipoDoacao
  descricao: string
  valor: number | null
}

export type TotalItem = { item: string; unidade: Unidade; quantidade: number }

export type RelatorioDoacoes = {
  de: string
  ate: string
  quantidade: number
  totalDinheiro: number
  totaisItens: TotalItem[]
  doacoes: LinhaRelatorioDoacao[]
}

export type FiltroRelatorioDoacoes = { de: string; ate: string; campanhaId?: number; tipo?: TipoDoacao }

function paramsDoacoes(filtro: FiltroRelatorioDoacoes): URLSearchParams {
  const params = new URLSearchParams({ de: filtro.de, ate: filtro.ate })
  if (filtro.campanhaId) params.set('campanhaId', String(filtro.campanhaId))
  if (filtro.tipo) params.set('tipo', filtro.tipo)
  return params
}

export function buscarRelatorioCampanha(id: number): Promise<ProgressoCampanha> {
  return api<ProgressoCampanha>(`/api/relatorios/campanha/${id}`)
}

export function buscarRelatorioDoacoes(filtro: FiltroRelatorioDoacoes): Promise<RelatorioDoacoes> {
  return api<RelatorioDoacoes>(`/api/relatorios/doacoes?${paramsDoacoes(filtro)}`)
}

export function buscarPendencias(campanhaId?: number): Promise<string> {
  return apiTexto(campanhaId ? `/api/relatorios/pendencias?campanhaId=${campanhaId}` : '/api/relatorios/pendencias')
}

/** Links de download: o navegador envia o cookie da sessão e baixa o arquivo. */
export function urlCsvCampanha(id: number): string {
  return `/api/relatorios/campanha/${id}?formato=csv`
}

export function urlCsvDoacoes(filtro: FiltroRelatorioDoacoes): string {
  const params = paramsDoacoes(filtro)
  params.set('formato', 'csv')
  return `/api/relatorios/doacoes?${params}`
}
