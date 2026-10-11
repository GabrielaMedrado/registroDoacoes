import { Button, Group, Select } from '@mantine/core'
import { useDebouncedValue } from '@mantine/hooks'
import { useQuery } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { buscarDoador, listarDoadores, type Doador } from '../api/doadores'
import { DoadorFormModal } from '../pages/DoadorFormModal'

const ANONIMO = 'anonimo'

function rotulo(d: Doador | undefined): string {
  if (!d) return 'Anônimo'
  return d.telefone ? `${d.nome} · ${d.telefone}` : d.nome
}

type Props ={ value: number | null; onChange(id: number | null): void; error?: ReactNode }

/** Escolhe o doador da doação: busca no servidor, "Anônimo" (nulo) ou cadastro rápido. */
export function SeletorDoador({ value, onChange, error }: Props) {
  const [busca, setBusca] = useState('')
  const [buscaAtrasada] = useDebouncedValue(busca, 300)
  const [cadastrando, setCadastrando] = useState(false)
  // Guarda o doador escolhido para o nome continuar aparecendo mesmo fora do resultado da busca.
  const [escolhido, setEscolhido] = useState<Doador | null>(null)

  const precisaCarregar = value != null && escolhido?.id !== value
  const atual = useQuery({
    queryKey: ['doador', value],
    queryFn: () => buscarDoador(value as number),
    enabled: precisaCarregar,
  })
  const selecionado = value != null ? (escolhido?.id === value ? escolhido : atual.data) : undefined

  // Ao abrir a lista, o campo mostra o nome escolhido; isso não é uma busca.
  const termo = buscaAtrasada === rotulo(selecionado) ? '' : buscaAtrasada
  const resultado = useQuery({
    queryKey: ['doadores', termo, 0],
    queryFn: () => listarDoadores(termo, 0),
  })

  const doadores = new Map<number, Doador>()
  if (selecionado) {
    doadores.set(selecionado.id, selecionado)
  }
  // Doadores anonimizados não fazem sentido como escolha: viram "Anônimo".
  resultado.data?.itens.filter((d) => d.nome !== 'Anônimo').forEach((d) => doadores.set(d.id, d))

  const opcoes = [
    { value: ANONIMO, label: 'Anônimo' },
    ...[...doadores.values()].map((d) => ({ value: String(d.id), label: rotulo(d) })),
  ]

  function escolher(doador: Doador | null) {
    setEscolhido(doador)
    onChange(doador?.id ?? null)
  }

  return (
    <>
      <Group align="flex-end" gap="xs" wrap="nowrap">
        <Select
          label="Doador"
          style={{ flex: 1 }}
          searchable
          data={opcoes}
          value={value == null ? ANONIMO : String(value)}
          onChange={(v) => escolher(v == null || v === ANONIMO ? null : (doadores.get(Number(v)) ?? null))}
          onSearchChange={setBusca}
          // A busca é feita no servidor (nome ou telefone); não filtrar de novo aqui.
          filter={({ options }) => options}
          nothingFoundMessage="Nenhum doador encontrado."
          allowDeselect={false}
          error={error}
        />
        <Button variant="light" onClick={() => setCadastrando(true)}>
          Cadastrar novo doador
        </Button>
      </Group>
      {cadastrando && (
        <DoadorFormModal aberto doador={null} aoFechar={() => setCadastrando(false)} aoSalvar={escolher} />
      )}
    </>
  )
}
