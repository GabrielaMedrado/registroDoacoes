import type { Papel } from '../api/auth'
import type { StatusCampanha, Unidade } from '../api/campanhas'
import type { Doacao, FormaPagamento } from '../api/doacoes'

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

export const ROTULO_FORMA: Record<FormaPagamento, string> = { PIX: 'PIX', ESPECIE: 'Espécie', OUTRO: 'Outro' }

/** Data de hoje no fuso do aparelho. toISOString() usaria UTC e viraria o dia depois das 21h em Brasília. */
export function hojeISO(): string {
  const agora = new Date()
  const doisDigitos = (n: number) => String(n).padStart(2, '0')
  return `${agora.getFullYear()}-${doisDigitos(agora.getMonth() + 1)}-${doisDigitos(agora.getDate())}`
}

/** 'Arroz 5 kg, Feijão 2,5 kg' ou 'R$ 50,00 (PIX)'. */
export function resumoDoacao(d: Doacao): string {
  if (d.tipo === 'DINHEIRO') {
    return `${formatarMoeda(d.valor)}${d.formaPagamento ? ` (${ROTULO_FORMA[d.formaPagamento]})` : ''}`
  }
  return d.itens.map((i) => `${i.item} ${formatarQuantidade(i.quantidade, i.unidade)}`).join(', ')
}

export const ROTULO_PAPEL: Record<Papel, string> = { ADMIN: 'Administrador', VOLUNTARIO: 'Voluntário' }
