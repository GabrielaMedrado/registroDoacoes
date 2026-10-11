import {
  ActionIcon,
  Alert,
  Anchor,
  Badge,
  Button,
  Center,
  Group,
  Loader,
  Menu,
  Modal,
  Stack,
  Table,
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
  listarNecessidades,
  type Necessidade,
  type StatusCampanha,
} from '../api/campanhas'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { COR_STATUS, formatarData, formatarMoeda, formatarQuantidade, ROTULO_STATUS } from '../util/formatos'
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
  const necessidades = useQuery({ queryKey: ['necessidades', id], queryFn: () => listarNecessidades(id) })

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
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['necessidades', id] }),
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

      <Group justify="space-between" mt="md">
        <Title order={3}>Necessidades</Title>
        {admin && <Button onClick={() => setEdicao({ aberto: true, necessidade: null })}>Adicionar necessidade</Button>}
      </Group>

      {necessidades.isPending && <Loader size="sm" />}
      {necessidades.data?.length === 0 && <Text c="dimmed">Nenhuma necessidade cadastrada.</Text>}
      {necessidades.data && necessidades.data.length > 0 && (
        <Table striped>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Item</Table.Th>
              <Table.Th>Meta</Table.Th>
              <Table.Th visibleFrom="sm">Observação</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {necessidades.data.map((n) => (
              <Table.Tr key={n.id}>
                <Table.Td>
                  {/* Ações ao lado do nome: no celular ficam sempre visíveis, sem arrastar a tabela. */}
                  <Group gap={4} wrap="nowrap">
                    {admin && (
                      <Menu position="bottom-start">
                        <Menu.Target>
                          <ActionIcon variant="subtle" color="gray" aria-label={`Ações de ${n.item}`}>
                            ⋮
                          </ActionIcon>
                        </Menu.Target>
                        <Menu.Dropdown>
                          <Menu.Item onClick={() => setEdicao({ aberto: true, necessidade: n })}>Editar</Menu.Item>
                          <Menu.Item onClick={() => setExcluindo(n)}>
                            Excluir
                          </Menu.Item>
                        </Menu.Dropdown>
                      </Menu>
                    )}
                    <Text size="sm">{n.item}</Text>
                  </Group>
                  {n.observacao && (
                    <Text size="xs" c="dimmed" hiddenFrom="sm">
                      {n.observacao}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>{formatarQuantidade(n.quantidadeMeta, n.unidade)}</Table.Td>
                <Table.Td visibleFrom="sm">{n.observacao}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

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
