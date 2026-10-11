import type { Papel } from '../api/auth'
import type { StatusCampanha, Unidade } from '../api/campanhas'

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const NUMERO = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 })

/** 'AAAA-MM-DD' → 'DD/MM/AAAA'. Não usa Date: new Date('2026-10-11') em Brasília cairia em 10/10. */
export function formatarData(iso: string | null): string {
  if (!iso) return '—'
  const [ano, mes, dia] = iso.split('-')
  return `${dia}/${mes}/${ano}`
}

export function formatarMoeda(valor: number | null): string {
  return valor == null ? '—' : MOEDA.format(valor)
}

export function formatarQuantidade(quantidade: number, unidade: Unidade): string {
  return `${NUMERO.format(quantidade)} ${ROTULO_UNIDADE[unidade]}`
}

export const ROTULO_STATUS: Record<StatusCampanha, string> = {
  RASCUNHO: 'Rascunho',
  ATIVA: 'Ativa',
  ENCERRADA: 'Encerrada',
}

export const COR_STATUS: Record<StatusCampanha, string> = {
  RASCUNHO: 'gray',
  ATIVA: 'green',
  ENCERRADA: 'red',
}

export const ROTULO_UNIDADE: Record<Unidade, string> = {
  UNIDADE: 'unidade(s)',
  KG: 'kg',
  LITRO: 'litro(s)',
  PACOTE: 'pacote(s)',
  CAIXA: 'caixa(s)',
  PAR: 'par(es)',
}

export const ROTULO_PAPEL: Record<Papel, string> = { ADMIN: 'Administrador', VOLUNTARIO: 'Voluntário' }
