import { Alert, Button, Group, Modal, PasswordInput, Select, Stack, Switch, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { Papel } from '../api/auth'
import { atualizarUsuario, criarUsuario, type Usuario } from '../api/usuarios'
import { aplicarErrosDeCampo } from '../util/erros'
import { ROTULO_PAPEL } from '../util/formatos'

const EMAIL_VALIDO = /^\S+@\S+\.\S+$/

type Valores = { nome: string; email: string; papel: Papel; senha: string; ativo: boolean }

type Props = { aberto: boolean; usuario: Usuario | null; aoFechar(): void }

/** Cria (usuario = null) ou edita um usuário. */
export function UsuarioFormModal({ aberto, usuario, aoFechar }: Props) {
  const criando = usuario === null
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)

  const form = useForm<Valores>({
    initialValues: {
      nome: usuario?.nome ?? '',
      email: usuario?.email ?? '',
      papel: usuario?.papel ?? 'VOLUNTARIO',
      senha: '',
      ativo: usuario?.ativo ?? true,
    },
    validate: {
      nome: (v) => (v.trim() ? null : 'Informe o nome.'),
      email: (v) => (EMAIL_VALIDO.test(v.trim()) ? null : 'Informe um e-mail válido.'),
      senha: (v) => (!criando || v.length >= 8 ? null : 'A senha deve ter ao menos 8 caracteres.'),
    },
  })

  const salvar = useMutation({
    mutationFn: (v: Valores) => {
      const comum = { nome: v.nome.trim(), email: v.email.trim(), papel: v.papel }
      return criando
        ? criarUsuario({ ...comum, senha: v.senha })
        : atualizarUsuario(usuario.id, { ...comum, ativo: v.ativo })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['usuarios'] })
      aoFechar()
    },
    onError: (e) => {
      if (!aplicarErrosDeCampo(form, e)) {
        setErroGeral(e.message)
      }
    },
  })

  return (
    <Modal opened={aberto} onClose={aoFechar} title={criando ? 'Novo usuário' : 'Editar usuário'}>
      <form
        noValidate
        onSubmit={form.onSubmit((v) => {
          setErroGeral(null)
          salvar.mutate(v)
        })}
      >
        <Stack>
          {erroGeral && <Alert color="red">{erroGeral}</Alert>}
          <TextInput label="Nome" {...form.getInputProps('nome')} />
          <TextInput label="E-mail" type="email" {...form.getInputProps('email')} />
          <Select
            label="Papel"
            data={[
              { value: 'VOLUNTARIO', label: ROTULO_PAPEL.VOLUNTARIO },
              { value: 'ADMIN', label: ROTULO_PAPEL.ADMIN },
            ]}
            allowDeselect={false}
            {...form.getInputProps('papel')}
          />
          {criando ? (
            <PasswordInput label="Senha" autoComplete="new-password" {...form.getInputProps('senha')} />
          ) : (
            <Switch label="Ativo" {...form.getInputProps('ativo', { type: 'checkbox' })} />
          )}
          <Group justify="flex-end">
            <Button variant="default" onClick={aoFechar}>
              Cancelar
            </Button>
            <Button type="submit" loading={salvar.isPending}>
              Salvar
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  )
}
