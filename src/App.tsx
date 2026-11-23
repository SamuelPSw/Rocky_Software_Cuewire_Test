import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { calendarDays, currency, dateTime, dayKey, get, localInput, request, sessionInput, summarize } from './tutoring'
import type { Handout, Role, Session } from './tutoring'
import './App.css'

const tabs = ['Overview', 'Calendar', 'Resources', 'Payments'] as const
type Tab = typeof tabs[number]
const money = (amount: number) => currency.format(amount)
const message = (error: unknown) => error instanceof Error ? error.message : 'Something went wrong.'

function App() {
  const [role, setRole] = useState<Role>('TUTOR')
  const [tab, setTab] = useState<Tab>('Overview')
  const [sessions, setSessions] = useState<Session[]>([])
  const [handouts, setHandouts] = useState<Handout[]>([])
  const [loaded, setLoaded] = useState(false)
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [editor, setEditor] = useState<Session | 'new' | null>(null)
  const [payment, setPayment] = useState<Session | null>(null)
  const [month, setMonth] = useState(() => new Date())
  const [selectedDay, selectDay] = useState('')
  const [pdf, setPdf] = useState<{ url: string; name: string } | null>(null)
  const dialog = useRef<HTMLDialogElement>(null)
  const tutor = role === 'TUTOR'
  const modal = Boolean(editor || payment || pdf)
  const totals = summarize(sessions)
  const reminders = sessions.filter(session => session.remindedAt && session.balance > 0)
  const active = sessions.filter(session => !session.cancelled)

  useEffect(() => {
    let generation = 0
    function refresh() {
      const current = ++generation
      Promise.all([get<Session[]>(role, '/sessions'), get<Handout[]>(role, '/handouts')])
        .then(([nextSessions, nextHandouts]) => {
          if (current !== generation) return
          setSessions(nextSessions)
          setHandouts(nextHandouts)
          setLoaded(true)
        })
        .catch(reason => { if (current === generation) setError(message(reason)) })
    }
    refresh()
    if (role === 'FAMILY') window.addEventListener('focus', refresh)
    return () => {
      generation++
      window.removeEventListener('focus', refresh)
    }
  }, [role, reload])

  useEffect(() => {
    if (modal) dialog.current?.showModal()
    else dialog.current?.close()
  }, [modal])

  useEffect(() => () => { if (pdf) URL.revokeObjectURL(pdf.url) }, [pdf])

  function closeModal() {
    if (busy) return
    setEditor(null)
    setPayment(null)
    setPdf(null)
    setError('')
  }

  async function run(action: () => Promise<void>, success = '') {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await action()
      setNotice(success)
    } catch (reason) {
      setError(message(reason))
    } finally {
      setBusy(false)
    }
  }

  async function changeSession(path: string, method: string, body?: unknown) {
    const updated: Session = await (await request(role, path, method, body)).json()
    setSessions(previous => [...previous.filter(item => item.id !== updated.id), updated]
      .sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt) || a.id - b.id))
  }

  function saveSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    void run(async () => {
      await changeSession(editor === 'new' ? '/sessions' : '/sessions/' + editor?.id,
        editor === 'new' ? 'POST' : 'PUT', sessionInput(form))
      setEditor(null)
    }, 'Session saved.')
  }

  function openEditor(session: Session | 'new') {
    setError('')
    setEditor(session)
  }

  function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    void run(async () => {
      const file = data.get('file') as File
      if (file.size > 5 * 1024 * 1024) throw new Error('PDFs are limited to 5 MB.')
      const result: Handout = await (await request(role, '/handouts', 'POST', data)).json()
      setHandouts(previous => [result, ...previous])
      form.reset()
    }, 'PDF shared with the family.')
  }

  function openPdf(handout: Handout, download: boolean) {
    void run(async () => {
      const response = await request(role, '/handouts/' + handout.id + (download ? '?download=true' : ''))
      const url = URL.createObjectURL(await response.blob())
      if (download) {
        const link = document.createElement('a')
        link.href = url
        link.download = handout.name
        link.click()
        window.setTimeout(() => URL.revokeObjectURL(url), 1000)
      } else {
        setPdf({ url, name: handout.name })
      }
    })
  }

  function moveMonth(offset: number) {
    setMonth(new Date(month.getFullYear(), month.getMonth() + offset, 1))
    selectDay('')
  }

  function sessionCards(items: Session[]) {
    return items.length === 0 ? <p className="empty">No sessions to show.</p> : items.map(session => (
      <article className="session" key={session.id}>
        <div className="date-tile">
          <span>{new Date(session.startsAt).toLocaleString('en-CA', { month: 'short' })}</span>
          <strong>{new Date(session.startsAt).getDate()}</strong>
        </div>
        <div className="session-info">
          <h3>{session.subject}</h3>
          <p>{dateTime.format(new Date(session.startsAt))} · {session.minutes} min</p>
          {session.cancelled ? <span className="badge muted">Cancelled</span> :
            session.zoomUrl && <a href={session.zoomUrl} target="_blank" rel="noreferrer">Join Zoom</a>}
        </div>
        {tutor && !session.cancelled && <div className="actions">
          <button disabled={busy} onClick={() => openEditor(session)}>Edit</button>
          <button disabled={busy} onClick={() => {
            if (window.confirm('Cancel ' + session.subject + '?')) {
              void run(() => changeSession('/sessions/' + session.id + '/cancel', 'POST'), 'Session cancelled.')
            }
          }}>Cancel</button>
        </div>}
      </article>
    ))
  }

  const editing = editor === 'new' ? null : editor

  return (
    <>
      <header className="topbar"><div className="frame top-inner">
        <div className="brand"><span className="brand-mark" aria-hidden="true">t</span> tutor<span>Me</span></div>
        <label className="role">Demo role
          <select value={role} disabled={busy || modal} onChange={event => {
            setRole(event.target.value as Role)
            setTab('Overview')
            setLoaded(false)
            setError('')
            setNotice('')
          }}><option value="TUTOR">Tutor</option><option value="FAMILY">Family</option></select>
        </label>
      </div></header>
      <div className="demo">Fictional data · Demo role switching is insecure for production.</div>
      <main className="frame">
        <section className="hero">
          <div>
            <h1>{tutor ? 'Tutor dashboard' : 'Family dashboard'}</h1>
            <p>{tutor ? 'Manage your sessions, learning materials, and payments.' : 'Find upcoming sessions, shared materials, and payment updates.'}</p>
          </div>
          {tutor && <button className="primary" disabled={busy || !loaded} onClick={() => openEditor('new')}>+ New session</button>}
        </section>
        <nav aria-label="Dashboard sections">{tabs.map(item =>
          <button key={item} className={tab === item ? 'selected' : ''} aria-current={tab === item ? 'page' : undefined}
            onClick={() => { setTab(item); setError(''); setNotice('') }}>{item}</button>)}</nav>
        {!modal && error && <p className="feedback error" role="alert">{error}</p>}
        {!loaded && error && <button onClick={() => { setError(''); setReload(count => count + 1) }}>Retry loading</button>}
        {notice && <p className="feedback" role="status">{notice}</p>}
        {!loaded && !error && <p role="status">Loading dashboard…</p>}
        {loaded && tab === 'Overview' && <>
          <div className="metrics">
            <article><span>Upcoming sessions</span><strong>{totals.upcoming}</strong></article>
            <article><span>Outstanding balance</span><strong>{money(totals.balance / 100)}</strong></article>
            <article><span>Payments recorded</span><strong>{money(totals.paid / 100)}</strong></article>
          </div>
          {!tutor && reminders.length > 0 && <section className="panel reminders">
            <h2>Payment reminders</h2>
            {reminders.map(session => <p key={session.id}>{session.subject} · {money(session.balance)} outstanding · {dateTime.format(new Date(session.remindedAt!))}</p>)}
          </section>}
          <section className="panel">
            <div className="section-head"><h2>Session schedule</h2><button onClick={() => setTab('Calendar')}>Open calendar</button></div>
            {sessionCards(sessions)}
          </section>
        </>}
        {loaded && tab === 'Calendar' && <section className="panel">
          <div className="section-head">
            <h2>{month.toLocaleString('en-CA', { month: 'long', year: 'numeric' })}</h2>
            <div className="actions"><button aria-label="Previous month" onClick={() => moveMonth(-1)}>‹</button>
              <button aria-label="Next month" onClick={() => moveMonth(1)}>›</button></div>
          </div>
          <div className="calendar">
            {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map(day => <span key={day} className="weekday">{day}</span>)}
            {calendarDays(month).map(date => {
              const key = dayKey(date)
              const items = active.filter(session => dayKey(new Date(session.startsAt)) === key)
              return <button key={key} className={date.getMonth() === month.getMonth() ? 'day' : 'day adjacent'}
                aria-pressed={selectedDay === key} aria-label={date.toLocaleDateString() + ', ' + items.length + ' sessions'}
                onClick={() => selectDay(key)}><strong>{date.getDate()}</strong>
                {items.map(item => <span key={item.id} title={item.subject}>{item.subject}</span>)}</button>
            })}
          </div>
          {selectedDay && <div className="day-items"><h3>Selected day</h3>
            {sessionCards(active.filter(session => dayKey(new Date(session.startsAt)) === selectedDay))}</div>}
        </section>}
        {loaded && tab === 'Resources' && <section className="panel">
          <div className="section-head"><h2>Shared PDFs</h2></div>
          {tutor && <form onSubmit={upload} className="upload">
            <label>PDF file (up to 5 MB)<input type="file" name="file" accept=".pdf,application/pdf" required /></label>
            <button className="primary" disabled={busy}>Upload PDF</button>
          </form>}
          {handouts.length === 0 && <p className="empty">No PDFs have been shared yet.</p>}
          {handouts.map(handout => <article className="session" key={handout.id}>
            <span className="badge">PDF</span><div className="session-info"><h3>{handout.name}</h3>
              <p>{Math.max(1, Math.round(handout.size / 1024))} KB</p></div>
            <div className="actions"><button disabled={busy} onClick={() => openPdf(handout, false)}>View</button>
              <button disabled={busy} onClick={() => openPdf(handout, true)}>Download</button></div>
          </article>)}
        </section>}
        {loaded && tab === 'Payments' && <section className="panel">
          <div className="section-head"><h2>Payment records</h2><span className="badge">{money(totals.balance / 100)} outstanding</span></div>
          <p className="subtle">Payments are recorded manually in CAD.</p>
          {active.length === 0 && <p className="empty">No payment records.</p>}
          {active.map(session => <article className="session" key={session.id}>
            <div className="session-info"><h3>{session.subject}</h3>
              <p>{money(session.hourlyRate)}/hr · {money(session.fee)} fee · {money(session.paid)} paid</p>
              <p><strong>{money(session.balance)} outstanding</strong></p>
              {session.remindedAt && <span className="badge">Reminder sent {dateTime.format(new Date(session.remindedAt))}</span>}
            </div>
            {tutor && <div className="actions"><button disabled={busy} onClick={() => { setError(''); setPayment(session) }}>Record payment</button>
              <button disabled={busy || session.balance <= 0} onClick={() =>
                void run(() => changeSession('/sessions/' + session.id + '/reminder', 'POST'), 'Family reminder sent.')
              }>Send reminder</button></div>}
          </article>)}
        </section>}
      </main>
      <dialog ref={dialog} onCancel={event => { event.preventDefault(); closeModal() }}
        className={pdf ? 'pdf-dialog' : ''} aria-labelledby="dialog-title">
        <div className="section-head">
          <h2 id="dialog-title">{pdf?.name || (payment ? 'Record payment' : editor === 'new' ? 'New session' : 'Edit session')}</h2>
          <button type="button" disabled={busy} onClick={closeModal} aria-label="Close dialog">×</button>
        </div>
        {modal && error && <p className="feedback error" role="alert">{error}</p>}
        {editor && <form onSubmit={saveSession} key={editing?.id || 'new'}>
          <label>Subject<input name="subject" required maxLength={100} defaultValue={editing?.subject} /></label>
          <label>Start time<input name="startsAt" type="datetime-local" required defaultValue={editing ? localInput(editing.startsAt) : ''} /></label>
          <div className="form-row">
            <label>Duration (minutes)<input name="minutes" type="number" min={15} max={480} required defaultValue={editing?.minutes ?? 60} /></label>
            <label>Hourly rate (CAD)<input name="hourlyRate" type="number" min={0} max={10000} step="0.01" required defaultValue={editing?.hourlyRate ?? 50} /></label>
          </div>
          <label>Zoom meeting URL (optional)<input name="zoomUrl" type="url" maxLength={500} defaultValue={editing?.zoomUrl || ''} /></label>
          <button className="primary" disabled={busy}>Save session</button>
        </form>}
        {payment && <form onSubmit={event => {
          event.preventDefault()
          const amount = Number(new FormData(event.currentTarget).get('amount'))
          void run(async () => {
            await changeSession('/sessions/' + payment.id + '/payment', 'PUT', { amount })
            setPayment(null)
          }, 'Payment record updated.')
        }}>
          <p>{payment.subject} · {money(payment.fee)} fee</p>
          <label>Total paid to date (CAD)<input name="amount" type="number" min={0} max={payment.fee} step="0.01" required defaultValue={payment.paid} /></label>
          <button className="primary" disabled={busy}>Save payment</button>
        </form>}
        {pdf && <iframe src={pdf.url} title={pdf.name} />}
      </dialog>
    </>
  )
}

export default App
