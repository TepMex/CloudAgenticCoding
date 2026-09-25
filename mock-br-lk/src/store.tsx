import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { loadSession, saveAccount } from './db'
import type { Account } from './domain/account'

interface Store {
  account: Account | null
  loading: boolean
  /** Replaces the in-memory account and persists it. */
  setAccount: (next: Account | null) => void
  update: (fn: (a: Account) => Account) => void
}

const Ctx = createContext<Store | null>(null)

export function AccountProvider({ children }: { children: ReactNode }) {
  const [account, setState] = useState<Account | null>(null)
  const [loading, setLoading] = useState(true)
  const ref = useRef<Account | null>(null)

  useEffect(() => {
    loadSession()
      .then((a) => {
        ref.current = a
        setState(a)
      })
      .finally(() => setLoading(false))
  }, [])

  const setAccount = useCallback((next: Account | null) => {
    ref.current = next
    setState(next)
    if (next) void saveAccount(next)
  }, [])

  const update = useCallback(
    (fn: (a: Account) => Account) => {
      if (ref.current) setAccount(fn(ref.current))
    },
    [setAccount],
  )

  const value = useMemo(() => ({ account, loading, setAccount, update }), [account, loading, setAccount, update])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useStore(): Store {
  const s = useContext(Ctx)
  if (!s) throw new Error('AccountProvider missing')
  return s
}

/** For screens rendered only after the session guard. */
export function useAccount(): [Account, (fn: (a: Account) => Account) => void] {
  const { account, update } = useStore()
  if (!account) throw new Error('No account in session')
  return [account, update]
}
