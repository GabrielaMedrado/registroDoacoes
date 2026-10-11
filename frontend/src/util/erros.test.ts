import { expect, test, vi } from 'vitest'
import { ApiError } from '../api/client'
import { aplicarErrosDeCampo } from './erros'

test('aplicarErrosDeCampo aplica cada erro e devolve true', () => {
  const form = { setFieldError: vi.fn() }
  const erro = new ApiError(400, {
    status: 400,
    erros: [
      { campo: 'email', mensagem: 'Já existe' },
      { campo: 'nome', mensagem: 'Obrigatório' },
    ],
  })

  expect(aplicarErrosDeCampo(form, erro)).toBe(true)
  expect(form.setFieldError).toHaveBeenCalledWith('email', 'Já existe')
  expect(form.setFieldError).toHaveBeenCalledWith('nome', 'Obrigatório')
})

test('aplicarErrosDeCampo devolve false sem erros de campo', () => {
  const form = { setFieldError: vi.fn() }

  expect(aplicarErrosDeCampo(form, new Error('x'))).toBe(false)
  expect(aplicarErrosDeCampo(form, new ApiError(409, { status: 409, detail: 'Conflito' }))).toBe(false)
  expect(form.setFieldError).not.toHaveBeenCalled()
})

test('aplicarErrosDeCampo converte itens[0].quantidade (Spring) para itens.0.quantidade (formulário)', () => {
  const form = { setFieldError: vi.fn() }
  const erro = new ApiError(400, { status: 400, erros: [{ campo: 'itens[0].quantidade', mensagem: 'Valor inválido' }] })

  aplicarErrosDeCampo(form, erro)

  expect(form.setFieldError).toHaveBeenCalledWith('itens.0.quantidade', 'Valor inválido')
})
