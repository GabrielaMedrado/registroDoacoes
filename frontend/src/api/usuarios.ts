import type { Papel } from './auth'
import { api } from './client'
import type { Pagina } from './tipos'

export type Usuario = { id: number; nome: string; email: string; papel: Papel; ativo: boolean; criadoEm: string }

export type NovoUsuario = { nome: string; email: string; papel: Papel; senha: string }

export type UsuarioEditado = { nome: string; email: string; papel: Papel; ativo: boolean }

export function listarUsuarios(pagina: number): Promise<Pagina<Usuario>> {
  return api<Pagina<Usuario>>(`/api/usuarios?pagina=${pagina}`)
}

export function criarUsuario(dados: NovoUsuario): Promise<Usuario> {
  return api<Usuario>('/api/usuarios', { method: 'POST', body: dados })
}

export function atualizarUsuario(id: number, dados: UsuarioEditado): Promise<Usuario> {
  return api<Usuario>(`/api/usuarios/${id}`, { method: 'PUT', body: dados })
}

export function redefinirSenhaUsuario(id: number, novaSenha: string): Promise<void> {
  return api<void>(`/api/usuarios/${id}/redefinir-senha`, { method: 'POST', body: { novaSenha } })
}
