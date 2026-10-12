import { Group, Stack, Table, Text } from '@mantine/core'
import type { ReactNode } from 'react'
import type { NecessidadeProgresso } from '../api/progresso'
import { formatarQuantidade } from '../util/formatos'
import { BarraProgresso } from './BarraProgresso'

type Props = { necessidades: NecessidadeProgresso[]; acoes?: (n: NecessidadeProgresso) => ReactNode }

/** Meta × arrecadado × pendente por necessidade. A barra fica sob o item para caber no celular. */
export function TabelaProgresso({ necessidades, acoes }: Props) {
  return (
    <Table striped>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Item</Table.Th>
          <Table.Th visibleFrom="sm">Meta</Table.Th>
          <Table.Th>Arrecadado</Table.Th>
          <Table.Th>Pendente</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {necessidades.map((n) => (
          <Table.Tr key={n.id}>
            <Table.Td miw={140}>
              <Stack gap={4}>
                <Group gap={4} wrap="nowrap">
                  {acoes?.(n)}
                  <Text size="sm">{n.item}</Text>
                </Group>
                {n.observacao && (
                  <Text size="xs" c="dimmed">
                    {n.observacao}
                  </Text>
                )}
                <BarraProgresso percentual={n.percentual} rotulo={`Progresso de ${n.item}`} />
              </Stack>
            </Table.Td>
            <Table.Td visibleFrom="sm">{formatarQuantidade(n.quantidadeMeta, n.unidade)}</Table.Td>
            <Table.Td>{formatarQuantidade(n.arrecadado, n.unidade)}</Table.Td>
            <Table.Td>
              <Text size="sm">{formatarQuantidade(n.pendente, n.unidade)}</Text>
              {n.excedente > 0 && (
                <Text size="xs" c="green">
                  {`+${formatarQuantidade(n.excedente, n.unidade)} acima da meta`}
                </Text>
              )}
            </Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  )
}
