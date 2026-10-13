import type { QueryClient } from '@tanstack/react-query'
import type { StatusCampanha, Unidade } from './campanhas'
import { api } from './client'

export type NecessidadeProgresso = {
  id: number
  item: string
  unidade: Unidade
  quantidadeMeta: number
  observacao: string | null
  arrecadado: number
  pendente: number
  excedente: number
  percentual: number
}

export type ProgressoCampanha = {
  campanhaId: number
  titulo: string
  status: StatusCampanha
  /** Nulo quando a campanha não tem necessidades (sem barra de itens). */
  percentualGeral: number | null
  totalDinheiro: number
  metaValor: number | null
  percentualValor: number | null
  necessidades: NecessidadeProgresso[]
}

export type Pendencia = {
  campanhaId: number
  campanhaTitulo: string
  necessidadeId: number
  item: string
  unidade: Unidade
  pendente: number
  percentual: number
}

export type Painel = { campanhasAtivas: ProgressoCampanha[]; maioresPendencias: Pendencia[] }

export function buscarProgresso(campanhaId: number): Promise<ProgressoCampanha> {
  return api<ProgressoCampanha>(`/api/campanhas/${campanhaId}/progresso`)
}

export function buscarPainel(): Promise<Painel> {
  return api<Painel>('/api/painel')
}

/** Depois de registrar, editar ou excluir doação: progresso, painel e relatórios mudam. */
export async function invalidarTotais(qc: QueryClient): Promise<void> {
  await Promise.all([
    qc.invalidateQueries({ queryKey: ['progresso'] }),
    qc.invalidateQueries({ queryKey: ['painel'] }),
    qc.invalidateQueries({ queryKey: ['relatorios'] }),
  ])
}
