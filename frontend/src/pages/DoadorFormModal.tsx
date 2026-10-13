import { Alert, Button, Group, Modal, Stack, Textarea, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { atualizarDoador, criarDoador, type Doador, type DoadorDados } from '../api/doadores'
import { aplicarErrosDeCampo } from '../util/erros'

type Valores = { nome: string; telefone: string; email: string; observacao: string }

type Props = { aberto: boolean; doador: Doador | null; aoFechar(): void; aoSalvar?(d: Doador): void }

function paraDados(v: Valores): DoadorDados {
  return {
    nome: v.nome.trim(),
    telefone: v.telefone.trim() || null,
    email: v.email.trim() || null,
    observacao: v.observacao.trim() || null,
  }
}

/** Cria (doador = null) ou edita um doador. Também é usado no formulário de doação. */
export function DoadorFormModal({ aberto, doador, aoFechar, aoSalvar }: Props) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)

  const form = useForm<Valores>({
    initialValues: {
      nome: doador?.nome ?? '',
      telefone: doador?.telefone ?? '',
      email: doador?.email ?? '',
      observacao: doador?.observacao ?? '',
    },
    validate: {
      nome: (v) => (v.trim() ? null : 'Informe o nome.'),
    },
  })

  const salvar = useMutation({
    mutationFn: (dados: DoadorDados) => (doador ? atualizarDoador(doador.id, dados) : criarDoador(dados)),
    onSuccess: async (salvo) => {
      await queryClient.invalidateQueries({ queryKey: ['doadores'] })
      await queryClient.invalidateQueries({ queryKey: ['doador', salvo.id] })
      aoSalvar?.(salvo)
      aoFechar()
    },
    onError: (e) => {
      if (!aplicarErrosDeCampo(form, e)) {
        setErroGeral(e.message)
      }
    },
  })

  return (
    <Modal closeOnClickOutside={false} opened={aberto} onClose={aoFechar} title={doador ? 'Editar doador' : 'Novo doador'}>
      <form
        noValidate
        onSubmit={(e) => {
          // O modal pode estar dentro de outro formulário (o da doação): no React o submit
          // atravessa o portal e enviaria a doação junto.
          e.stopPropagation()
          form.onSubmit((v) => {
            setErroGeral(null)
            salvar.mutate(paraDados(v))
          })(e)
        }}
      >
        <Stack>
          {erroGeral && <Alert color="red">{erroGeral}</Alert>}
          <TextInput label="Nome" {...form.getInputProps('nome')} />
          <TextInput label="Telefone" type="tel" {...form.getInputProps('telefone')} />
          <TextInput label="E-mail" type="email" {...form.getInputProps('email')} />
          <Textarea label="Observação" autosize minRows={2} {...form.getInputProps('observacao')} />
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
