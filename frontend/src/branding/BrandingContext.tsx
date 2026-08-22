import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { api } from '../api/client'
import type { Branding } from '../api/types'

interface BrandingState {
  branding: Branding
  /** Called after an administrator saves, so the title bar updates without a reload. */
  refresh: () => Promise<void>
}

const EMPTY: Branding = { companyName: null, hasLogo: false, logoVersion: null }

const BrandingContext = createContext<BrandingState | undefined>(undefined)

/**
 * Loads the company name and logo once, for the header, the login page and the tab title.
 *
 * Sits outside AuthProvider because the endpoint is public and the login page is branded too:
 * waiting for a session would leave the sign-in screen unbranded. A failure here is silent -
 * branding is decoration, and the app must still work if the call fails.
 */
export function BrandingProvider({ children }: { children: ReactNode }) {
  const [branding, setBranding] = useState<Branding>(EMPTY)

  const refresh = useCallback(async () => {
    try {
      setBranding(await api.getBranding())
    } catch {
      setBranding(EMPTY)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  // The tab should say who this belongs to, not just what it is.
  useEffect(() => {
    document.title = branding.companyName
      ? `${branding.companyName} · Issue Tracker`
      : 'Issue Tracker'
  }, [branding.companyName])

  return (
    <BrandingContext.Provider value={{ branding, refresh }}>{children}</BrandingContext.Provider>
  )
}

export function useBranding(): BrandingState {
  const context = useContext(BrandingContext)
  if (!context) throw new Error('useBranding must be used inside BrandingProvider')
  return context
}
