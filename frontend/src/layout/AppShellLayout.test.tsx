import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import App from '../App'
import { ADMIN, authMock, prepararAuth, VOLUNTARIO } from '../test/mockAuth'
import { renderComProviders } from '../test/render'

vi.mock('../api/auth', async () => (await import('../test/mockAuth')).authMock)

beforeEach(() => {
  vi.clearAllMocks()
})

const ITENS_COMUNS = ['Painel', 'Campanhas', 'Doações', 'Doadores', 'Relatórios']

test('VOLUNTARIO não vê o item "Usuários" no menu', async () => {
  prepararAuth(VOLUNTARIO)
  renderComProviders(<App />)

  const menu = await screen.findByRole('navigation')
  for (const item of ITENS_COMUNS) {
    expect(menu).toHaveTextContent(item)
  }
  expect(menu).not.toHaveTextContent('Usuários')
})

test('ADMIN vê Painel, Campanhas, Doações, Doadores, Relatórios e Usuários', async () => {
  prepararAuth(ADMIN)
  renderComProviders(<App />)

  const menu = await screen.findByRole('navigation')
  for (const item of [...ITENS_COMUNS, 'Usuários']) {
    expect(menu).toHaveTextContent(item)
  }
  expect(screen.getByText('Ana Admin')).toBeInTheDocument()
})

test('Sair chama sair() e volta ao login', async () => {
  prepararAuth(VOLUNTARIO)
  renderComProviders(<App />)

  await userEvent.click(await screen.findByRole('button', { name: 'Sair' }))

  expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
  expect(authMock.sair).toHaveBeenCalledTimes(1)
})
