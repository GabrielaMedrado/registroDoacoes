import { expect, test } from 'vitest'
import { formatarData, formatarMoeda, formatarQuantidade, ROTULO_STATUS, ROTULO_UNIDADE } from './formatos'

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
