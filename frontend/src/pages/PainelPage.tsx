import { Alert, Anchor, Button, Card, Group, Loader, SimpleGrid, Stack, Text, Title } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { buscarPainel, type ProgressoCampanha } from '../api/progresso'
import { BarraProgresso } from '../components/BarraProgresso'
import { formatarMoeda, formatarQuantidade } from '../util/formatos'

function CardCampanha({ c }: { c: ProgressoCampanha }) {
  const arrecadado = `${formatarMoeda(c.totalDinheiro)} arrecadados`
  return (
    <Card withBorder radius="md">
      <Stack gap="xs">
        <Anchor component={Link} to={`/campanhas/${c.campanhaId}`} fw={600}>
          {c.titulo}
        </Anchor>
        {c.percentualGeral != null && (
          <BarraProgresso percentual={c.percentualGeral} rotulo={`Progresso dos itens de ${c.titulo}`} />
        )}
        <Text size="sm">{c.metaValor != null ? `${arrecadado} de ${formatarMoeda(c.metaValor)}` : arrecadado}</Text>
        {c.percentualValor != null && (
          <BarraProgresso percentual={c.percentualValor} rotulo={`Arrecadação de ${c.titulo}`} />
        )}
      </Stack>
    </Card>
  )
}

export function PainelPage() {
  const painel = useQuery({ queryKey: ['painel'], queryFn: buscarPainel })

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Painel</Title>
        <Button component={Link} to="/doacoes/nova">
          Registrar doação
        </Button>
      </Group>

      {painel.isPending && <Loader size="sm" />}
      {painel.isError && <Alert color="red">{painel.error.message}</Alert>}

      {painel.data && (
        <>
          <Title order={3}>Campanhas ativas</Title>
          {painel.data.campanhasAtivas.length === 0 && <Text c="dimmed">Nenhuma campanha ativa no momento.</Text>}
          <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
            {painel.data.campanhasAtivas.map((c) => (
              <CardCampanha key={c.campanhaId} c={c} />
            ))}
          </SimpleGrid>

          <Title order={3} mt="md">
            Maiores pendências
          </Title>
          {painel.data.maioresPendencias.length === 0 && <Text c="dimmed">Nenhuma pendência.</Text>}
          <Stack gap="xs">
            {painel.data.maioresPendencias.map((p) => (
              <div key={p.necessidadeId}>
                <Text size="sm">{`${p.item}: faltam ${formatarQuantidade(p.pendente, p.unidade)}`}</Text>
                <Text size="xs" c="dimmed">
                  {p.campanhaTitulo}
                </Text>
              </div>
            ))}
          </Stack>
        </>
      )}
    </Stack>
  )
}
