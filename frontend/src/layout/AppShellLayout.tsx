import { AppShell, Burger, Button, Group, NavLink, Stack, Text, Title } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { Link, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/AuthContext'

const ITENS = [
  { rota: '/', rotulo: 'Painel' },
  { rota: '/campanhas', rotulo: 'Campanhas' },
  { rota: '/doacoes', rotulo: 'Doações' },
  { rota: '/doadores', rotulo: 'Doadores' },
  { rota: '/relatorios', rotulo: 'Relatórios' },
]

export function AppShellLayout() {
  const { usuario, sair } = useAuth()
  const [aberto, { toggle, close }] = useDisclosure()
  const { pathname } = useLocation()

  const itens = usuario?.papel === 'ADMIN' ? [...ITENS, { rota: '/usuarios', rotulo: 'Usuários' }] : ITENS
  const ativo = (rota: string) => (rota === '/' ? pathname === '/' : pathname.startsWith(rota))

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !aberto } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group wrap="nowrap">
            <Burger opened={aberto} onClick={toggle} hiddenFrom="sm" size="sm" aria-label="Abrir menu" />
            <Title order={1} size="h4">
              Gestão de Doações
            </Title>
          </Group>
          <Text size="sm" truncate visibleFrom="xs">
            {usuario?.nome}
          </Text>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="sm" component="nav" aria-label="Menu principal">
        <Stack justify="space-between" h="100%">
          <Stack gap={4}>
            {itens.map((item) => (
              <NavLink
                key={item.rota}
                component={Link}
                to={item.rota}
                label={item.rotulo}
                active={ativo(item.rota)}
                onClick={close}
              />
            ))}
          </Stack>
          <Stack gap={4}>
            <NavLink
              component={Link}
              to="/minha-conta"
              label="Minha conta"
              active={ativo('/minha-conta')}
              onClick={close}
            />
            <Button variant="light" color="gray" onClick={() => void sair()}>
              Sair
            </Button>
          </Stack>
        </Stack>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  )
}
