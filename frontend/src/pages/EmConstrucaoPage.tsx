import { Stack, Text, Title } from '@mantine/core'

export function EmConstrucaoPage({ titulo }: { titulo: string }) {
  return (
    <Stack>
      <Title order={2}>{titulo}</Title>
      <Text c="dimmed">Em construção — disponível em breve.</Text>
    </Stack>
  )
}
