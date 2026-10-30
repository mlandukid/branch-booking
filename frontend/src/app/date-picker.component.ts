import { Component, ElementRef, Injector, afterNextRender, computed, inject, input, output, signal } from '@angular/core';

import { Holiday } from './booking.models';
import { DayCell, buildMonth, monthLabel, parseIso, shiftDays, shiftMonths, toIso } from './calendar-grid';

/** How many months ahead can be browsed, so seasonal days further out can be discovered. */
const MONTHS_AHEAD = 12;

function monthIndexOf(date: Date): number {
  return date.getFullYear() * 12 + date.getMonth();
}

/**
 * A month calendar that knows which days are closed and why. Closed days stay focusable so their
 * reason can be read out and hovered. Arrow keys move by day and week, Home and End jump to the
 * start and end of the week, and Page Up and Page Down change month.
 */
@Component({
  selector: 'app-date-picker',
  standalone: true,
  template: `
    <div class="picker" role="group" aria-label="Choose a date">
      <div class="head">
        <button type="button" class="nav" aria-label="Previous month" [disabled]="!canPrevious()" (click)="previous()">&lsaquo;</button>
        <span class="title" aria-live="polite">{{ title() }}</span>
        <button type="button" class="nav" aria-label="Next month" [disabled]="!canNext()" (click)="next()">&rsaquo;</button>
      </div>

      <div class="week dow" aria-hidden="true">
        @for (name of weekdays; track name) {
          <span>{{ name }}</span>
        }
      </div>

      <div class="grid" (keydown)="onKeydown($event)">
        @for (week of weeks(); track $index) {
          <div class="week">
            @for (cell of week; track $index) {
              @if (cell) {
                <button
                  type="button"
                  class="day"
                  [attr.data-date]="cell.iso"
                  [attr.tabindex]="cell.iso === tabStop() ? 0 : -1"
                  [attr.aria-label]="cell.label"
                  [attr.aria-disabled]="cell.available ? null : 'true'"
                  [attr.aria-pressed]="cell.iso === selected()"
                  [class.selected]="cell.iso === selected()"
                  [class.today]="cell.today"
                  [class.closed]="!cell.available"
                  [class.holiday]="!!cell.holiday"
                  [title]="cell.note ?? ''"
                  (focus)="focused.set(cell.iso)"
                  (click)="choose(cell)"
                >
                  <span class="num">{{ cell.day }}</span>
                  @if (cell.icon) {
                    <span class="icon" aria-hidden="true">{{ cell.icon }}</span>
                  }
                </button>
              } @else {
                <span class="gap"></span>
              }
            }
          </div>
        }
      </div>

      <p class="legend">Greyed out days are closed. Look for the little symbols, and hover over them.</p>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .picker {
      padding: 10px;
      border: 1.5px solid var(--ink);
      border-radius: 4px;
      background: #fff;
    }
    .head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;
    }
    .title {
      font-family: var(--serif);
      font-size: 1.05rem;
      font-weight: 700;
    }
    .nav {
      width: 34px;
      height: 34px;
      border: 1.5px solid var(--ink);
      border-radius: 4px;
      background: #fff;
      font-size: 1.2rem;
      line-height: 1;
      cursor: pointer;
    }
    .nav:disabled {
      border-color: var(--line);
      color: #b4aea1;
      cursor: not-allowed;
    }
    .week {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      gap: 4px;
      margin-bottom: 4px;
    }
    .dow {
      margin-bottom: 2px;
      color: var(--muted);
      font-size: 0.7rem;
      font-weight: 700;
      letter-spacing: 0.6px;
      text-align: center;
      text-transform: uppercase;
    }
    .day {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 46px;
      padding: 2px 0;
      border: 1.5px solid transparent;
      border-radius: 4px;
      background: #fff;
      color: var(--ink);
      font-family: var(--mono);
      font-size: 0.9rem;
      font-variant-numeric: tabular-nums;
      cursor: pointer;
      transition: background 0.12s ease, border-color 0.12s ease;
    }
    .day:hover:not(.closed) {
      border-color: var(--ink);
      background: var(--sand);
    }
    .day.closed {
      background: transparent;
      color: #a39d90;
      cursor: not-allowed;
    }
    .day.holiday {
      background: rgba(232, 163, 23, 0.16);
    }
    .day.today {
      border-color: var(--ink);
    }
    .day.selected {
      border-color: var(--forest);
      background: var(--forest);
      color: #fff;
    }
    .icon {
      font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Noto Color Emoji', sans-serif;
      font-size: 0.85rem;
      line-height: 1;
    }
    .day:focus-visible,
    .nav:focus-visible {
      outline: 3px solid var(--forest);
      outline-offset: 2px;
    }
    .legend {
      margin: 8px 0 0;
      color: var(--muted);
      font-size: 0.78rem;
    }
  `,
})
export class DatePickerComponent {
  readonly selected = input<string | null>(null);
  /** First bookable day, normally today. ISO format. */
  readonly min = input.required<string>();
  /** Last bookable day. ISO format. */
  readonly max = input.required<string>();
  readonly holidays = input<Record<string, Holiday>>({});
  readonly picked = output<string>();

  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly weekdays = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];
  protected readonly focused = signal<string | null>(null);
  private readonly shown = signal<number | null>(null);

  private readonly firstMonth = computed(() => monthIndexOf(parseIso(this.min())));
  private readonly lastMonth = computed(() => this.firstMonth() + MONTHS_AHEAD);

  private readonly viewMonth = computed(() => {
    const selected = this.selected();
    const index = this.shown() ?? monthIndexOf(parseIso(selected ?? this.min()));
    return Math.min(Math.max(index, this.firstMonth()), this.lastMonth());
  });

  protected readonly title = computed(() => monthLabel(Math.floor(this.viewMonth() / 12), this.viewMonth() % 12));
  protected readonly canPrevious = computed(() => this.viewMonth() > this.firstMonth());
  protected readonly canNext = computed(() => this.viewMonth() < this.lastMonth());

  protected readonly weeks = computed(() =>
    buildMonth(Math.floor(this.viewMonth() / 12), this.viewMonth() % 12, {
      min: this.min(),
      max: this.max(),
      today: this.min(),
      holidays: this.holidays(),
    }),
  );

  /** The one day that is reachable with the Tab key. The arrow keys move from there. */
  protected readonly tabStop = computed(() => {
    const cells = this.weeks().flat().filter((cell): cell is DayCell => cell !== null);
    const focused = this.focused();
    if (focused && cells.some((cell) => cell.iso === focused)) {
      return focused;
    }
    const selected = this.selected();
    if (selected && cells.some((cell) => cell.iso === selected)) {
      return selected;
    }
    return (cells.find((cell) => cell.available) ?? cells[0]).iso;
  });

  protected previous(): void {
    this.shown.set(this.viewMonth() - 1);
  }

  protected next(): void {
    this.shown.set(this.viewMonth() + 1);
  }

  protected choose(cell: DayCell): void {
    if (cell.available) {
      this.picked.emit(cell.iso);
    }
  }

  protected onKeydown(event: KeyboardEvent): void {
    const current = (event.target as HTMLElement).closest<HTMLElement>('[data-date]')?.dataset['date'];
    if (!current) {
      return;
    }
    const date = parseIso(current);
    const weekday = (date.getDay() + 6) % 7;

    let target: Date;
    switch (event.key) {
      case 'ArrowLeft':
        target = shiftDays(date, -1);
        break;
      case 'ArrowRight':
        target = shiftDays(date, 1);
        break;
      case 'ArrowUp':
        target = shiftDays(date, -7);
        break;
      case 'ArrowDown':
        target = shiftDays(date, 7);
        break;
      case 'Home':
        target = shiftDays(date, -weekday);
        break;
      case 'End':
        target = shiftDays(date, 6 - weekday);
        break;
      case 'PageUp':
        target = shiftMonths(date, -1);
        break;
      case 'PageDown':
        target = shiftMonths(date, 1);
        break;
      default:
        return;
    }

    event.preventDefault();
    const index = monthIndexOf(target);
    if (index < this.firstMonth() || index > this.lastMonth()) {
      return;
    }
    this.moveTo(toIso(target), index);
  }

  private moveTo(iso: string, monthIndex: number): void {
    this.shown.set(monthIndex);
    this.focused.set(iso);
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>(`[data-date="${iso}"]`)?.focus(),
      { injector: this.injector },
    );
  }
}