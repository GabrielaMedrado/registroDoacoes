import { vi } from 'vitest'
import type { UsuarioLogado } from '../api/auth'

export const ADMIN: UsuarioLogado = { id: 1, nome: 'Ana Admin', email: 'ana@igreja.org', papel: 'ADMIN' }
export const VOLUNTARIO: UsuarioLogado = { id: 2, nome: 'Vitor', email: 'vitor@igreja.org', papel: 'VOLUNTARIO' }

/** Funções de api/auth substituídas nos testes (usar junto com vi.mock('<caminho>/api/auth')). */
export const authMock = {
  obterCsrf: vi.fn(),
  usuarioAtual: vi.fn(),
  entrar: vi.fn(),
  sair: vi.fn(),
  alterarSenha: vi.fn(),
}

export function prepararAuth(usuario: UsuarioLogado | null): void {
  authMock.obterCsrf.mockResolvedValue(undefined)
  authMock.usuarioAtual.mockResolvedValue(usuario)
  authMock.entrar.mockResolvedValue(usuario)
  authMock.sair.mockResolvedValue(undefined)
  authMock.alterarSenha.mockResolvedValue(undefined)
}
