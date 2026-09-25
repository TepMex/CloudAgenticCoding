import type { ReactNode } from 'react'

export function Page({ title, subtitle, actions, children }: { title: string; subtitle?: ReactNode; actions?: ReactNode; children: ReactNode }) {
  return (
    <div className="page">
      <header className="page__head">
        <div>
          <h1 className="title-serif">{title}</h1>
          {subtitle && <p className="page__sub">{subtitle}</p>}
        </div>
        {actions}
      </header>
      {children}
    </div>
  )
}
