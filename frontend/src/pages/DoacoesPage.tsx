import {
  ActionIcon,
  Alert,
  Button,
  Card,
  Center,
  Group,
  Loader,
  Menu,
  Modal,
  Pagination,
  Select,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { listarCampanhas } from '../api/campanhas'
import { excluirDoacao, listarDoacoes, type Doacao, type FiltroDoacoes, type TipoDoacao } from '../api/doacoes'
import { formatarData, resumoDoacao } from '../util/formatos'

const TIPOS = [
  { value: '', label: 'Todos' },
  { value: 'ITEM', label: 'Itens' },
  { value: 'DINHEIRO', label: 'Dinheiro' },
]

export function DoacoesPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [filtro, setFiltro] = useState<FiltroDoacoes>({ pagina: 0 })
  const [excluindo, setExcluindo] = useState<Doacao | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  const campanhas = useQuery({
    queryKey: ['campanhas', undefined, 0, 100],
    queryFn: () => listarCampanhas({ pagina: 0, tamanho: 100 }),
  })
  const consulta = useQuery({ queryKey: ['doacoes', filtro], queryFn: () => listarDoacoes(filtro) })

  const excluir = useMutation({
    mutationFn: (d: Doacao) => excluirDoacao(d.id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['doacoes'] }),
    onError: (e) => setErro(e.message),
    onSettled: () => setExcluindo(null),
  })

  /** Muda um filtro e volta para a primeira página; valores vazios saem do filtro. */
  function filtrar(mudanca: Partial<FiltroDoacoes>) {
    const novo: FiltroDoacoes = { ...filtro, ...mudanca, pagina: 0 }
    for (const chave of Object.keys(novo) as (keyof FiltroDoacoes)[]) {
      if (novo[chave] === undefined || novo[chave] === '') delete novo[chave]
    }
    setFiltro(novo)
  }

  const opcoesCampanha = [
    { value: '', label: 'Todas' },
    ...(campanhas.data?.itens ?? []).map((c) => ({ value: String(c.id), label: c.titulo })),
  ]

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Doações</Title>
        <Button component={Link} to="/doacoes/nova">
          Registrar doação
        </Button>
      </Group>

      <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }}>
        <Select
          label="Campanha"
          data={opcoesCampanha}
          value={filtro.campanhaId ? String(filtro.campanhaId) : ''}
          onChange={(v) => filtrar({ campanhaId: v ? Number(v) : undefined })}
          allowDeselect={false}
        />
        <Select
          label="Tipo"
          data={TIPOS}
          value={filtro.tipo ?? ''}
          onChange={(v) => filtrar({ tipo: (v || undefined) as TipoDoacao | undefined })}
          allowDeselect={false}
        />
        <TextInput label="De" type="date" value={filtro.de ?? ''} onChange={(e) => filtrar({ de: e.currentTarget.value })} />
        <TextInput
          label="Até"
          type="date"
          value={filtro.ate ?? ''}
          onChange={(e) => filtrar({ ate: e.currentTarget.value })}
        />
      </SimpleGrid>

      {erro && (
        <Alert color="red" withCloseButton onClose={() => setErro(null)}>
          {erro}
        </Alert>
      )}
      {consulta.isPending && (
        <Center>
          <Loader />
        </Center>
      )}
      {consulta.isError && <Alert color="red">{consulta.error.message}</Alert>}
      {consulta.data?.itens.length === 0 && <Text c="dimmed">Nenhuma doação encontrada.</Text>}

      {consulta.data?.itens.map((d) => (
        <Card key={d.id} withBorder radius="md">
          <Group justify="space-between" wrap="nowrap" align="flex-start">
            <Stack gap={2}>
              <Group gap="xs">
                <Text fw={600}>{d.doadorNome ?? 'Anônimo'}</Text>
                <Text size="sm" c="dimmed">
                  {formatarData(d.dataDoacao)}
                </Text>
              </Group>
              <Text size="sm">{d.campanhaTitulo}</Text>
            </Stack>
            {d.podeAlterar && (
              <Menu position="bottom-end">
                <Menu.Target>
                  <ActionIcon
                    variant="subtle"
                    color="gray"
                    aria-label={`Ações da doação de ${formatarData(d.dataDoacao)}`}
                  >
                    ⋮
                  </ActionIcon>
                </Menu.Target>
                <Menu.Dropdown>
                  <Menu.Item onClick={() => navigate(`/doacoes/${d.id}/editar`)}>Editar</Menu.Item>
                  <Menu.Item onClick={() => setExcluindo(d)}>Excluir</Menu.Item>
                </Menu.Dropdown>
              </Menu>
            )}
          </Group>
          <Text>{resumoDoacao(d)}</Text>
          <Text size="xs" c="dimmed">
            {`Registrado por ${d.registradoPorNome}`}
          </Text>
        </Card>
      ))}

      {consulta.data && consulta.data.totalPaginas > 1 && (
        <Pagination
          total={consulta.data.totalPaginas}
          value={filtro.pagina + 1}
          onChange={(p) => setFiltro({ ...filtro, pagina: p - 1 })}
        />
      )}

      <Modal opened={excluindo !== null} onClose={() => setExcluindo(null)} title="Excluir doação">
        <Stack>
          <Text>Excluir esta doação?</Text>
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
