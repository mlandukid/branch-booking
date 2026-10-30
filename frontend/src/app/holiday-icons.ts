/** Icons for the public holidays the API reports, keyed by the stable holiday code. */
export const HOLIDAY_ICONS: Record<string, string> = {
  NEW_YEARS_DAY: '\u{1F386}',
  HUMAN_RIGHTS_DAY: '\u{2696}\u{FE0F}',
  GOOD_FRIDAY: '\u{271D}\u{FE0F}',
  FAMILY_DAY: '\u{1F423}',
  FREEDOM_DAY: '\u{1F54A}\u{FE0F}',
  WORKERS_DAY: '\u{1F6E0}\u{FE0F}',
  YOUTH_DAY: '\u{1F393}',
  NATIONAL_WOMENS_DAY: '\u{1F338}',
  HERITAGE_DAY: '\u{1F356}',
  DAY_OF_RECONCILIATION: '\u{1F91D}',
  CHRISTMAS_DAY: '\u{1F384}',
  DAY_OF_GOODWILL: '\u{1F381}',
  LOCAL_GOVERNMENT_ELECTIONS: '\u{1F5F3}\u{FE0F}',
};

export function holidayIcon(code: string): string {
  return HOLIDAY_ICONS[code] ?? '\u{1F4C5}';
}

export interface FunDay {
  icon: string;
  label: string;
}

/** Days that are not holidays but deserve a smile. Keyed by month and day, so they repeat every year. */
export const FUN_DAYS: Record<string, FunDay> = {
  '02-14': { icon: '\u{1F498}', label: "Happy Valentine's Day" },
  '10-31': { icon: '\u{1F383}', label: 'Happy Halloween' },
  '12-24': { icon: '\u{1F31F}', label: 'Christmas Eve' },
  '12-31': { icon: '\u{1F942}', label: "New Year's Eve" },
};