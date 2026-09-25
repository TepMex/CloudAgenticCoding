import { useToast } from '../../components/Toast'
import { SOCIAL_LABEL, type SocialProvider } from '../../components/common'
import { saveAccount, signIn } from '../../db'
import { navigate } from '../../router'
import { useStore } from '../../store'
import { routeForAccount } from '../../routes'

/** Each provider maps to one demo account so the mock can show OAuth without a backend. */
export function useSocialSignIn() {
  const { setAccount } = useStore()
  const toast = useToast()
  return async (provider: SocialProvider) => {
    const { account, created } = await signIn(`${provider}-demo@bry.school`)
    let next = account
    if (created) {
      next = { ...account, studentName: 'Юный путешественник' }
      await saveAccount(next)
    }
    setAccount(next)
    toast(`Вход через ${SOCIAL_LABEL[provider]} (демо)`, 'info')
    navigate(routeForAccount(next))
  }
}
