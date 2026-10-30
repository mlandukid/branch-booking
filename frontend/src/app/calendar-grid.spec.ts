import { DayCell, GridContext, buildMonth, parseIso, shiftMonths, toIso } from './calendar-grid';

const ctx: GridContext = {
  min: '2026-10-31',
  max: '2026-11-30',
  today: '2026-10-31',
  holidays: {
    '2026-11-04': { date: '2026-11-04', code: 'LOCAL_GOVERNMENT_ELECTIONS', name: 'Local Government Elections Day' },
    '2026-12-25': { date: '2026-12-25', code: 'CHRISTMAS_DAY', name: 'Christmas Day' },
  },
};

function find(weeks: (DayCell | null)[][], iso: string): DayCell | null {
  return weeks.flat().find((cell) => cell?.iso === iso) ?? null;
}

describe('buildMonth', () => {
  it('builds full weeks of seven cells', () => {
    expect(buildMonth(2026, 10, ctx).every((week) => week.length === 7)).toBe(true);
  });

  it('starts the week on Monday', () => {
    // 1 November 2026 is a Sunday, so six empty cells come first.
    const weeks = buildMonth(2026, 10, ctx);

    expect(weeks[0].slice(0, 6).every((cell) => cell === null)).toBe(true);
    expect(weeks[0][6]?.day).toBe(1);
  });

  it('never offers a weekend', () => {
    const saturday = find(buildMonth(2026, 10, ctx), '2026-11-07');

    expect(saturday?.weekend).toBe(true);
    expect(saturday?.available).toBe(false);
  });

  it('offers a weekday inside the booking window', () => {
    expect(find(buildMonth(2026, 10, ctx), '2026-11-02')?.available).toBe(true);
  });

  it('closes a public holiday and gives it an icon and a name', () => {
    const election = find(buildMonth(2026, 10, ctx), '2026-11-04');

    expect(election?.available).toBe(false);
    expect(election?.icon).toBe('\u{1F5F3}\u{FE0F}');
    expect(election?.note).toBe('Local Government Elections Day');
    expect(election?.label).toContain('closed');
  });

  it('shows a pumpkin on Halloween even though that Saturday is closed', () => {
    const halloween = find(buildMonth(2026, 9, ctx), '2026-10-31');

    expect(halloween?.icon).toBe('\u{1F383}');
    expect(halloween?.note).toBe('Happy Halloween');
    expect(halloween?.available).toBe(false);
  });

  it('shows a Christmas tree beyond the booking window, but does not offer it', () => {
    const christmas = find(buildMonth(2026, 11, ctx), '2026-12-25');

    expect(christmas?.icon).toBe('\u{1F384}');
    expect(christmas?.available).toBe(false);
  });

  it('includes the last bookable day and excludes the next one', () => {
    expect(find(buildMonth(2026, 10, ctx), '2026-11-30')?.available).toBe(true);
    expect(find(buildMonth(2026, 11, ctx), '2026-12-01')?.available).toBe(false);
  });

  it('does not offer days before today', () => {
    expect(find(buildMonth(2026, 9, ctx), '2026-10-30')?.available).toBe(false);
  });
});

describe('date helpers', () => {
  it('round-trips an ISO date without drifting a day', () => {
    expect(toIso(parseIso('2026-03-01'))).toBe('2026-03-01');
  });

  it('clamps the day when moving to a shorter month', () => {
    expect(toIso(shiftMonths(new Date(2026, 0, 31), 1))).toBe('2026-02-28');
  });
});