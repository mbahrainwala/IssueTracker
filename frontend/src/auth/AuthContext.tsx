import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, getToken, setSessionExpiredHandler, setToken } from '../api/client'
import type { User } from '../api/types'

interface AuthState {
  user: User | null
  loading: boolean
  /** True when the last sign-out was the token being refused, not the user choosing to leave. */
  sessionExpired: boolean
  login: (usernameOrEmail: string, password: string) => Promise<void>
  register: (input: { username: string; email: string; password: string; displayName: string }) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [sessionExpired, setSessionExpired] = useState(false)

  // Any call that sends a token and is refused ends the session here, which drops the app back
  // to the sign-in page - App renders LoginPage whenever there is no user. Registered once, and
  // torn down on unmount so a hot reload does not leave a stale closure holding an old setter.
  useEffect(() => {
    setSessionExpiredHandler(() => {
      setUser(null)
      setSessionExpired(true)
    })
    return () => setSessionExpiredHandler(null)
  }, [])

  // A stored token survives reloads, so verify it against /auth/me before trusting it.
  useEffect(() => {
    if (!getToken()) {
      setLoading(false)
      return
    }
    api
      .me()
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (usernameOrEmail: string, password: string) => {
    const auth = await api.login({ usernameOrEmail, password })
    setToken(auth.token)
    setUser(auth.user)
    setSessionExpired(false)
  }, [])

  const register = useCallback(
    async (input: { username: string; email: string; password: string; displayName: string }) => {
      const auth = await api.register(input)
      setToken(auth.token)
      setUser(auth.user)
      setSessionExpired(false)
    },
    [],
  )

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
    // Leaving on purpose is not an expiry; clear the notice so the next visit is not scolded.
    setSessionExpired(false)
  }, [])

  const value = useMemo(
    () => ({ user, loading, sessionExpired, login, register, logout }),
    [user, loading, sessionExpired, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
