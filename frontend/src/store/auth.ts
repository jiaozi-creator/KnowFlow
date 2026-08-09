import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthResponse, UserView } from '../types'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: UserView | null
  setAuth: (auth: AuthResponse) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setAuth: (auth) => set({
        accessToken: auth.accessToken,
        refreshToken: auth.refreshToken,
        user: auth.user,
      }),
      logout: () => set({ accessToken: null, refreshToken: null, user: null }),
    }),
    { name: 'knowflow-auth' },
  ),
)
