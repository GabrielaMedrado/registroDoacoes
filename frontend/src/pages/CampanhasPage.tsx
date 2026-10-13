import { Alert, Badge, Button, Card, Center, Group, Loader, Pagination, SegmentedControl, Stack, Text, Title } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { listarCampanhas, type StatusCampanha } from '../api/campanhas'
import { useAuth } from '../auth/AuthContext'
import { COR_STATUS, formatarData, formatarMoeda, ROTULO_STATUS } from '../util/formatos'
import { CampanhaFormModal } from './CampanhaFormModal'

type Filtro = StatusCampanha | 'TODAS'

const FILTROS: { value: Filtro; label: string }[] = [
  { value: 'ATIVA', label: 'Ativas' },
  { value: 'RASCUNHO', label: 'Rascunho' },
  { value: 'ENCERRADA', label: 'Encerradas' },
  { value: 'TODAS', label: 'Todas' },
]

export function CampanhasPage() {
  const { usuario } = useAuth()
  const [filtro, setFiltro] = useState<Filtro>('ATIVA')
  const [pagina, setPagina] = useState(0)
  const [criando, setCriando] = useState(false)

  const status = filtro === 'TODAS' ? undefined : filtro
  const consulta = useQuery({
    queryKey: ['campanhas', status, pagina],
    queryFn: () => listarCampanhas({ status, pagina }),
  })

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Campanhas</Title>
        {usuario?.papel === 'ADMIN' && <Button onClick={() => setCriando(true)}>Nova campanha</Button>}
      </Group>

      <SegmentedControl
        data={FILTROS}
        value={filtro}
        onChange={(v) => {
          setFiltro(v as Filtro)
          setPagina(0)
        }}
      />

      {consulta.isPending && (
        <Center>
          <Loader />
        </Center>
      )}
      {consulta.isError && <Alert color="red">{consulta.error.message}</Alert>}
      {consulta.data?.itens.length === 0 && <Text c="dimmed">Nenhuma campanha encontrada.</Text>}

      {consulta.data?.itens.map((c) => (
        <Card key={c.id} withBorder radius="md" component={Link} to={`/campanhas/${c.id}`}>
          <Group justify="space-between" wrap="nowrap" align="flex-start">
            <Text fw={600}>{c.titulo}</Text>
            <Badge color={COR_STATUS[c.status]} variant="light">
              {ROTULO_STATUS[c.status]}
            </Badge>
          </Group>
          <Text size="sm" c="dimmed">
            {`${formatarData(c.dataInicio)} – ${formatarData(c.dataFim)}`}
          </Text>
          {c.metaValor != null && <Text size="sm">Meta: {formatarMoeda(c.metaValor)}</Text>}
        </Card>
      ))}

      {consulta.data && consulta.data.totalPaginas > 1 && (
        <Pagination total={consulta.data.totalPaginas} value={pagina + 1} onChange={(p) => setPagina(p - 1)} />
      )}

      {criando && <CampanhaFormModal aberto campanha={null} aoFechar={() => setCriando(false)} />}
    </Stack>
  )
}
