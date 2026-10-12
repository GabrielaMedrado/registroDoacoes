import { Group, Progress, Text } from '@mantine/core'

/** Barra de 0 a 100% com o número ao lado; `rotulo` é o nome acessível da barra. */
export function BarraProgresso({ percentual, rotulo }: { percentual: number; rotulo: string }) {
  return (
    <Group gap="xs" wrap="nowrap">
      <Progress.Root size="md" style={{ flex: 1 }}>
        <Progress.Section
          value={percentual}
          color={percentual >= 100 ? 'green' : 'blue'}
          aria-label={rotulo}
          aria-valuenow={percentual}
        />
      </Progress.Root>
      <Text size="xs" c="dimmed" w={36} ta="right">
        {`${percentual}%`}
      </Text>
    </Group>
  )
}
