import { Alert, Button, Card, Center, Group, Loader, Pagination, Stack, Text, TextInput, Title } from '@mantine/core'
import { useDebouncedValue } from '@mantine/hooks'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { listarDoadores } from '../api/doadores'
import { DoadorFormModal } from './DoadorFormModal'

export function DoadoresPage() {
  const [busca, setBusca] = useState('')
  const [buscaAtrasada] = useDebouncedValue(busca, 300)
  const [pagina, setPagina] = useState(0)
  const [criando, setCriando] = useState(false)

  const consulta = useQuery({
    queryKey: ['doadores', buscaAtrasada, pagina],
    queryFn: () => listarDoadores(buscaAtrasada, pagina),
  })

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Doadores</Title>
        <Button onClick={() => setCriando(true)}>Novo doador</Button>
      </Group>

      <TextInput
        label="Buscar por nome ou telefone"
        value={busca}
        onChange={(e) => {
          setBusca(e.currentTarget.value)
          setPagina(0)
        }}
      />

      {consulta.isPending && (
        <Center>
          <Loader />
        </Center>
      )}
      {consulta.isError && <Alert color="red">{consulta.error.message}</Alert>}
      {consulta.data?.itens.length === 0 && <Text c="dimmed">Nenhum doador encontrado.</Text>}

      {consulta.data?.itens.map((d) => (
        <Card key={d.id} withBorder radius="md" component={Link} to={`/doadores/${d.id}`}>
          <Text fw={600}>{d.nome}</Text>
          {d.telefone && <Text size="sm">{d.telefone}</Text>}
          {d.email && (
            <Text size="sm" c="dimmed">
              {d.email}
            </Text>
          )}
        </Card>
      ))}

      {consulta.data && consulta.data.totalPaginas > 1 && (
        <Pagination total={consulta.data.totalPaginas} value={pagina + 1} onChange={(p) => setPagina(p - 1)} />
      )}

      {criando && <DoadorFormModal aberto doador={null} aoFechar={() => setCriando(false)} />}
    </Stack>
  )
}
