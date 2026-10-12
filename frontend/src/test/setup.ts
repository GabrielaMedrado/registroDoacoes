import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
})

// O Mantine usa matchMedia e ResizeObserver, que não existem no jsdom.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})

// O Textarea com autosize escuta document.fonts, que o jsdom também não tem.
Object.defineProperty(document, 'fonts', {
  configurable: true,
  value: { addEventListener: () => {}, removeEventListener: () => {}, ready: Promise.resolve() },
})

class ResizeObserverFalso {
  observe() {}
  unobserve() {}
  disconnect() {}
}
window.ResizeObserver = ResizeObserverFalso

// O Combobox do Mantine rola a opção ativa para a vista; o jsdom não implementa scrollIntoView.
Element.prototype.scrollIntoView = () => {}
