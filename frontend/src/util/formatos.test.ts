import { expect, test, vi } from 'vitest'
import type { Doacao } from '../api/doacoes'
import {
  formatarData,
  formatarMoeda,
  formatarQuantidade,
  hojeISO,
  resumoDoacao,
  ROTULO_FORMA,
  ROTULO_STATUS,
  ROTULO_UNIDADE,
} from './formatos'

test('formatarData não desloca o dia por fuso horário', () => {
  expect(formatarData('2026-10-11')).toBe('11/10/2026')
  expect(formatarData('2026-01-01')).toBe('01/01/2026')
})

test('formatarData de nulo mostra travessão', () => {
  expect(formatarData(null)).toBe('—')
})

test('formatarMoeda usa o padrão brasileiro', () => {
  expect(formatarMoeda(1234.5).replace(/\s/g, ' ')).toBe('R$ 1.234,50')
  expect(formatarMoeda(null)).toBe('—')
})

test('formatarQuantidade usa vírgula e rótulo da unidade', () => {
  expect(formatarQuantidade(2.5, 'KG')).toBe('2,5 kg')
  expect(formatarQuantidade(10, 'PACOTE')).toBe('10 pacote(s)')
})

test('rótulos de status e unidade', () => {
  expect(ROTULO_STATUS).toEqual({ RASCUNHO: 'Rascunho', ATIVA: 'Ativa', ENCERRADA: 'Encerrada' })
  expect(ROTULO_UNIDADE.PAR).toBe('par(es)')
})

test('hojeISO usa a data local mesmo depois das 21h em Brasília', () => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-12T02:30:00Z')) // 23:30 de 11/10 em Brasília (UTC-3)
  expect(hojeISO()).toBe('2026-10-11')
  vi.useRealTimers()
})

const ITENS: Doacao = {
  id: 1,
  campanhaId: 1,
  campanhaTitulo: 'Cesta',
  doadorId: null,
  doadorNome: null,
  tipo: 'ITEM',
  valor: null,
  formaPagamento: null,
  dataDoacao: '2026-10-11',
  observacao: null,
  registradoPorNome: 'Vitor',
  itens: [
    { necessidadeId: 10, item: 'Arroz', unidade: 'KG', quantidade: 5 },
    { necessidadeId: 11, item: 'Feijão', unidade: 'KG', quantidade: 2.5 },
  ],
  campanhaAtiva: true,
  podeAlterar: true,
}

test('resumoDoacao de itens', () => {
  expect(resumoDoacao(ITENS)).toBe('Arroz 5 kg, Feijão 2,5 kg')
})

test('resumoDoacao em dinheiro', () => {
  const dinheiro: Doacao = { ...ITENS, tipo: 'DINHEIRO', valor: 50, formaPagamento: 'PIX', itens: [] }
  expect(resumoDoacao(dinheiro).replace(/\s/g, ' ')).toBe('R$ 50,00 (PIX)')
  expect(ROTULO_FORMA.ESPECIE).toBe('Espécie')
})
