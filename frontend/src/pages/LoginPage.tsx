import type { FormEvent } from 'react'
import { ShieldCheck } from 'lucide-react'

export function LoginPage({ message, onLogin }: { message: string; onLogin: (event: FormEvent<HTMLFormElement>) => void }) {
  return (
    <main className="auth-shell">
      <form onSubmit={onLogin} className="auth-panel" noValidate>
        <ShieldCheck size={30} />
        <h1>Archive Sentinel</h1>
        <p>Sign in to manage safe archive optimization.</p>
        <input name="username" defaultValue="admin" placeholder="Username" required />
        <input name="password" defaultValue="admin" type="password" placeholder="Password" required />
        <button type="submit">Sign in</button>
        {message && <p className="form-message">{message}</p>}
      </form>
    </main>
  )
}
