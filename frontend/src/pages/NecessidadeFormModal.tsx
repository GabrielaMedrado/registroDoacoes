import { Alert, Button, Group, Modal, NumberInput, Select, Stack, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  atualizarNecessidade,
  criarNecessidade,
  type Necessidade,
  type NecessidadeDados,
  type Unidade,
} from '../api/campanhas'
import { aplicarErrosDeCampo } from '../util/erros'
import { ROTULO_UNIDADE } from '../util/formatos'

type Valores = { item: string; unidade: Unidade | null; quantidadeMeta: number | string; observacao: string }

type Props = { campanhaId: number; necessidade: Necessidade | null; aberto: boolean; aoFechar(): void }

const UNIDADES = (Object.keys(ROTULO_UNIDADE) as Unidade[]).map((u) => ({ value: u, label: ROTULO_UNIDADE[u] }))

/** Cria (necessidade = null) ou edita uma necessidade da campanha. */
export function NecessidadeFormModal({ campanhaId, necessidade, aberto, aoFechar }: Props) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)

  const form = useForm<Valores>({
    initialValues: {
      item: necessidade?.item ?? '',
      unidade: necessidade?.unidade ?? null,
      quantidadeMeta: necessidade?.quantidadeMeta ?? '',
      observacao: necessidade?.observacao ?? '',
    },
    validate: {
      item: (v) => (v.trim() ? null : 'Informe o item.'),
      unidade: (v) => (v ? null : 'Escolha a unidade.'),
      quantidadeMeta: (v) => (typeof v === 'number' && v > 0 ? null : 'Informe uma quantidade maior que zero.'),
    },
  })

  const salvar = useMutation({
    mutationFn: (dados: NecessidadeDados) =>
      necessidade ? atualizarNecessidade(necessidade.id, dados) : criarNecessidade(campanhaId, dados),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['necessidades', campanhaId] })
      await queryClient.invalidateQueries({ queryKey: ['progresso', campanhaId] })
      aoFechar()
    },
    onError: (e) => {
      if (!aplicarErrosDeCampo(form, e)) {
        setErroGeral(e.message)
      }
    },
  })

  return (
    <Modal closeOnClickOutside={false} opened={aberto} onClose={aoFechar} title={necessidade ? 'Editar necessidade' : 'Nova necessidade'}>
      <form
        noValidate
        onSubmit={form.onSubmit((v) => {
          setErroGeral(null)
          salvar.mutate({
            item: v.item.trim(),
            unidade: v.unidade as Unidade,
            quantidadeMeta: v.quantidadeMeta as number,
            observacao: v.observacao.trim() || null,
          })
        })}
      >
        <Stack>
          {erroGeral && <Alert color="red">{erroGeral}</Alert>}
          <TextInput label="Item" placeholder="Ex.: Arroz" {...form.getInputProps('item')} />
          <Group grow align="flex-start">
            <NumberInput
              label="Quantidade"
              decimalSeparator=","
              decimalScale={2}
              min={0}
              hideControls
              {...form.getInputProps('quantidadeMeta')}
            />
            <Select label="Unidade" data={UNIDADES} allowDeselect={false} {...form.getInputProps('unidade')} />
          </Group>
          <TextInput label="Observação" {...form.getInputProps('observacao')} />
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
