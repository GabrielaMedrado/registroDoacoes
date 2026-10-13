import { ApiError } from '../api/client'

/** Leva os erros de campo vindos da API (400 com `erros`) para o formulário. Devolve true se aplicou algum. */
export function aplicarErrosDeCampo(form: { setFieldError(campo: string, msg: string): void }, erro: unknown): boolean {
  const erros = erro instanceof ApiError ? erro.problem?.erros : undefined
  if (!erros?.length) {
    return false
  }
  // O Spring indexa listas como itens[0].quantidade; o formulário usa itens.0.quantidade.
  erros.forEach((e) => form.setFieldError(e.campo.replace(/\[(\d+)\]/g, '.$1'), e.mensagem))
  return true
}
