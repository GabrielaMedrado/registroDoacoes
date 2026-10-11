import { Navigate, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthContext'
import { RotaAdmin } from './auth/RotaAdmin'
import { RotaProtegida } from './auth/RotaProtegida'
import { AppShellLayout } from './layout/AppShellLayout'
import { EmConstrucaoPage } from './pages/EmConstrucaoPage'
import { LoginPage } from './pages/LoginPage'
import { CampanhaDetalhePage } from './pages/CampanhaDetalhePage'
import { CampanhasPage } from './pages/CampanhasPage'
import { DoacaoFormPage } from './pages/DoacaoFormPage'
import { DoadorDetalhePage } from './pages/DoadorDetalhePage'
import { DoadoresPage } from './pages/DoadoresPage'
import { MinhaContaPage } from './pages/MinhaContaPage'
import { UsuariosPage } from './pages/UsuariosPage'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RotaProtegida />}>
          <Route element={<AppShellLayout />}>
            <Route index element={<EmConstrucaoPage titulo="Painel" />} />
            <Route path="campanhas" element={<CampanhasPage />} />
            <Route path="campanhas/:id" element={<CampanhaDetalhePage />} />
            <Route path="doacoes" element={<EmConstrucaoPage titulo="Doações" />} />
            <Route path="doacoes/nova" element={<DoacaoFormPage />} />
            <Route path="doacoes/:id/editar" element={<DoacaoFormPage />} />
            <Route path="doadores" element={<DoadoresPage />} />
            <Route path="doadores/:id" element={<DoadorDetalhePage />} />
            <Route path="relatorios" element={<EmConstrucaoPage titulo="Relatórios" />} />
            <Route path="minha-conta" element={<MinhaContaPage />} />
            <Route element={<RotaAdmin />}>
              <Route path="usuarios" element={<UsuariosPage />} />
            </Route>
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
