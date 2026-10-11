import { Alert, Anchor, Button, Card, Center, Group, Loader, Modal, Pagination, Stack, Text, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { anonimizarDoador, buscarDoador, listarDoacoesDoDoador } from '../api/doadores'
import { useAuth } from '../auth/AuthContext'
import { formatarData, resumoDoacao } from '../util/formatos'
import { DoadorFormModal } from './DoadorFormModal'

export function DoadorDetalhePage() {
  const id = Number(useParams().id)
  const { usuario } = useAuth()
  const admin = usuario?.papel === 'ADMIN'
  const queryClient = useQueryClient()

  const [pagina, setPagina] = useState(0)
  const [editando, setEditando] = useState(false)
  const [confirmando, setConfirmando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const doador = useQuery({ queryKey: ['doador', id], queryFn: () => buscarDoador(id) })
  const doacoes = useQuery({
    queryKey: ['doador', id, 'doacoes', pagina],
    queryFn: () => listarDoacoesDoDoador(id, pagina),
  })

  const anonimizar = useMutation({
    mutationFn: () => anonimizarDoador(id),
    onSuccess: async (atualizado) => {
      queryClient.setQueryData(['doador', id], atualizado)
      await queryClient.invalidateQueries({ queryKey: ['doadores'] })
      await queryClient.invalidateQueries({ queryKey: ['doacoes'] })
    },
    onError: (e) => setErro(e.message),
    onSettled: () => setConfirmando(false),
  })

  if (doador.isPending) {
    return (
      <Center>
        <Loader />
      </Center>
    )
  }
  if (doador.isError) {
    const naoEncontrado = doador.error instanceof ApiError && doador.error.status === 404
    return (
      <Stack>
        <Alert color={naoEncontrado ? 'yellow' : 'red'}>
          {naoEncontrado ? 'Doador não encontrado.' : doador.error.message}
        </Alert>
        <Anchor component={Link} to="/doadores">
          Voltar para doadores
        </Anchor>
      </Stack>
    )
  }

  const d = doador.data
  return (
    <Stack>
      <Anchor component={Link} to="/doadores" size="sm">
        ← Doadores
      </Anchor>
      <Group justify="space-between" align="flex-start">
        <Stack gap={4}>
          <Title order={2}>{d.nome}</Title>
          {d.telefone && <Text size="sm">{d.telefone}</Text>}
          {d.email && (
            <Text size="sm" c="dimmed">
              {d.email}
            </Text>
          )}
        </Stack>
        {/* R9: a anonimização é definitiva. */}
        {!d.anonimizado && (
          <Group gap="xs">
            <Button variant="light" onClick={() => setEditando(true)}>
              Editar
            </Button>
            {admin && (
              <Button variant="default" color="red" onClick={() => setConfirmando(true)}>
                Anonimizar
              </Button>
            )}
          </Group>
        )}
      </Group>
      {d.observacao && <Text style={{ whiteSpace: 'pre-line' }}>{d.observacao}</Text>}

      {erro && (
        <Alert color="red" withCloseButton onClose={() => setErro(null)}>
          {erro}
        </Alert>
      )}

      <Title order={3} mt="md">
        Doações
      </Title>
      {doacoes.isPending && <Loader size="sm" />}
      {doacoes.isError && <Alert color="red">{doacoes.error.message}</Alert>}
      {doacoes.data?.itens.length === 0 && <Text c="dimmed">Nenhuma doação registrada.</Text>}
      {doacoes.data?.itens.map((doacao) => (
        <Card key={doacao.id} withBorder radius="md">
          <Group justify="space-between" wrap="nowrap" align="flex-start">
            <Text fw={600}>{doacao.campanhaTitulo}</Text>
            <Text size="sm" c="dimmed">
              {formatarData(doacao.dataDoacao)}
            </Text>
          </Group>
          <Text size="sm">{resumoDoacao(doacao)}</Text>
        </Card>
      ))}
      {doacoes.data && doacoes.data.totalPaginas > 1 && (
        <Pagination total={doacoes.data.totalPaginas} value={pagina + 1} onChange={(p) => setPagina(p - 1)} />
      )}

      {editando && <DoadorFormModal aberto doador={d} aoFechar={() => setEditando(false)} />}
      <Modal opened={confirmando} onClose={() => setConfirmando(false)} title="Anonimizar doador">
        <Stack>
          <Text>{`Apagar os dados pessoais de ${d.nome}? As doações continuam registradas como anônimas.`}</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setConfirmando(false)}>
              Cancelar
            </Button>
            <Button color="red" loading={anonimizar.isPending} onClick={() => anonimizar.mutate()}>
              Anonimizar
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  )
}
