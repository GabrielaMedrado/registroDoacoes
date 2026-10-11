import { Alert, Button, Group, Modal, PasswordInput, Stack, Text } from '@mantine/core'
import { useForm } from '@mantine/form'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { redefinirSenhaUsuario, type Usuario } from '../api/usuarios'
import { aplicarErrosDeCampo } from '../util/erros'

type Props = { usuario: Usuario | null; aoFechar(): void }

export function RedefinirSenhaModal({ usuario, aoFechar }: Props) {
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const form = useForm({
    initialValues: { novaSenha: '' },
    validate: { novaSenha: (v) => (v.length >= 8 ? null : 'A senha deve ter ao menos 8 caracteres.') },
  })

  const redefinir = useMutation({
    mutationFn: (novaSenha: string) => redefinirSenhaUsuario(usuario!.id, novaSenha),
    onSuccess: aoFechar,
    onError: (e) => {
      if (!aplicarErrosDeCampo(form, e)) {
        setErroGeral(e.message)
      }
    },
  })

  return (
    <Modal closeOnClickOutside={false} opened={usuario !== null} onClose={aoFechar} title="Redefinir senha">
      <form
        noValidate
        onSubmit={form.onSubmit((v) => {
          setErroGeral(null)
          redefinir.mutate(v.novaSenha)
        })}
      >
        <Stack>
          <Text size="sm">
            Defina uma nova senha para <strong>{usuario?.nome}</strong> e informe-a pessoalmente.
          </Text>
          {erroGeral && <Alert color="red">{erroGeral}</Alert>}
          <PasswordInput label="Nova senha" autoComplete="new-password" {...form.getInputProps('novaSenha')} />
          <Group justify="flex-end">
            <Button variant="default" onClick={aoFechar}>
              Cancelar
            </Button>
            <Button type="submit" loading={redefinir.isPending}>
              Redefinir
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  )
}
