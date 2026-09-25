import { useEffect } from 'react'
import { asset } from './asset'
import { navigate, useRoute } from './router'
import { guard } from './routes'
import { Login } from './screens/auth/Login'
import { Register } from './screens/auth/Register'
import { HeroPage } from './screens/cabinet/HeroPage'
import { MapPage } from './screens/cabinet/MapPage'
import { QuizPage } from './screens/cabinet/QuizPage'
import { RewardsPage } from './screens/cabinet/RewardsPage'
import { Shell } from './screens/cabinet/Shell'
import { CollectionsPage, EventsPage, FriendsPage } from './screens/cabinet/SocialPages'
import { StatsPage } from './screens/cabinet/StatsPage'
import { TasksPage } from './screens/cabinet/TasksPage'
import { TreasuryPage } from './screens/cabinet/TreasuryPage'
import { ChooseHero } from './screens/onboarding/ChooseHero'
import { HeroCreated } from './screens/onboarding/HeroCreated'
import { Welcome } from './screens/onboarding/Welcome'
import { useStore } from './store'

function CabinetPage({ path }: { path: string }) {
  if (path.startsWith('/app/tasks/')) {
    const id = decodeURIComponent(path.slice('/app/tasks/'.length))
    return <QuizPage key={id} taskId={id} />
  }
  switch (path) {
    case '/app/hero':
      return <HeroPage />
    case '/app/tasks':
      return <TasksPage />
    case '/app/stats':
      return <StatsPage />
    case '/app/rewards':
      return <RewardsPage />
    case '/app/treasury':
      return <TreasuryPage />
    case '/app/collections':
      return <CollectionsPage />
    case '/app/friends':
      return <FriendsPage />
    case '/app/events':
      return <EventsPage />
    default:
      return <MapPage />
  }
}

export default function App() {
  const { account, loading } = useStore()
  const path = useRoute()
  const redirect = loading ? null : guard(path, account)

  useEffect(() => {
    if (redirect) navigate(redirect, { replace: true })
  }, [redirect])

  useEffect(() => {
    window.scrollTo(0, 0)
  }, [path])

  if (loading || redirect) {
    return (
      <div className="splash">
        <img src={asset('img/logo-fox.webp')} alt="" />
      </div>
    )
  }

  switch (path) {
    case '/register':
      return <Register />
    case '/login':
      return <Login />
    case '/welcome':
      return <Welcome />
    case '/hero':
      return <ChooseHero />
    case '/hero-created':
      return <HeroCreated />
    default:
      return (
        <Shell>
          <CabinetPage path={path} />
        </Shell>
      )
  }
}
