import { screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import App from './App'
import { prepararAuth } from './test/mockAuth'
import { renderComProviders } from './test/render'

vi.mock('./api/auth', async () => (await import('./test/mockAuth')).authMock)

test('renderiza o título do sistema', async () => {
  prepararAuth(null)
  renderComProviders(<App />)
  expect(await screen.findByText('Gestão de Doações')).toBeInTheDocument()
})
