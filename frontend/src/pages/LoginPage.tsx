import { Alert, Button, Center, Paper, PasswordInput, Stack, TextInput, Title } from '@mantine/core'
import { useForm } from '@mantine/form'
import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

const EMAIL_VALIDO = /^\S+@\S+\.\S+$/

export function LoginPage() {
  const { usuario, entrar, sessaoExpirou } = useAuth()
  const navigate = useNavigate()
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const form = useForm({
    initialValues: { email: '', senha: '' },
    validate: {
      email: (v) => (EMAIL_VALIDO.test(v.trim()) ? null : 'Informe um e-mail válido.'),
      senha: (v) => (v ? null : 'Informe a senha.'),
    },
  })

  if (usuario) {
    return <Navigate to="/" replace />
  }

  async function enviar(valores: { email: string; senha: string }) {
    setErro(null)
    setEnviando(true)
    try {
      await entrar(valores.email.trim(), valores.senha)
      navigate('/', { replace: true })
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Erro inesperado. Tente novamente.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Center mih="100vh" p="md">
      <Paper withBorder shadow="sm" p="xl" radius="md" w="100%" maw={400}>
        <form onSubmit={form.onSubmit(enviar)} noValidate>
          <Stack>
            <Title order={1} size="h2" ta="center">
              Gestão de Doações
            </Title>
            {sessaoExpirou && !erro && <Alert color="yellow">Sua sessão expirou. Entre novamente.</Alert>}
            {erro && <Alert color="red">{erro}</Alert>}
            <TextInput label="E-mail" type="email" autoComplete="username" {...form.getInputProps('email')} />
            <PasswordInput label="Senha" autoComplete="current-password" {...form.getInputProps('senha')} />
            <Button type="submit" loading={enviando} fullWidth>
              Entrar
            </Button>
          </Stack>
        </form>
      </Paper>
    </Center>
  )
}
