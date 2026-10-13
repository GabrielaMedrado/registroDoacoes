import { Alert, Badge, Button, Center, Group, Loader, Pagination, Stack, Table, Title } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { listarUsuarios, type Usuario } from '../api/usuarios'
import { RedefinirSenhaModal } from './RedefinirSenhaModal'
import { ROTULO_PAPEL } from '../util/formatos'
import { UsuarioFormModal } from './UsuarioFormModal'

type Edicao = { aberto: false } | { aberto: true; usuario: Usuario | null }

export function UsuariosPage() {
  const [pagina, setPagina] = useState(0)
  const [edicao, setEdicao] = useState<Edicao>({ aberto: false })
  const [redefinindo, setRedefinindo] = useState<Usuario | null>(null)

  const consulta = useQuery({ queryKey: ['usuarios', pagina], queryFn: () => listarUsuarios(pagina) })

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Usuários</Title>
        <Button onClick={() => setEdicao({ aberto: true, usuario: null })}>Novo usuário</Button>
      </Group>

      {consulta.isPending && (
        <Center>
          <Loader />
        </Center>
      )}
      {consulta.isError && <Alert color="red">{consulta.error.message}</Alert>}

      {consulta.data && (
        <>
          <Table.ScrollContainer minWidth={640}>
            <Table striped>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Nome</Table.Th>
                  <Table.Th>E-mail</Table.Th>
                  <Table.Th>Papel</Table.Th>
                  <Table.Th>Situação</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {consulta.data.itens.map((u) => (
                  <Table.Tr key={u.id}>
                    <Table.Td>{u.nome}</Table.Td>
                    <Table.Td>{u.email}</Table.Td>
                    <Table.Td>{ROTULO_PAPEL[u.papel]}</Table.Td>
                    <Table.Td>
                      <Badge color={u.ativo ? 'green' : 'gray'} variant="light">
                        {u.ativo ? 'Ativo' : 'Inativo'}
                      </Badge>
                    </Table.Td>
                    <Table.Td>
                      <Group gap="xs" justify="flex-end" wrap="nowrap">
                        <Button size="xs" variant="light" onClick={() => setEdicao({ aberto: true, usuario: u })}>
                          Editar
                        </Button>
                        <Button size="xs" variant="subtle" onClick={() => setRedefinindo(u)}>
                          Redefinir senha
                        </Button>
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
          {consulta.data.totalPaginas > 1 && (
            <Pagination
              total={consulta.data.totalPaginas}
              value={pagina + 1}
              onChange={(p) => setPagina(p - 1)}
            />
          )}
        </>
      )}

      {edicao.aberto && (
        <UsuarioFormModal
          key={edicao.usuario?.id ?? 'novo'}
          aberto
          usuario={edicao.usuario}
          aoFechar={() => setEdicao({ aberto: false })}
        />
      )}
      {redefinindo && (
        <RedefinirSenhaModal key={redefinindo.id} usuario={redefinindo} aoFechar={() => setRedefinindo(null)} />
      )}
    </Stack>
  )
}
