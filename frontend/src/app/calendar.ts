import { Booking } from './booking.models';

/** Builds an iCalendar (.ics) file for a booking, so it can be added to any calendar app. */
export function buildIcs(booking: Booking, location: string, now: Date = new Date()): string {
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Branch Booking//EN',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    'BEGIN:VEVENT',
    `UID:${booking.reference}@branch-booking`,
    `DTSTAMP:${utcStamp(now)}`,
    `DTSTART:${localStamp(booking.date, booking.startTime)}`,
    `DTEND:${localStamp(booking.date, booking.endTime)}`,
    `SUMMARY:${escapeText(`Branch appointment: ${booking.branchName}`)}`,
    `LOCATION:${escapeText(location)}`,
    `DESCRIPTION:${escapeText(`Reference: ${booking.reference}. Please arrive a few minutes early.`)}`,
    'BEGIN:VALARM',
    'TRIGGER:-PT1H',
    'ACTION:DISPLAY',
    'DESCRIPTION:Appointment reminder',
    'END:VALARM',
    'END:VEVENT',
    'END:VCALENDAR',
  ];
  return lines.join('\r\n') + '\r\n';
}

/** Date and time without a zone ("floating"), so it shows at the same wall-clock time for the customer. */
function localStamp(date: string, time: string): string {
  const [hours, minutes, seconds = '00'] = time.split(':');
  return `${date.replaceAll('-', '')}T${hours}${minutes}${seconds}`;
}

function utcStamp(now: Date): string {
  return now.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
}

function escapeText(value: string): string {
  return value
    .replaceAll('\\', '\\\\')
    .replaceAll(';', '\\;')
    .replaceAll(',', '\\,')
    .replaceAll('\n', '\\n');
}