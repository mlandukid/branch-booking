import { Holiday } from './booking.models';
import { FUN_DAYS, holidayIcon } from './holiday-icons';

export interface DayCell {
  iso: string;
  day: number;
  available: boolean;
  today: boolean;
  weekend: boolean;
  holiday: Holiday | null;
  icon: string | null;
  note: string | null;
  /** The full description read out by screen readers. */
  label: string;
}

export interface GridContext {
  /** First bookable day, normally today. ISO format. */
  min: string;
  /** Last bookable day. ISO format. */
  max: string;
  today: string;
  holidays: Record<string, Holiday>;
}

/** Formats using local date parts, so the result never shifts a day because of UTC conversion. */
export function toIso(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export function parseIso(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function monthLabel(year: number, month: number): string {
  return new Date(year, month, 1).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' });
}

export function shiftDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

/** Moves by whole months and keeps the day of the month, clamped to the length of the target month. */
export function shiftMonths(date: Date, months: number): Date {
  const lastDay = new Date(date.getFullYear(), date.getMonth() + months + 1, 0).getDate();
  return new Date(date.getFullYear(), date.getMonth() + months, Math.min(date.getDate(), lastDay));
}

/** Builds one month as rows of seven cells, Monday first. Cells outside the month are null. */
export function buildMonth(year: number, month: number, ctx: GridContext): (DayCell | null)[][] {
  const lead = (new Date(year, month, 1).getDay() + 6) % 7;
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  const cells: (DayCell | null)[] = Array.from({ length: lead }, () => null);
  for (let day = 1; day <= daysInMonth; day++) {
    cells.push(buildCell(new Date(year, month, day), ctx));
  }
  while (cells.length % 7 !== 0) {
    cells.push(null);
  }

  const weeks: (DayCell | null)[][] = [];
  for (let i = 0; i < cells.length; i += 7) {
    weeks.push(cells.slice(i, i + 7));
  }
  return weeks;
}

function buildCell(date: Date, ctx: GridContext): DayCell {
  const iso = toIso(date);
  const weekday = date.getDay();
  const weekend = weekday === 0 || weekday === 6;
  const holiday = ctx.holidays[iso] ?? null;
  const fun = FUN_DAYS[iso.slice(5)] ?? null;
  const inWindow = iso >= ctx.min && iso <= ctx.max;
  const available = inWindow && !weekend && !holiday;

  const icon = holiday ? holidayIcon(holiday.code) : (fun?.icon ?? null);
  const note = holiday ? holiday.name : (fun?.label ?? null);

  let status = 'available';
  if (!available) {
    status = holiday || weekend ? 'closed' : 'not open for booking';
  }
  const dateText = date.toLocaleDateString('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });

  return {
    iso,
    day: date.getDate(),
    available,
    today: iso === ctx.today,
    weekend,
    holiday,
    icon,
    note,
    label: [dateText, status, note].filter(Boolean).join(', '),
  };
}