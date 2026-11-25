export type Role = 'TUTOR' | 'FAMILY'

export type Session = {
  id: number
  subject: string
  startsAt: string
  minutes: number
  hourlyRate: number
  zoomUrl: string | null
  cancelled: boolean
  fee: number
  paid: number
  balance: number
  remindedAt: string | null
  hasSharedNotes: boolean
}

export type Handout = { id: number; name: string; size: number }
export type LessonNotes = { text: string | null; shared: boolean; pdf: Handout | null }

export function sessionCompleted(session: Session, now = Date.now()) {
  return !session.cancelled && Date.parse(session.startsAt) + session.minutes * 60000 <= now
}

export const currency = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' })
export const dateTime = new Intl.DateTimeFormat('en-CA', { dateStyle: 'medium', timeStyle: 'short' })

export function calendarDays(month: Date) {
  const first = new Date(month.getFullYear(), month.getMonth(), 1)
  const count = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
  return Array.from({ length: Math.ceil((first.getDay() + count) / 7) * 7 }, (_, index) =>
    new Date(first.getFullYear(), first.getMonth(), index - first.getDay() + 1))
}

export function dayKey(date: Date) {
  return [date.getFullYear(), date.getMonth() + 1, date.getDate()].join('-')
}

export function localInput(iso: string) {
  const date = new Date(iso)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

export function summarize(sessions: Session[], now = Date.now()) {
  return sessions.reduce((totals, session) => {
    if (session.cancelled) return totals
    if (Date.parse(session.startsAt) >= now) totals.upcoming++
    totals.paid += Math.round(session.paid * 100)
    totals.balance += Math.round(session.balance * 100)
    return totals
  }, { upcoming: 0, paid: 0, balance: 0 })
}

export function sessionInput(form: FormData) {
  return {
    subject: String(form.get('subject')).trim(),
    startsAt: new Date(String(form.get('startsAt'))).toISOString(),
    minutes: Number(form.get('minutes')),
    hourlyRate: Number(form.get('hourlyRate')),
    zoomUrl: String(form.get('zoomUrl')).trim(),
  }
}

export async function request(role: Role, path: string, method = 'GET', body?: unknown) {
  const headers: Record<string, string> = { 'X-Demo-Role': role }
  if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const response = await fetch('/api' + path, {
    method, headers, body: body instanceof FormData ? body : JSON.stringify(body),
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(problem?.detail || `Request failed (${response.status}).`)
  }
  return response
}

export async function get<T>(role: Role, path: string): Promise<T> {
  return (await request(role, path)).json()
}
