import { Navigate, Outlet } from 'react-router'
import { useAuth } from './AuthContext'

export function RotaAdmin() {
  const { usuario } = useAuth()
  return usuario?.papel === 'ADMIN' ? <Outlet /> : <Navigate to="/" replace />
}
