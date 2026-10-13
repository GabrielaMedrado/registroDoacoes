import {
  Alert,
  Button,
  Group,
  Loader,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Tabs,
  Text,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useQuery } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { listarCampanhas, type Campanha } from '../api/campanhas'
import type { TipoDoacao } from '../api/doacoes'
import {
  buscarPendencias,
  buscarRelatorioCampanha,
  buscarRelatorioDoacoes,
  urlCsvCampanha,
  urlCsvDoacoes,
  type FiltroRelatorioDoacoes,
} from '../api/relatorios'
import { TabelaProgresso } from '../components/TabelaProgresso'
import { formatarData, formatarMoeda, formatarQuantidade, hojeISO } from '../util/formatos'

const TIPOS = [
  { value: '', label: 'Todos' },
  { value: 'ITEM', label: 'Itens' },
  { value: 'DINHEIRO', label: 'Dinheiro' },
]

function useCampanhas() {
  return useQuery({
    queryKey: ['campanhas', { pagina: 0, tamanho: 100 }],
    queryFn: () => listarCampanhas({ pagina: 0, tamanho: 100 }),
  })
}

function opcoes(campanhas: Campanha[] | undefined) {
  return (campanhas ?? []).map((c) => ({ value: String(c.id), label: c.titulo }))
}

/** Baixar CSV e Imprimir; somem na impressão. */
function AcoesRelatorio({ csv }: { csv: string }) {
  return (
    <Group className="nao-imprimir">
      <Button component="a" href={csv} download variant="light">
        Baixar CSV
      </Button>
      <Button variant="default" onClick={() => window.print()}>
        Imprimir
      </Button>
    </Group>
  )
}

function PorCampanha() {
  const campanhas = useCampanhas()
  const [campanhaId, setCampanhaId] = useState<number | null>(null)
  const relatorio = useQuery({
    queryKey: ['relatorios', 'campanha', campanhaId],
    queryFn: () => buscarRelatorioCampanha(campanhaId as number),
    enabled: campanhaId != null,
  })
  const r = relatorio.data

  return (
    <Stack>
      <Select
        className="nao-imprimir"
        label="Campanha"
        placeholder="Escolha a campanha"
        data={opcoes(campanhas.data?.itens)}
        value={campanhaId == null ? null : String(campanhaId)}
        onChange={(v) => setCampanhaId(v ? Number(v) : null)}
        searchable
      />
      {relatorio.isLoading && <Loader size="sm" />}
      {relatorio.isError && <Alert color="red">{relatorio.error.message}</Alert>}
      {r && (
        <>
          <Title order={3}>{r.titulo}</Title>
          {r.necessidades.length === 0 ? (
            <Text c="dimmed">Nenhuma necessidade cadastrada.</Text>
          ) : (
            <TabelaProgresso necessidades={r.necessidades} />
          )}
          <Text fw={500}>
            {r.metaValor != null
              ? `Total em dinheiro: ${formatarMoeda(r.totalDinheiro)} de ${formatarMoeda(r.metaValor)}`
              : `Total em dinheiro: ${formatarMoeda(r.totalDinheiro)}`}
          </Text>
          <AcoesRelatorio csv={urlCsvCampanha(r.campanhaId)} />
        </>
      )}
    </Stack>
  )
}

function PorPeriodo() {
  const campanhas = useCampanhas()
  const hoje = hojeISO()
  const [de, setDe] = useState(`${hoje.slice(0, 8)}01`)
  const [ate, setAte] = useState(hoje)
  const [campanhaId, setCampanhaId] = useState('')
  const [tipo, setTipo] = useState('')

  const invertido = de !== '' && ate !== '' && ate < de
  const filtro: FiltroRelatorioDoacoes = { de, ate }
  if (campanhaId) filtro.campanhaId = Number(campanhaId)
  if (tipo) filtro.tipo = tipo as TipoDoacao

  const relatorio = useQuery({
    queryKey: ['relatorios', 'doacoes', filtro],
    queryFn: () => buscarRelatorioDoacoes(filtro),
    enabled: de !== '' && ate !== '' && !invertido,
  })
  const r = invertido ? undefined : relatorio.data

  return (
    <Stack>
      <SimpleGrid className="nao-imprimir" cols={{ base: 1, xs: 2, md: 4 }}>
        <TextInput label="De" type="date" value={de} onChange={(e) => setDe(e.currentTarget.value)} />
        <TextInput
          label="Até"
          type="date"
          value={ate}
          onChange={(e) => setAte(e.currentTarget.value)}
          error={invertido ? 'A data final não pode ser anterior à inicial.' : undefined}
        />
        <Select
          label="Campanha"
          data={[{ value: '', label: 'Todas' }, ...opcoes(campanhas.data?.itens)]}
          value={campanhaId}
          onChange={(v) => setCampanhaId(v ?? '')}
          allowDeselect={false}
        />
        <Select label="Tipo" data={TIPOS} value={tipo} onChange={(v) => setTipo(v ?? '')} allowDeselect={false} />
      </SimpleGrid>

      <Text size="sm" c="dimmed" className="so-impressao">
        {`Período: ${formatarData(de)} a ${formatarData(ate)}`}
      </Text>
      {relatorio.isLoading && !invertido && <Loader size="sm" />}
      {relatorio.isError && !invertido && <Alert color="red">{relatorio.error.message}</Alert>}
      {r && r.doacoes.length === 0 && <Text c="dimmed">Nenhuma doação no período.</Text>}
      {r && r.doacoes.length > 0 && (
        <>
          <Text fw={500}>
            {`${r.quantidade} ${r.quantidade === 1 ? 'doação' : 'doações'} · Total em dinheiro: ${formatarMoeda(r.totalDinheiro)}`}
          </Text>
          {r.totaisItens.length > 0 && (
            <Stack gap={2}>
              {r.totaisItens.map((t) => (
                <Text size="sm" key={`${t.item}|${t.unidade}`}>
                  {`${t.item}: ${formatarQuantidade(t.quantidade, t.unidade)}`}
                </Text>
              ))}
            </Stack>
          )}
          <Table striped>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Data</Table.Th>
                <Table.Th className="oculto-no-celular">Campanha</Table.Th>
                <Table.Th>Doador</Table.Th>
                <Table.Th>Descrição</Table.Th>
                <Table.Th>Valor</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {r.doacoes.map((d) => (
                <Table.Tr key={d.id}>
                  <Table.Td>{formatarData(d.dataDoacao)}</Table.Td>
                  <Table.Td className="oculto-no-celular">{d.campanhaTitulo}</Table.Td>
                  <Table.Td>{d.doadorNome}</Table.Td>
                  <Table.Td>{d.descricao}</Table.Td>
                  <Table.Td>{d.valor == null ? '' : formatarMoeda(d.valor)}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </>
      )}
      {r && <AcoesRelatorio csv={urlCsvDoacoes(filtro)} />}
    </Stack>
  )
}

function Pendencias() {
  const campanhas = useCampanhas()
  const [campanhaId, setCampanhaId] = useState('')
  const texto = useQuery({
    queryKey: ['relatorios', 'pendencias', campanhaId],
    queryFn: () => buscarPendencias(campanhaId ? Number(campanhaId) : undefined),
  })
  const ativas = campanhas.data?.itens.filter((c) => c.status === 'ATIVA')
  const caixaDeTexto = useRef<HTMLTextAreaElement>(null)

  /** Navegadores dentro de outros apps (ex.: WhatsApp) costumam negar a área de transferência moderna. */
  function copiarSelecionando(): boolean {
    const caixa = caixaDeTexto.current
    if (!caixa) return false
    caixa.select()
    try {
      return document.execCommand('copy')
    } catch {
      return false
    }
  }

  async function copiar(conteudo: string) {
    let copiado: boolean
    try {
      await navigator.clipboard.writeText(conteudo)
      copiado = true
    } catch {
      copiado = copiarSelecionando()
    }
    if (copiado) notifications.show({ message: 'Texto copiado.' })
    else notifications.show({ color: 'red', message: 'Não foi possível copiar. Selecione o texto e copie.' })
  }

  return (
    <Stack>
      <Select
        label="Campanha"
        data={[{ value: '', label: 'Todas as campanhas ativas' }, ...opcoes(ativas)]}
        value={campanhaId}
        onChange={(v) => setCampanhaId(v ?? '')}
        allowDeselect={false}
      />
      {texto.isLoading && <Loader size="sm" />}
      {texto.isError && <Alert color="red">{texto.error.message}</Alert>}
      {texto.data != null && (
        <>
          <Textarea
            ref={caixaDeTexto}
            label="Texto para WhatsApp"
            value={texto.data}
            readOnly
            autosize
            minRows={6}
          />
          <Group>
            <Button onClick={() => void copiar(texto.data)}>Copiar texto</Button>
            <Button
              component="a"
              variant="light"
              color="green"
              href={`https://wa.me/?text=${encodeURIComponent(texto.data)}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              Abrir no WhatsApp
            </Button>
          </Group>
        </>
      )}
    </Stack>
  )
}

export function RelatoriosPage() {
  return (
    <Stack>
      <Title order={2}>Relatórios</Title>
      {/* keepMounted={false}: cada aba só busca seus dados quando aberta. */}
      <Tabs defaultValue="campanha" keepMounted={false}>
        <Tabs.List className="nao-imprimir" mb="md">
          <Tabs.Tab value="campanha">Por campanha</Tabs.Tab>
          <Tabs.Tab value="doacoes">Doações por período</Tabs.Tab>
          <Tabs.Tab value="pendencias">Pendências</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="campanha">
          <PorCampanha />
        </Tabs.Panel>
        <Tabs.Panel value="doacoes">
          <PorPeriodo />
        </Tabs.Panel>
        <Tabs.Panel value="pendencias">
          <Pendencias />
        </Tabs.Panel>
      </Tabs>
    </Stack>
  )
}
