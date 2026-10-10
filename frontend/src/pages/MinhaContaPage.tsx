import { Alert, Button, Paper, PasswordInput, Stack, Title } from '@mantine/core'
import { useForm } from '@mantine/form'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { alterarSenha } from '../api/auth'
import { ApiError } from '../api/client'

type Valores = { senhaAtual: string; novaSenha: string; confirmacao: string }

export function MinhaContaPage() {
  const [sucesso, setSucesso] = useState(false)
  const [erroGeral, setErroGeral] = useState<string | null>(null)

  const form = useForm<Valores>({
    initialValues: { senhaAtual: '', novaSenha: '', confirmacao: '' },
    validate: {
      senhaAtual: (v) => (v ? null : 'Informe a senha atual.'),
      novaSenha: (v) => (v.length >= 8 ? null : 'A nova senha deve ter ao menos 8 caracteres.'),
      confirmacao: (v, valores) => (v === valores.novaSenha ? null : 'As senhas não conferem.'),
    },
  })

  const mutacao = useMutation({
    mutationFn: (v: Valores) => alterarSenha(v.senhaAtual, v.novaSenha),
    onSuccess: () => {
      setSucesso(true)
      form.reset()
    },
    onError: (e) => {
      const erros = e instanceof ApiError ? e.problem?.erros : undefined
      if (erros?.length) {
        erros.forEach((erro) => form.setFieldError(erro.campo, erro.mensagem))
      } else {
        setErroGeral(e.message)
      }
    },
  })

  function enviar(valores: Valores) {
    setSucesso(false)
    setErroGeral(null)
    mutacao.mutate(valores)
  }

  return (
    <Stack maw={420}>
      <Title order={2}>Minha conta</Title>
      <Paper withBorder p="lg" radius="md">
        <form onSubmit={form.onSubmit(enviar)} noValidate>
          <Stack>
            {sucesso && <Alert color="green">Senha alterada com sucesso.</Alert>}
            {erroGeral && <Alert color="red">{erroGeral}</Alert>}
            <PasswordInput label="Senha atual" autoComplete="current-password" {...form.getInputProps('senhaAtual')} />
            <PasswordInput label="Nova senha" autoComplete="new-password" {...form.getInputProps('novaSenha')} />
            <PasswordInput
              label="Confirmar nova senha"
              autoComplete="new-password"
              {...form.getInputProps('confirmacao')}
            />
            <Button type="submit" loading={mutacao.isPending}>
              Alterar senha
            </Button>
          </Stack>
        </form>
      </Paper>
    </Stack>
  )
}
