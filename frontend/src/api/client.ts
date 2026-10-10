export type ErroCampo = { campo: string; mensagem: string }

export type ProblemDetail = {
  status: number
  title?: string
  detail?: string
  erros?: ErroCampo[]
}

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

const MENSAGEM_PADRAO = 'Erro inesperado. Tente novamente.'

export class ApiError extends Error {
  status: number
  problem?: ProblemDetail

  constructor(status: number, problem?: ProblemDetail) {
    super(problem?.detail ?? MENSAGEM_PADRAO)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

let aoNaoAutenticado: () => void = () => {}

/** Registra o que fazer quando a sessão expira (401 em rotas que não são de autenticação). */
export function definirAoNaoAutenticado(fn: () => void): void {
  aoNaoAutenticado = fn
}

function lerCookie(nome: string): string | undefined {
  const par = document.cookie.split('; ').find((c) => c.startsWith(`${nome}=`))
  return par ? decodeURIComponent(par.substring(nome.length + 1)) : undefined
}

async function lerProblema(resposta: Response): Promise<ProblemDetail | undefined> {
  try {
    const texto = await resposta.text()
    return texto ? (JSON.parse(texto) as ProblemDetail) : undefined
  } catch {
    return undefined
  }
}

export async function api<T>(caminho: string, opcoes?: { method?: Metodo; body?: unknown }): Promise<T> {
  const method = opcoes?.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (opcoes?.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET') {
    const token = lerCookie('XSRF-TOKEN')
    if (token) {
      headers['X-XSRF-TOKEN'] = token
    }
  }

  const resposta = await fetch(caminho, {
    method,
    headers,
    credentials: 'same-origin',
    body: opcoes?.body === undefined ? undefined : JSON.stringify(opcoes.body),
  })

  if (!resposta.ok) {
    if (resposta.status === 401 && !caminho.startsWith('/api/auth/')) {
      aoNaoAutenticado()
    }
    throw new ApiError(resposta.status, await lerProblema(resposta))
  }
  if (resposta.status === 204) {
    return undefined as T
  }
  const texto = await resposta.text()
  return (texto ? JSON.parse(texto) : undefined) as T
}
