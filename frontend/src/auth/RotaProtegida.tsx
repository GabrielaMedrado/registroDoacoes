import { Center, Loader } from '@mantine/core'
import { Navigate, Outlet } from 'react-router'
import { useAuth } from './AuthContext'

export function RotaProtegida() {
  const { usuario, carregando } = useAuth()
  if (carregando) {
    return (
      <Center h="100vh">
        <Loader data-testid="carregando" />
      </Center>
    )
  }
  return usuario ? <Outlet /> : <Navigate to="/login" replace />
}
