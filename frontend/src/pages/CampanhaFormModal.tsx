import { Alert, Button, Group, Modal, NumberInput, Stack, Textarea, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { atualizarCampanha, criarCampanha, type Campanha, type CampanhaDados } from '../api/campanhas'
import { aplicarErrosDeCampo } from '../util/erros'

type Valores = {
  titulo: string
  descricao: string
  dataInicio: string
  dataFim: string
  metaValor: number | string
}

type Props = { aberto: boolean; campanha: Campanha | null; aoFechar(): void; aoSalvar?(c: Campanha): void }

function paraDados(v: Valores): CampanhaDados {
  return {
    titulo: v.titulo.trim(),
    descricao: v.descricao.trim() || null,
    dataInicio: v.dataInicio,
    dataFim: v.dataFim || null,
    metaValor: typeof v.metaValor === 'number' ? v.metaValor : null,
  }
}

/** Cria (campanha = null) ou edita uma campanha. */
export function CampanhaFormModal({ aberto, campanha, aoFechar, aoSalvar }: Props) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)

  const form = useForm<Valores>({
    initialValues: {
      titulo: campanha?.titulo ?? '',
      descricao: campanha?.descricao ?? '',
      dataInicio: campanha?.dataInicio ?? '',
      dataFim: campanha?.dataFim ?? '',
      metaValor: campanha?.metaValor ?? '',
    },
    validate: {
      titulo: (v) => (v.trim() ? null : 'Informe o título.'),
      dataInicio: (v) => (v ? null : 'Informe a data de início.'),
      // Datas ISO (AAAA-MM-DD) se comparam corretamente como texto.
      dataFim: (v, valores) =>
        !v || !valores.dataInicio || v >= valores.dataInicio
          ? null
          : 'A data de término deve ser igual ou posterior à de início.',
    },
  })

  const salvar = useMutation({
    mutationFn: (dados: CampanhaDados) => (campanha ? atualizarCampanha(campanha.id, dados) : criarCampanha(dados)),
    onSuccess: async (salva) => {
      await queryClient.invalidateQueries({ queryKey: ['campanhas'] })
      await queryClient.invalidateQueries({ queryKey: ['campanha', salva.id] })
      aoSalvar?.(salva)
      aoFechar()
    },
    onError: (e) => {
      if (!aplicarErrosDeCampo(form, e)) {
        setErroGeral(e.message)
      }
    },
  })

  return (
    <Modal closeOnClickOutside={false} opened={aberto} onClose={aoFechar} title={campanha ? 'Editar campanha' : 'Nova campanha'}>
      <form
        noValidate
        onSubmit={form.onSubmit((v) => {
          setErroGeral(null)
          salvar.mutate(paraDados(v))
        })}
      >
        <Stack>
          {erroGeral && <Alert color="red">{erroGeral}</Alert>}
          <TextInput label="Título" {...form.getInputProps('titulo')} />
          <Textarea label="Descrição" autosize minRows={2} {...form.getInputProps('descricao')} />
          <Group grow>
            <TextInput label="Data de início" type="date" {...form.getInputProps('dataInicio')} />
            <TextInput label="Data de término" type="date" {...form.getInputProps('dataFim')} />
          </Group>
          <NumberInput
            label="Meta em R$ (opcional)"
            prefix="R$ "
            decimalSeparator=","
            thousandSeparator="."
            decimalScale={2}
            min={0}
            hideControls
            {...form.getInputProps('metaValor')}
          />
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
