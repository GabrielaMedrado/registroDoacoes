import { screen } from '@testing-library/react'
import App from './App'
import { renderComProviders } from './test/render'

test('renderiza o título do sistema', () => {
  renderComProviders(<App />)
  expect(screen.getByText('Gestão de Doações')).toBeInTheDocument()
})
