import { screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { renderComProviders } from '../test/render'
import { TabelaProgresso } from './TabelaProgresso'

test('a coluna Meta some só na tela do celular, nunca na impressão', () => {
  renderComProviders(
    <TabelaProgresso
      necessidades={[
        {
          id: 1,
          item: 'Arroz',
          unidade: 'KG',
          quantidadeMeta: 10,
          observacao: null,
          arrecadado: 5,
          pendente: 5,
          excedente: 0,
          percentual: 50,
        },
      ]}
    />,
  )

  // O visibleFrom do Mantine também esconde na impressão (papel A4 tem menos de 768px).
  for (const celula of [screen.getByRole('columnheader', { name: 'Meta' }), screen.getByText('10 kg')]) {
    expect(celula).toHaveClass('oculto-no-celular')
    expect(celula).not.toHaveClass('mantine-visible-from-sm')
  }
})
