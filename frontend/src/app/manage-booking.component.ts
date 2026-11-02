import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';

import { describeError } from './api-error';
import { BookingApi } from './booking-api.service';
import { Booking, Branch, Holiday, Slot } from './booking.models';
import { BRANCH_PHOTOS } from './branch-photos';
import { directionsUrl, googleCalendarUrl } from './calendar';
import { DatePickerComponent } from './date-picker.component';

type View = 'loading' | 'failed' | 'missing' | 'ready' | 'moved' | 'cancelled';
type Mode = 'summary' | 'move' | 'cancel';

/**
 * The screen behind the private link in the confirmation email. The token lives only in memory and in
 * the address fragment, which browsers never send to the server. A wrong link shows the same
 * "not found" page as an unknown booking.
 */
@Component({
  selector: 'app-manage-booking',
  standalone: true,
  imports: [DatePickerComponent],
  template: `
    <section class="card" [attr.aria-busy]="view() === 'loading'">
      @switch (view()) {
        @case ('loading') {
          <p class="muted" role="status">Finding your appointment...</p>
        }
        @case ('failed') {
          <div class="alert" role="alert">{{ error() }}</div>
          <button type="button" class="primary" (click)="retry()">Try again</button>
        }
        @case ('missing') {
          <h2>We could not find that appointment</h2>
          <p class="muted">The link may be incomplete, or the appointment may already have been cancelled.</p>
          <button type="button" class="primary" (click)="leave.emit()">Book a new appointment</button>
        }
        @case ('cancelled') {
          <div role="status">
            <h2>Your appointment has been cancelled</h2>
            <p class="muted">The time is available to other customers again. A confirmation is on its way to your inbox.</p>
            <button type="button" class="primary" (click)="leave.emit()">Book a new appointment</button>
          </div>
        }
        @case ('moved') {
          @if (booking(); as b) {
            <div role="status">
              <h2>Your appointment has been moved</h2>
              <p class="big">{{ time(b.startTime) }}</p>
              <p>{{ prettyDate(b.date) }} at {{ b.branchName }}</p>
              <p class="muted">A confirmation is on its way to your inbox.</p>
              <div class="actions">
                <a class="primary" [href]="calendarLink(b)" target="_blank" rel="noopener">Add to Google Calendar</a>
                <button type="button" class="secondary" (click)="leave.emit()">Done</button>
              </div>
            </div>
          }
        }
        @default {
          @if (booking(); as b) {
            @if (photo(); as p) {
              <figure class="photo">
                <img [src]="p.src" [alt]="p.alt" [style.object-position]="p.focus" decoding="async" />
                <figcaption>{{ b.branchName }}</figcaption>
              </figure>
            }
            <h2>Your appointment</h2>
            <dl class="meta">
              <div>
                <dt>Reference</dt>
                <dd class="reference">{{ b.reference }}</dd>
              </div>
              <div>
                <dt>Branch</dt>
                <dd>
                  {{ b.branchName }}
                  @if (branch(); as br) {
                    <span class="muted block">
                      {{ br.address }}, {{ br.city }} &middot;
                      <a [href]="directionsLink(br)" target="_blank" rel="noopener">Get directions</a>
                    </span>
                  }
                </dd>
              </div>
              <div>
                <dt>When</dt>
                <dd>{{ prettyDate(b.date) }}, {{ time(b.startTime) }} to {{ time(b.endTime) }}</dd>
              </div>
              <div>
                <dt>Booked for</dt>
                <dd>{{ b.customerName }} <span class="muted">({{ b.customerEmail }})</span></dd>
              </div>
            </dl>

            @if (mode() === 'summary') {
              <div class="actions">
                <button type="button" class="primary" (click)="startMove()">Change time</button>
                <button type="button" class="danger" (click)="startCancel()">Cancel appointment</button>
              </div>
              <p class="fine">You can change or cancel up to two hours before your appointment starts.</p>
              <p class="fine">
                <a [href]="calendarLink(b)" target="_blank" rel="noopener">Add to Google Calendar</a>
                &middot;
                <button type="button" class="link" (click)="leave.emit()">Back to the booking page</button>
              </p>
            }

            @if (mode() === 'cancel') {
              <div class="confirm" role="group" aria-label="Confirm the cancellation">
                <p><strong>Cancel this appointment?</strong> The time goes back to other customers straight away.</p>
                <div class="actions">
                  <button type="button" class="danger" [disabled]="busy()" (click)="confirmCancel()">
                    {{ busy() ? 'Cancelling...' : 'Yes, cancel it' }}
                  </button>
                  <button type="button" class="secondary" [disabled]="busy()" (click)="back()">Keep my appointment</button>
                </div>
              </div>
            }

            @if (mode() === 'move') {
              <h3>Choose a new time</h3>
              <app-date-picker
                [selected]="date()"
                [min]="min()"
                [max]="max()"
                [holidays]="holidays()"
                (picked)="onDatePicked($event)"
              />

              @if (!date()) {
                <p class="muted">Pick a new day and the free times will appear here.</p>
              } @else if (loadingSlots()) {
                <div class="slot-grid" aria-busy="true" aria-label="Loading available times">
                  @for (n of skeleton; track n) {
                    <span class="slot skeleton"></span>
                  }
                </div>
              } @else if (slotsMessage()) {
                <p class="notice">{{ slotsMessage() }}</p>
              } @else {
                @for (group of groups(); track group.label) {
                  <h4 class="group">{{ group.label }}</h4>
                  <div class="slot-grid" role="group" [attr.aria-label]="group.label + ' times'">
                    @for (slot of group.slots; track slot.startTime) {
                      <button
                        type="button"
                        class="slot"
                        [class.selected]="chosen()?.startTime === slot.startTime"
                        [disabled]="!slot.available"
                        [attr.aria-pressed]="chosen()?.startTime === slot.startTime"
                        (click)="choose(slot)"
                      >
                        {{ time(slot.startTime) }}
                      </button>
                    }
                  </div>
                }
              }

              @if (chosen(); as c) {
                <p class="chosen">New time: {{ chosenDate() }} at {{ time(c.startTime) }}</p>
              }
              <div class="actions">
                <button type="button" class="primary" [disabled]="!chosen() || busy()" (click)="confirmMove()">
                  {{ busy() ? 'Moving...' : 'Move my appointment' }}
                </button>
                <button type="button" class="secondary" [disabled]="busy()" (click)="back()">Keep my current time</button>
              </div>
            }

            @if (error()) {
              <div class="alert" role="alert">{{ error() }}</div>
            }
          }
        }
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .card {
      margin: 28px 0 16px;
      padding: 22px;
      border: 1.5px solid var(--ink);
      border-radius: 6px;
      background: var(--card);
    }
    h2 {
      margin: 0 0 12px;
      font-family: var(--serif);
      font-size: 1.4rem;
    }
    h3 {
      margin: 22px 0 10px;
      font-family: var(--serif);
      font-size: 1.15rem;
    }
    .photo {
      position: relative;
      height: 150px;
      margin: -22px -22px 18px;
      overflow: hidden;
      border-radius: 4px 4px 0 0;
    }
    .photo img {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .photo::after {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(to top, rgba(10, 16, 24, 0.7), transparent 60%);
    }
    .photo figcaption {
      position: absolute;
      right: 16px;
      bottom: 10px;
      left: 16px;
      z-index: 1;
      color: #fff;
      font-family: var(--serif);
      font-size: 1.2rem;
      font-weight: 700;
    }
    .meta {
      margin: 0 0 16px;
    }
    .meta div {
      display: flex;
      justify-content: space-between;
      gap: 16px;
      padding: 8px 0;
      border-bottom: 1px solid var(--line);
    }
    .meta dt {
      color: var(--muted);
    }
    .meta dd {
      margin: 0;
      font-weight: 600;
      text-align: right;
    }
    .reference {
      font-family: var(--mono);
      font-size: 0.8rem;
      word-break: break-all;
    }
    .big {
      margin: 8px 0 0;
      font-family: var(--serif);
      font-size: 3rem;
      font-weight: 700;
      line-height: 1;
    }
    .muted,
    .fine {
      color: var(--muted);
      font-size: 0.875rem;
    }
    .block {
      display: block;
    }
    a {
      color: var(--forest);
      font-weight: 600;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-top: 16px;
    }
    .primary,
    .secondary,
    .danger {
      padding: 12px 22px;
      border-radius: 4px;
      font-weight: 700;
      text-align: center;
      text-decoration: none;
      cursor: pointer;
    }
    .primary {
      border: 1.5px solid var(--forest);
      background: var(--forest);
      color: #fff;
    }
    .secondary {
      border: 1.5px solid var(--ink);
      background: transparent;
      color: var(--ink);
    }
    .danger {
      border: 1.5px solid var(--brick);
      background: transparent;
      color: var(--brick);
    }
    .danger:hover:not(:disabled) {
      background: var(--brick);
      color: #fff;
    }
    button:disabled {
      opacity: 0.55;
      cursor: not-allowed;
    }
    .link {
      padding: 0;
      border: 0;
      background: none;
      color: var(--forest);
      font-weight: 600;
      text-decoration: underline;
      cursor: pointer;
    }
    .confirm {
      margin-top: 12px;
      padding: 14px;
      border-left: 4px solid var(--brick);
      background: #f9e6e2;
    }
    .confirm p {
      margin: 0;
    }
    .group {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 16px 0 10px;
      color: var(--muted);
      font-size: 0.75rem;
      letter-spacing: 1.2px;
      text-transform: uppercase;
    }
    .group::after {
      content: '';
      flex: 1;
      border-top: 1px solid var(--line);
    }
    .slot-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(86px, 1fr));
      gap: 10px;
    }
    .slot {
      padding: 10px 0;
      border: 1.5px solid var(--ink);
      border-radius: 4px;
      background: #fff;
      color: var(--ink);
      font-family: var(--mono);
      font-size: 0.95rem;
      font-weight: 600;
      cursor: pointer;
    }
    .slot:hover:not(:disabled) {
      background: var(--sand);
    }
    .slot.selected {
      border-color: var(--forest);
      background: var(--forest);
      color: #fff;
    }
    .slot:disabled {
      border: 1.5px dashed var(--line);
      background: repeating-linear-gradient(45deg, transparent 0 5px, rgba(27, 36, 48, 0.09) 5px 6px);
      color: #9a9a9a;
      text-decoration: line-through;
      cursor: not-allowed;
    }
    .slot.skeleton {
      display: block;
      height: 42px;
      border: 0;
      background: linear-gradient(90deg, #ece3cf 25%, #f6efdf 50%, #ece3cf 75%);
      background-size: 200% 100%;
      animation: shimmer 1.2s linear infinite;
    }
    @keyframes shimmer {
      to {
        background-position: -200% 0;
      }
    }
    .chosen {
      margin: 16px 0 0;
      padding: 10px 12px;
      border-left: 4px solid var(--saffron);
      background: var(--sand);
      font-weight: 600;
    }
    .notice {
      margin: 12px 0 0;
      padding: 10px 12px;
      border-left: 4px solid var(--saffron);
      background: var(--sand);
    }
    .alert {
      margin: 16px 0 0;
      padding: 12px 14px;
      border-left: 4px solid var(--brick);
      background: #f9e6e2;
      color: #6e211a;
    }
    :is(a, button):focus-visible {
      outline: 3px solid var(--forest);
      outline-offset: 2px;
    }
  `,
})
export class ManageBookingComponent {
  readonly reference = input.required<string>();
  readonly token = input.required<string>();
  readonly holidays = input<Record<string, Holiday>>({});
  readonly branches = input<Branch[]>([]);
  /** First and last bookable day, the same window the booking form uses. */
  readonly min = input.required<string>();
  readonly max = input.required<string>();
  readonly leave = output<void>();

  private readonly api = inject(BookingApi);

  /** Incremented on every slot request so a slow, older response can never overwrite a newer one. */
  private slotRequest = 0;

  protected readonly view = signal<View>('loading');
  protected readonly mode = signal<Mode>('summary');
  protected readonly booking = signal<Booking | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly date = signal<string | null>(null);
  protected readonly slots = signal<Slot[]>([]);
  protected readonly loadingSlots = signal(false);
  protected readonly slotsMessage = signal<string | null>(null);
  protected readonly chosen = signal<Slot | null>(null);
  protected readonly skeleton = Array.from({ length: 8 }, (_, i) => i);

  protected readonly branch = computed(
    () => this.branches().find((branch) => branch.id === this.booking()?.branchId) ?? null,
  );
  protected readonly photo = computed(() => {
    const booking = this.booking();
    return booking ? (BRANCH_PHOTOS[booking.branchName.trim().toLowerCase()] ?? null) : null;
  });
  protected readonly chosenDate = computed(() => {
    const date = this.date();
    return date ? this.prettyDate(date) : '';
  });
  protected readonly groups = computed(() => {
    const slots = this.slots();
    return [
      { label: 'Morning', slots: slots.filter((slot) => slot.startTime < '12:00') },
      { label: 'Afternoon', slots: slots.filter((slot) => slot.startTime >= '12:00') },
    ].filter((group) => group.slots.length > 0);
  });

  constructor() {
    effect(() => {
      const reference = this.reference();
      const token = this.token();
      untracked(() => this.load(reference, token));
    });
  }

  protected retry(): void {
    this.load(this.reference(), this.token());
  }

  protected startMove(): void {
    this.error.set(null);
    this.date.set(null);
    this.slots.set([]);
    this.chosen.set(null);
    this.slotsMessage.set(null);
    this.mode.set('move');
  }

  protected startCancel(): void {
    this.error.set(null);
    this.mode.set('cancel');
  }

  protected back(): void {
    this.error.set(null);
    this.mode.set('summary');
  }

  protected onDatePicked(iso: string): void {
    this.date.set(iso);
    this.chosen.set(null);
    this.error.set(null);
    this.loadSlots();
  }

  protected choose(slot: Slot): void {
    if (slot.available) {
      this.chosen.set(slot);
      this.error.set(null);
    }
  }

  protected confirmMove(): void {
    const date = this.date();
    const slot = this.chosen();
    if (!date || !slot) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.move(this.reference(), this.token(), date, slot.startTime).subscribe({
      next: (moved) => {
        this.busy.set(false);
        this.booking.set(moved);
        this.view.set('moved');
      },
      error: (err: HttpErrorResponse) => this.fail(err, true),
    });
  }

  protected confirmCancel(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.cancel(this.reference(), this.token()).subscribe({
      next: () => {
        this.busy.set(false);
        this.view.set('cancelled');
      },
      error: (err: HttpErrorResponse) => this.fail(err, false),
    });
  }

  protected calendarLink(booking: Booking): string {
    return googleCalendarUrl(booking, this.locationOf(booking));
  }

  protected directionsLink(branch: Branch): string {
    return directionsUrl(`${branch.name}, ${branch.address}, ${branch.city}`);
  }

  protected time(value: string): string {
    return value.slice(0, 5);
  }

  protected prettyDate(iso: string): string {
    const [year, month, day] = iso.split('-').map(Number);
    return new Date(year, month - 1, day).toLocaleDateString('en-GB', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
  }

  private locationOf(booking: Booking): string {
    const branch = this.branch();
    return branch ? `${branch.name}, ${branch.address}, ${branch.city}` : booking.branchName;
  }

  private load(reference: string, token: string): void {
    this.view.set('loading');
    this.mode.set('summary');
    this.error.set(null);
    this.api.view(reference, token).subscribe({
      next: (booking) => {
        this.booking.set(booking);
        this.view.set('ready');
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 404) {
          this.view.set('missing');
        } else {
          this.error.set(describeError(err));
          this.view.set('failed');
        }
      },
    });
  }

  private fail(err: HttpErrorResponse, refreshSlots: boolean): void {
    this.busy.set(false);
    if (err.status === 404) {
      this.view.set('missing');
      return;
    }
    this.error.set(describeError(err));
    if (refreshSlots && err.status === 409) {
      this.chosen.set(null);
      this.loadSlots();
    }
  }

  private loadSlots(): void {
    const booking = this.booking();
    const date = this.date();
    const request = ++this.slotRequest;

    this.slots.set([]);
    this.slotsMessage.set(null);
    if (!booking || !date) {
      this.loadingSlots.set(false);
      return;
    }

    this.loadingSlots.set(true);
    this.api.slots(booking.branchId, date).subscribe({
      next: (slots) => {
        if (request !== this.slotRequest) {
          return;
        }
        this.slots.set(slots);
        this.loadingSlots.set(false);
        if (!slots.some((slot) => slot.available)) {
          this.slotsMessage.set('No times are available on this date. Please try another day.');
        }
      },
      error: (err: HttpErrorResponse) => {
        if (request !== this.slotRequest) {
          return;
        }
        this.loadingSlots.set(false);
        this.slotsMessage.set(describeError(err));
      },
    });
  }
}