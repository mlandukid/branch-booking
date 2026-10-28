import { Booking } from './booking.models';
import { buildIcs } from './calendar';

const booking: Booking = {
  reference: 'abc-123',
  branchId: 1,
  branchName: 'Sandton',
  date: '2026-11-02',
  startTime: '09:00:00',
  endTime: '09:30:00',
  customerName: 'Thandi Nkosi',
  customerEmail: 'thandi@example.com',
  createdAt: '2026-10-28T08:00:00Z',
};

describe('buildIcs', () => {
  const ics = buildIcs(booking, 'Sandton City, Johannesburg', new Date('2026-10-28T12:00:00Z'));

  it('produces a calendar with a single event', () => {
    expect(ics).toContain('BEGIN:VCALENDAR');
    expect(ics).toContain('BEGIN:VEVENT');
    expect(ics).toContain('END:VCALENDAR');
  });

  it('uses the appointment start and end', () => {
    expect(ics).toContain('DTSTART:20261102T090000');
    expect(ics).toContain('DTEND:20261102T093000');
  });

  it('stamps the file with the creation time in UTC', () => {
    expect(ics).toContain('DTSTAMP:20261028T120000Z');
  });

  it('escapes commas in text fields', () => {
    expect(ics).toContain('LOCATION:Sandton City\\, Johannesburg');
  });

  it('uses CRLF line endings as the format requires', () => {
    expect(ics.includes('\r\n')).toBe(true);
  });
});