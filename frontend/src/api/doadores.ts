import { api } from './client'
import type { Doacao } from './doacoes'
import type { Pagina } from './tipos'

export type Doador = {
  id: number
  nome: string
  telefone: string | null
  email: string | null
  observacao: string | null
}

export type DoadorDados = Omit<Doador, 'id'>

export function listarDoadores(busca: string, pagina: number): Promise<Pagina<Doador>> {
  const params = new URLSearchParams()
  if (busca.trim()) {
    params.set('busca', busca.trim())
  }
  params.set('pagina', String(pagina))
  return api<Pagina<Doador>>(`/api/doadores?${params}`)
}

export function buscarDoador(id: number): Promise<Doador> {
  return api<Doador>(`/api/doadores/${id}`)
}

export function criarDoador(dados: DoadorDados): Promise<Doador> {
  return api<Doador>('/api/doadores', { method: 'POST', body: dados })
}

export function atualizarDoador(id: number, dados: DoadorDados): Promise<Doador> {
  return api<Doador>(`/api/doadores/${id}`, { method: 'PUT', body: dados })
}

export function anonimizarDoador(id: number): Promise<Doador> {
  return api<Doador>(`/api/doadores/${id}/anonimizar`, { method: 'POST' })
}

export function listarDoacoesDoDoador(id: number, pagina: number): Promise<Pagina<Doacao>> {
  return api<Pagina<Doacao>>(`/api/doadores/${id}/doacoes?pagina=${pagina}`)
}
