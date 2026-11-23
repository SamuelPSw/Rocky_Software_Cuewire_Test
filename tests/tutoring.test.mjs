import assert from 'node:assert/strict'
import test from 'node:test'
import { calendarDays, dayKey, localInput, request, sessionInput, summarize } from '../src/tutoring.ts'

test('calendar includes every day in February of a leap year', () => {
  const days = calendarDays(new Date(2028, 1, 1))
  assert.equal(days[0].getDay(), 0)
  assert.equal(days.at(-1).getDay(), 6)
  assert.equal(days.filter(date => date.getMonth() === 1).length, 29)
  assert.ok(days.some(date => dayKey(date) === '2028-2-29'))
})

test('amounts use cents and exclude cancelled sessions', () => {
  const session = { startsAt: '2027-01-01T15:00:00Z', paid: 15.45, balance: 20.01 }
  assert.deepEqual(summarize([
    session, { ...session, paid: 1.12, balance: 1.11 },
    { ...session, cancelled: true }, { ...session, startsAt: '2023-01-01T00:00:00Z' },
  ], Date.parse('2026-01-01')), { upcoming: 2, paid: 3202, balance: 4113 })
})

test('session forms round trip browser-local times and convert numeric values', () => {
  const date = '2027-01-15T15:30:00.000Z'
  const form = new FormData()
  Object.entries({ subject: ' Algebra ', startsAt: localInput(date), minutes: '45',
    hourlyRate: '50.25', zoomUrl: '' }).forEach(([key, value]) => form.set(key, value))
  assert.deepEqual(sessionInput(form), {
    subject: 'Algebra', startsAt: date, minutes: 45, hourlyRate: 50.25, zoomUrl: '',
  })
})

test('API requests send the selected role and surface server errors', async (context) => {
  const fetch = context.mock.method(globalThis, 'fetch', async () =>
    Response.json({ detail: 'Only the demo tutor can make changes.' }, { status: 403 }))
  await assert.rejects(request('FAMILY', '/sessions/1/cancel', 'POST'), /Only the demo tutor/)
  assert.equal(fetch.mock.calls[0].arguments[0], '/api/sessions/1/cancel')
  assert.equal(fetch.mock.calls[0].arguments[1].headers['X-Demo-Role'], 'FAMILY')
})
