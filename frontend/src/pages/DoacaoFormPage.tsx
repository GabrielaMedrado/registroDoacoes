import {
  ActionIcon,
  Alert,
  Anchor,
  Button,
  Center,
  Group,
  Input,
  Loader,
  NumberInput,
  SegmentedControl,
  Select,
  Stack,
  Text,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core'
import { useForm, type FormErrors } from '@mantine/form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { listarCampanhas, listarNecessidades } from '../api/campanhas'
import { ApiError } from '../api/client'
import {
  atualizarDoacao,
  buscarDoacao,
  registrarDoacao,
  type Doacao,
  type DoacaoDados,
  type FormaPagamento,
  type TipoDoacao,
} from '../api/doacoes'
import { invalidarTotais } from '../api/progresso'
import { SeletorDoador } from '../components/SeletorDoador'
import { aplicarErrosDeCampo } from '../util/erros'
import { hojeISO, ROTULO_FORMA, ROTULO_UNIDADE } from '../util/formatos'

type LinhaItem = { chave: number; necessidadeId: string | null; quantidade: number | string }

type Valores = {
  campanhaId: string | null
  tipo: TipoDoacao
  doadorId: number | null
  dataDoacao: string
  observacao: string
  valor: number | string
  formaPagamento: string | null
  itens: LinhaItem[]
}

const TIPOS = [
  { value: 'ITEM', label: 'Itens' },
  { value: 'DINHEIRO', label: 'Dinheiro' },
]

const FORMAS = (Object.keys(ROTULO_FORMA) as FormaPagamento[]).map((f) => ({ value: f, label: ROTULO_FORMA[f] }))

let proximaChave = 0
function linhaVazia(): LinhaItem {
  return { chave: proximaChave++, necessidadeId: null, quantidade: '' }
}

const positivo = (v: number | string): v is number => typeof v === 'number' && v > 0

function validar(v: Valores): FormErrors {
  const erros: FormErrors = {}
  if (!v.campanhaId) erros.campanhaId = 'Escolha a campanha.'
  if (!v.dataDoacao) erros.dataDoacao = 'Informe a data.'
  // Datas ISO (AAAA-MM-DD) se comparam corretamente como texto.
  else if (v.dataDoacao > hojeISO()) erros.dataDoacao = 'A data da doação não pode ser futura.'

  if (v.tipo === 'DINHEIRO') {
    if (!positivo(v.valor)) erros.valor = 'Informe o valor.'
    if (!v.formaPagamento) erros.formaPagamento = 'Escolha a forma de pagamento.'
    return erros
  }

  v.itens.forEach((l, i) => {
    if (l.necessidadeId && !positivo(l.quantidade)) erros[`itens.${i}.quantidade`] = 'Informe a quantidade.'
    if (!l.necessidadeId && l.quantidade !== '') erros[`itens.${i}.necessidadeId`] = 'Escolha o item.'
  })
  const escolhidos = v.itens.filter((l) => l.necessidadeId).map((l) => l.necessidadeId)
  if (escolhidos.length === 0) erros.itens = 'Adicione ao menos um item.'
  else if (new Set(escolhidos).size !== escolhidos.length) erros.itens = 'O mesmo item foi informado mais de uma vez.'
  return erros
}

function paraDados(v: Valores): DoacaoDados {
  const dinheiro = v.tipo === 'DINHEIRO'
  return {
    campanhaId: Number(v.campanhaId),
    doadorId: v.doadorId,
    tipo: v.tipo,
    valor: dinheiro && typeof v.valor === 'number' ? v.valor : null,
    formaPagamento: dinheiro ? (v.formaPagamento as FormaPagamento) : null,
    dataDoacao: v.dataDoacao,
    observacao: v.observacao.trim() || null,
    itens: dinheiro
      ? []
      : v.itens
          .filter((l) => l.necessidadeId)
          .map((l) => ({ necessidadeId: Number(l.necessidadeId), quantidade: Number(l.quantidade) })),
  }
}

function valoresIniciais(doacao: Doacao | null, campanhaDaUrl: string | null): Valores {
  return {
    campanhaId: doacao ? String(doacao.campanhaId) : campanhaDaUrl,
    tipo: doacao?.tipo ?? 'ITEM',
    doadorId: doacao?.doadorId ?? null,
    dataDoacao: doacao?.dataDoacao ?? hojeISO(),
    observacao: doacao?.observacao ?? '',
    valor: doacao?.valor ?? '',
    formaPagamento: doacao?.formaPagamento ?? null,
    itens: doacao?.itens.length
      ? doacao.itens.map((i) => ({ chave: proximaChave++, necessidadeId: String(i.necessidadeId), quantidade: i.quantidade }))
      : [linhaVazia()],
  }
}

/** Registrar (/doacoes/nova, aceita ?campanha=) ou editar (/doacoes/:id/editar) uma doação. */
export function DoacaoFormPage() {
  const params = useParams()
  const id = params.id ? Number(params.id) : null
  const doacao = useQuery({ queryKey: ['doacao', id], queryFn: () => buscarDoacao(id as number), enabled: id != null })

  if (id == null) {
    return <FormularioDoacao doacao={null} />
  }
  if (doacao.isPending) {
    return (
      <Center>
        <Loader />
      </Center>
    )
  }
  if (doacao.isError || !doacao.data.podeAlterar) {
    const naoEncontrada = doacao.error instanceof ApiError && doacao.error.status === 404
    let mensagem = 'Você só pode alterar doações que você registrou.'
    if (doacao.isSuccess && !doacao.data.campanhaAtiva) mensagem = 'Só é possível alterar doações de campanhas ativas.'
    if (doacao.isError) mensagem = naoEncontrada ? 'Doação não encontrada.' : doacao.error.message
    return (
      <Stack>
        <Alert color={naoEncontrada ? 'yellow' : 'red'}>{mensagem}</Alert>
        <Anchor component={Link} to="/doacoes">
          Voltar para doações
        </Anchor>
      </Stack>
    )
  }
  return <FormularioDoacao key={id} doacao={doacao.data} />
}

function FormularioDoacao({ doacao }: { doacao: Doacao | null }) {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState(false)
  // Trava contra toque duplo: o botão só fica "carregando" depois do próximo render.
  const enviando = useRef(false)

  const form = useForm<Valores>({ initialValues: valoresIniciais(doacao, searchParams.get('campanha')), validate: validar })
  const { campanhaId, tipo, itens } = form.values

  const campanhas = useQuery({
    queryKey: ['campanhas', 'ATIVA', 0, 100],
    queryFn: () => listarCampanhas({ status: 'ATIVA', pagina: 0, tamanho: 100 }),
  })
  const necessidades = useQuery({
    queryKey: ['necessidades', Number(campanhaId)],
    queryFn: () => listarNecessidades(Number(campanhaId)),
    enabled: !!campanhaId,
  })

  const opcoesCampanha = (campanhas.data?.itens ?? []).map((c) => ({ value: String(c.id), label: c.titulo }))
  // Na edição, a campanha da doação aparece mesmo que não esteja mais ativa (o servidor recusa com a mensagem certa).
  if (doacao && !opcoesCampanha.some((o) => o.value === String(doacao.campanhaId))) {
    opcoesCampanha.unshift({ value: String(doacao.campanhaId), label: doacao.campanhaTitulo })
  }
  const opcoesItem = (necessidades.data ?? []).map((n) => ({ value: String(n.id), label: n.item }))
  const unidadeDe = (necessidadeId: string | null) => {
    const n = necessidades.data?.find((x) => String(x.id) === necessidadeId)
    return n ? ROTULO_UNIDADE[n.unidade] : ''
  }

  const salvar = useMutation({
    mutationFn: (dados: DoacaoDados) =>
      doacao ? atualizarDoacao(doacao.id, { ...dados, versao: doacao.versao }) : registrarDoacao(dados),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['doacoes'] })
      await queryClient.invalidateQueries({ queryKey: ['doador'] })
      await invalidarTotais(queryClient)
      if (doacao) {
        navigate('/doacoes')
        return
      }
      setSucesso(true)
      // Mantém campanha, tipo e data para agilizar a próxima doação.
      form.setValues({ doadorId: null, observacao: '', valor: '', formaPagamento: null, itens: [linhaVazia()] })
      form.resetDirty()
    },
    onError: (e) => {
      if (!aplicarErrosDeCampo(form, e)) {
        setErroGeral(e.message)
      }
    },
    onSettled: () => {
      enviando.current = false
    },
  })

  function enviar(v: Valores) {
    if (enviando.current) return
    enviando.current = true
    setErroGeral(null)
    setSucesso(false)
    salvar.mutate(paraDados(v))
  }

  function trocarCampanha(valor: string | null) {
    form.setFieldValue('campanhaId', valor)
    // Os itens pertencem à campanha: trocar de campanha recomeça a lista.
    form.setFieldValue('itens', [linhaVazia()])
  }

  return (
    <Stack>
      <Anchor component={Link} to="/doacoes" size="sm">
        ← Doações
      </Anchor>
      <Title order={2}>{doacao ? 'Editar doação' : 'Registrar doação'}</Title>

      {sucesso && (
        <Alert color="green" withCloseButton onClose={() => setSucesso(false)}>
          Doação registrada.
        </Alert>
      )}
      {erroGeral && <Alert color="red">{erroGeral}</Alert>}

      <form noValidate onSubmit={(e) => form.onSubmit(enviar)(e)}>
        <Stack>
          <Select
            label="Campanha"
            placeholder={campanhas.isPending ? 'Carregando…' : 'Escolha a campanha'}
            data={opcoesCampanha}
            allowDeselect={false}
            {...form.getInputProps('campanhaId')}
            onChange={trocarCampanha}
          />
          <Input.Wrapper label="Tipo">
            <SegmentedControl fullWidth data={TIPOS} {...form.getInputProps('tipo')} />
          </Input.Wrapper>
          <SeletorDoador
            value={form.values.doadorId}
            onChange={(v) => form.setFieldValue('doadorId', v)}
            error={form.errors.doadorId}
          />
          <TextInput label="Data" type="date" max={hojeISO()} {...form.getInputProps('dataDoacao')} />

          {tipo === 'ITEM' ? (
            <Stack gap="xs">
              <Text size="sm" fw={500}>
                Itens
              </Text>
              {itens.map((linha, i) => (
                <Group key={linha.chave} align="flex-start" gap="xs" wrap="nowrap">
                  <Select
                    aria-label={`Item ${i + 1}`}
                    placeholder={campanhaId ? 'Escolha o item' : 'Escolha a campanha antes'}
                    style={{ flex: 2 }}
                    data={opcoesItem}
                    disabled={!campanhaId}
                    {...form.getInputProps(`itens.${i}.necessidadeId`)}
                  />
                  <NumberInput
                    aria-label={`Quantidade ${i + 1}`}
                    placeholder="Qtd."
                    style={{ flex: 1 }}
                    decimalSeparator=","
                    decimalScale={2}
                    min={0}
                    hideControls
                    rightSection={<Text size="xs">{unidadeDe(linha.necessidadeId)}</Text>}
                    rightSectionWidth={40}
                    {...form.getInputProps(`itens.${i}.quantidade`)}
                  />
                  {/* Ícone compacto: no celular sobra espaço para a quantidade. */}
                  <ActionIcon
                    variant="subtle"
                    color="red"
                    size="input-sm"
                    aria-label="Remover"
                    disabled={itens.length === 1}
                    onClick={() => form.removeListItem('itens', i)}
                  >
                    ✕
                  </ActionIcon>
                </Group>
              ))}
              {typeof form.errors.itens === 'string' && (
                <Text size="sm" c="red">
                  {form.errors.itens}
                </Text>
              )}
              <Group>
                <Button variant="light" onClick={() => form.insertListItem('itens', linhaVazia())}>
                  + Adicionar item
                </Button>
              </Group>
            </Stack>
          ) : (
            <Group grow align="flex-start">
              <NumberInput
                label="Valor"
                prefix="R$ "
                decimalSeparator=","
                thousandSeparator="."
                decimalScale={2}
                min={0}
                hideControls
                {...form.getInputProps('valor')}
              />
              <Select label="Forma de pagamento" data={FORMAS} {...form.getInputProps('formaPagamento')} />
            </Group>
          )}

          <Textarea label="Observação" autosize minRows={2} {...form.getInputProps('observacao')} />

          <Group justify="flex-end">
            <Button type="submit" loading={salvar.isPending}>
              {doacao ? 'Salvar alterações' : 'Registrar'}
            </Button>
          </Group>
        </Stack>
      </form>
    </Stack>
  )
}
