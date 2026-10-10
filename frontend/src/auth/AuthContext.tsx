import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import * as authApi from '../api/auth'
import type { UsuarioLogado } from '../api/auth'
import { definirAoNaoAutenticado } from '../api/client'

type AuthValor = {
  usuario: UsuarioLogado | null
  carregando: boolean
  sessaoExpirou: boolean
  entrar(email: string, senha: string): Promise<void>
  sair(): Promise<void>
}

const AuthContext = createContext<AuthValor | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [usuario, setUsuario] = useState<UsuarioLogado | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [sessaoExpirou, setSessaoExpirou] = useState(false)

  useEffect(() => {
    let ativo = true
    ;(async () => {
      try {
        await authApi.obterCsrf()
        const atual = await authApi.usuarioAtual()
        if (ativo) setUsuario(atual)
      } catch {
        if (ativo) setUsuario(null)
      } finally {
        if (ativo) setCarregando(false)
      }
    })()
    return () => {
      ativo = false
    }
  }, [])

  useEffect(() => {
    definirAoNaoAutenticado(() => {
      setUsuario(null)
      setSessaoExpirou(true)
      navigate('/login', { replace: true })
    })
  }, [navigate])

  const entrar = useCallback(async (email: string, senha: string) => {
    const logado = await authApi.entrar(email, senha)
    // O Spring renova o token CSRF na autenticação; busca o novo antes do próximo POST.
    await authApi.obterCsrf()
    setSessaoExpirou(false)
    setUsuario(logado)
  }, [])

  const sair = useCallback(async () => {
    try {
      await authApi.sair()
    } finally {
      setUsuario(null)
      navigate('/login', { replace: true })
    }
  }, [navigate])

  const valor = useMemo(
    () => ({ usuario, carregando, sessaoExpirou, entrar, sair }),
    [usuario, carregando, sessaoExpirou, entrar, sair],
  )
  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react/only-export-components
export function useAuth(): AuthValor {
  const valor = useContext(AuthContext)
  if (!valor) {
    throw new Error('useAuth deve ser usado dentro de <AuthProvider>')
  }
  return valor
}
