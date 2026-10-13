import {
  ActionIcon,
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  Center,
  Group,
  Loader,
  Menu,
  Modal,
  Stack,
  Text,
  Title,
} from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  alterarStatusCampanha,
  buscarCampanha,
  excluirNecessidade,
  type Necessidade,
  type StatusCampanha,
} from '../api/campanhas'
import { ApiError } from '../api/client'
import { listarDoacoes } from '../api/doacoes'
import { buscarProgresso, type NecessidadeProgresso } from '../api/progresso'
import { useAuth } from '../auth/AuthContext'
import { BarraProgresso } from '../components/BarraProgresso'
import { TabelaProgresso } from '../components/TabelaProgresso'
import { COR_STATUS, formatarData, formatarMoeda, resumoDoacao, ROTULO_STATUS } from '../util/formatos'
import { CampanhaFormModal } from './CampanhaFormModal'
import { NecessidadeFormModal } from './NecessidadeFormModal'

const ACOES_STATUS: { status: StatusCampanha; rotulo: string; confirmacao?: string }[] = [
  { status: 'ATIVA', rotulo: 'Ativar' },
  { status: 'ENCERRADA', rotulo: 'Encerrar', confirmacao: 'Encerrar a campanha? Novas doações ficarão bloqueadas.' },
  {
    status: 'RASCUNHO',
    rotulo: 'Voltar para rascunho',
    confirmacao: 'Voltar a campanha para rascunho? Novas doações ficarão bloqueadas.',
  },
]

type AcaoStatus = (typeof ACOES_STATUS)[number]

/** O modal de edição usa o formato de Necessidade; o progresso traz os mesmos campos. */
function paraNecessidade(n: NecessidadeProgresso, campanhaId: number): Necessidade {
  const { id, item, unidade, quantidadeMeta, observacao } = n
  return { id, campanhaId, item, unidade, quantidadeMeta, observacao }
}

type EdicaoNecessidade = { aberto: false } | { aberto: true; necessidade: Necessidade | null }

export function CampanhaDetalhePage() {
  const id = Number(useParams().id)
  const { usuario } = useAuth()
  const admin = usuario?.papel === 'ADMIN'
  const queryClient = useQueryClient()

  const [editandoCampanha, setEditandoCampanha] = useState(false)
  const [edicao, setEdicao] = useState<EdicaoNecessidade>({ aberto: false })
  const [excluindo, setExcluindo] = useState<Necessidade | null>(null)
  const [confirmandoStatus, setConfirmandoStatus] = useState<AcaoStatus | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  const campanha = useQuery({ queryKey: ['campanha', id], queryFn: () => buscarCampanha(id) })
  const progresso = useQuery({ queryKey: ['progresso', id], queryFn: () => buscarProgresso(id) })
  const ultimas = useQuery({
    queryKey: ['doacoes', { campanhaId: id, pagina: 0, tamanho: 5 }],
    queryFn: () => listarDoacoes({ campanhaId: id, pagina: 0, tamanho: 5 }),
  })

  const mudarStatus = useMutation({
    mutationFn: (status: StatusCampanha) => alterarStatusCampanha(id, status),
    onSuccess: (atualizada) => {
      queryClient.setQueryData(['campanha', id], atualizada)
      void queryClient.invalidateQueries({ queryKey: ['campanhas'] })
    },
    onError: (e) => setErro(e.message),
    onSettled: () => setConfirmandoStatus(null),
  })

  function escolherStatus(acao: AcaoStatus) {
    if (acao.confirmacao) setConfirmandoStatus(acao)
    else mudarStatus.mutate(acao.status)
  }

  const excluir = useMutation({
    mutationFn: (n: Necessidade) => excluirNecessidade(n.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['progresso', id] })
      await queryClient.invalidateQueries({ queryKey: ['necessidades', id] })
    },
    onError: (e) => setErro(e.message),
    onSettled: () => setExcluindo(null),
  })

  if (campanha.isPending) {
    return (
      <Center>
        <Loader />
      </Center>
    )
  }
  if (campanha.isError) {
    const naoEncontrada = campanha.error instanceof ApiError && campanha.error.status === 404
    return (
      <Stack>
        <Alert color={naoEncontrada ? 'yellow' : 'red'}>
          {naoEncontrada ? 'Campanha não encontrada.' : campanha.error.message}
        </Alert>
        <Anchor component={Link} to="/campanhas">
          Voltar para campanhas
        </Anchor>
      </Stack>
    )
  }

  const c = campanha.data
  return (
    <Stack>
      <Anchor component={Link} to="/campanhas" size="sm">
        ← Campanhas
      </Anchor>
      <Group justify="space-between" align="flex-start">
        <Stack gap={4}>
          <Group gap="sm">
            <Title order={2}>{c.titulo}</Title>
            <Badge color={COR_STATUS[c.status]} variant="light">
              {ROTULO_STATUS[c.status]}
            </Badge>
          </Group>
          <Text size="sm" c="dimmed">
            {`${formatarData(c.dataInicio)} – ${formatarData(c.dataFim)}`}
          </Text>
          {c.metaValor != null && <Text size="sm">Meta: {formatarMoeda(c.metaValor)}</Text>}
        </Stack>
        {admin && (
          <Group gap="xs">
            <Button variant="light" onClick={() => setEditandoCampanha(true)}>
              Editar campanha
            </Button>
            <Menu>
              <Menu.Target>
                <Button variant="default" loading={mudarStatus.isPending}>
                  Alterar status
                </Button>
              </Menu.Target>
              <Menu.Dropdown>
                {ACOES_STATUS.filter((a) => a.status !== c.status).map((a) => (
                  <Menu.Item key={a.status} onClick={() => escolherStatus(a)}>
                    {a.rotulo}
                  </Menu.Item>
                ))}
              </Menu.Dropdown>
            </Menu>
          </Group>
        )}
      </Group>
      {c.descricao && <Text>{c.descricao}</Text>}
      {c.status === 'ATIVA' && (
        <Group>
          <Button component={Link} to={`/doacoes/nova?campanha=${c.id}`}>
            Registrar doação
          </Button>
        </Group>
      )}

      {erro && (
        <Alert color="red" withCloseButton onClose={() => setErro(null)}>
          {erro}
        </Alert>
      )}

      {progresso.data && (progresso.data.percentualGeral != null || progresso.data.totalDinheiro > 0 || c.metaValor != null) && (
        <Stack gap="xs" mt="md">
          {progresso.data.percentualGeral != null && (
            <div>
              <Text size="sm" fw={500}>
                Progresso dos itens
              </Text>
              <BarraProgresso percentual={progresso.data.percentualGeral} rotulo="Progresso dos itens" />
            </div>
          )}
          <div>
            <Text size="sm" fw={500}>
              {progresso.data.metaValor != null
                ? `Arrecadado em dinheiro: ${formatarMoeda(progresso.data.totalDinheiro)} de ${formatarMoeda(progresso.data.metaValor)}`
                : `Arrecadado em dinheiro: ${formatarMoeda(progresso.data.totalDinheiro)}`}
            </Text>
            {progresso.data.percentualValor != null && (
              <BarraProgresso percentual={progresso.data.percentualValor} rotulo="Arrecadação em dinheiro" />
            )}
          </div>
        </Stack>
      )}

      <Group justify="space-between" mt="md">
        <Title order={3}>Necessidades</Title>
        {admin && <Button onClick={() => setEdicao({ aberto: true, necessidade: null })}>Adicionar necessidade</Button>}
      </Group>

      {progresso.isPending && <Loader size="sm" />}
      {progresso.isError && <Alert color="red">{progresso.error.message}</Alert>}
      {progresso.data?.necessidades.length === 0 && <Text c="dimmed">Nenhuma necessidade cadastrada.</Text>}
      {progresso.data && progresso.data.necessidades.length > 0 && (
        <TabelaProgresso
          necessidades={progresso.data.necessidades}
          // Ações ao lado do nome: no celular ficam sempre visíveis, sem arrastar a tabela.
          acoes={
            admin
              ? (n) => (
                  <Menu position="bottom-start">
                    <Menu.Target>
                      <ActionIcon variant="subtle" color="gray" aria-label={`Ações de ${n.item}`}>
                        ⋮
                      </ActionIcon>
                    </Menu.Target>
                    <Menu.Dropdown>
                      <Menu.Item onClick={() => setEdicao({ aberto: true, necessidade: paraNecessidade(n, id) })}>
                        Editar
                      </Menu.Item>
                      <Menu.Item onClick={() => setExcluindo(paraNecessidade(n, id))}>Excluir</Menu.Item>
                    </Menu.Dropdown>
                  </Menu>
                )
              : undefined
          }
        />
      )}

      <Stack gap="xs" mt="md">
        <Title order={3}>Últimas doações</Title>
        {ultimas.isPending && <Loader size="sm" />}
        {ultimas.isError && <Alert color="red">{ultimas.error.message}</Alert>}
        {ultimas.data?.itens.length === 0 && <Text c="dimmed">Nenhuma doação registrada.</Text>}
        {ultimas.data?.itens.map((d) => (
          <Card key={d.id} withBorder radius="md" padding="sm">
            <Group justify="space-between" wrap="nowrap" align="flex-start">
              <Text size="sm" fw={500}>
                {d.doadorNome ?? 'Anônimo'}
              </Text>
              <Text size="xs" c="dimmed">
                {formatarData(d.dataDoacao)}
              </Text>
            </Group>
            <Text size="sm">{resumoDoacao(d)}</Text>
          </Card>
        ))}
      </Stack>

      {editandoCampanha && <CampanhaFormModal aberto campanha={c} aoFechar={() => setEditandoCampanha(false)} />}
      {edicao.aberto && (
        <NecessidadeFormModal
          key={edicao.necessidade?.id ?? 'nova'}
          aberto
          campanhaId={id}
          necessidade={edicao.necessidade}
          aoFechar={() => setEdicao({ aberto: false })}
        />
      )}
      <Modal opened={confirmandoStatus !== null} onClose={() => setConfirmandoStatus(null)} title="Alterar status">
        <Stack>
          <Text>{confirmandoStatus?.confirmacao}</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setConfirmandoStatus(null)}>
              Cancelar
            </Button>
            <Button
              loading={mudarStatus.isPending}
              onClick={() => confirmandoStatus && mudarStatus.mutate(confirmandoStatus.status)}
            >
              Confirmar
            </Button>
          </Group>
        </Stack>
      </Modal>
      <Modal opened={excluindo !== null} onClose={() => setExcluindo(null)} title="Excluir necessidade">
        <Stack>
          <Text>{`Excluir "${excluindo?.item}"?`}</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setExcluindo(null)}>
              Cancelar
            </Button>
            <Button color="red" loading={excluir.isPending} onClick={() => excluindo && excluir.mutate(excluindo)}>
              Excluir
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  )
}
