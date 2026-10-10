import { api, ApiError } from './client'

export type Papel = 'ADMIN' | 'VOLUNTARIO'

export type UsuarioLogado = { id: number; nome: string; email: string; papel: Papel }

/** Garante que o cookie XSRF-TOKEN exista antes de qualquer POST/PUT/DELETE. */
export function obterCsrf(): Promise<void> {
  return api<void>('/api/auth/csrf')
}

export function entrar(email: string, senha: string): Promise<UsuarioLogado> {
  return api<UsuarioLogado>('/api/auth/login', { method: 'POST', body: { email, senha } })
}

export function sair(): Promise<void> {
  return api<void>('/api/auth/logout', { method: 'POST' })
}

export async function usuarioAtual(): Promise<UsuarioLogado | null> {
  try {
    return await api<UsuarioLogado>('/api/auth/me')
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      return null
    }
    throw e
  }
}

export function alterarSenha(senhaAtual: string, novaSenha: string): Promise<void> {
  return api<void>('/api/auth/minha-senha', { method: 'PUT', body: { senhaAtual, novaSenha } })
}
