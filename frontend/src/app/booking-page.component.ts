import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { BookingApi } from './booking-api.service';
import { Booking, Branch, Slot } from './booking.models';
import { BRANCH_PHOTOS, BranchPhoto } from './branch-photos';
import { Holiday } from './booking.models';
import { buildIcs } from './calendar';
import { DatePickerComponent } from './date-picker.component';
import { GrowTextComponent } from './grow-text.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ReactiveFormsModule, GrowTextComponent, DatePickerComponent],
  templateUrl: './booking-page.component.html',
  styleUrl: './booking-page.component.css',
})
export class BookingPageComponent implements OnInit {
  private readonly api = inject(BookingApi);
  private readonly fb = inject(FormBuilder);

  /** Incremented on every slot request so a slow, older response can never overwrite a newer one. */
  private slotRequest = 0;

  /** Identifies one booking attempt, so a retry after a dropped connection cannot book twice. */
  private attemptKey: string | null = null;

  protected readonly steps = [
    { n: 1, label: 'Where and when' },
    { n: 2, label: 'Pick a time' },
    { n: 3, label: 'Your details' },
  ];
  protected readonly skeleton = Array.from({ length: 12 }, (_, i) => i);

  protected readonly branches = signal<Branch[]>([]);
  protected readonly branchesError = signal<string | null>(null);
  protected readonly loadingBranches = signal(true);

  protected readonly holidays = signal<Record<string, Holiday>>({});
  protected readonly branchId = signal<number | null>(null);
  protected readonly date = signal<string | null>(null);

  protected readonly slots = signal<Slot[]>([]);
  protected readonly loadingSlots = signal(false);
  protected readonly slotsMessage = signal<string | null>(null);
  protected readonly selectedSlot = signal<Slot | null>(null);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly confirmation = signal<Booking | null>(null);
  protected readonly copied = signal(false);

  protected readonly minDate = toIsoDate(new Date());
  protected readonly maxDate = toIsoDate(addDays(new Date(), 30));

  protected readonly selectedBranch = computed(
    () => this.branches().find((branch) => branch.id === this.branchId()) ?? null,
  );
  protected readonly availableCount = computed(() => this.slots().filter((slot) => slot.available).length);
  protected readonly availablePercent = computed(() => {
    const total = this.slots().length;
    return total === 0 ? 0 : Math.round((this.availableCount() / total) * 100);
  });
  protected readonly chosenDate = computed(() => {
    const date = this.date();
    return date ? this.prettyDate(date) : '';
  });

  /** Branches that have a photo, in display order. The photo panel shows one tile per entry. */
  protected readonly tiles = computed(() =>
    this.branches()
      .flatMap((branch) => {
        const photo = BRANCH_PHOTOS[branch.name.trim().toLowerCase()];
        return photo ? [{ branch, photo }] : [];
      })
      .sort((a, b) => a.photo.order - b.photo.order),
  );

  protected readonly serviceStatus = computed<'loading' | 'online' | 'offline'>(() => {
    if (this.loadingBranches()) {
      return 'loading';
    }
    return this.branchesError() ? 'offline' : 'online';
  });

  protected readonly currentStep = computed(() => {
    if (!this.branchId() || !this.date()) {
      return 1;
    }
    return this.selectedSlot() ? 3 : 2;
  });

  /** Branches grouped by city, with cities and branches sorted alphabetically. */
  protected readonly branchGroups = computed(() => {
    const byCity = new Map<string, Branch[]>();
    for (const branch of this.branches()) {
      byCity.set(branch.city, [...(byCity.get(branch.city) ?? []), branch]);
    }
    return [...byCity.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([city, branches]) => ({
        city,
        branches: [...branches].sort((a, b) => a.name.localeCompare(b.name)),
      }));
  });

  protected readonly slotGroups = computed(() => {
    const slots = this.slots();
    return [
      { label: 'Morning', slots: slots.filter((slot) => slot.startTime < '12:00') },
      { label: 'Afternoon', slots: slots.filter((slot) => slot.startTime >= '12:00') },
    ].filter((group) => group.slots.length > 0);
  });

  protected readonly form = this.fb.nonNullable.group({
    branchId: ['', [Validators.required]],
    date: ['', [Validators.required]],
    customerName: ['', [Validators.required, Validators.maxLength(120)]],
    customerEmail: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  ngOnInit(): void {
    this.loadBranches();
    this.loadHolidays();
  }

  /** Loads this year and next, which covers every month the calendar lets the customer browse. */
  private loadHolidays(): void {
    const year = new Date().getFullYear();
    for (const y of [year, year + 1]) {
      this.api.holidays(y).subscribe({
        next: (list) =>
          this.holidays.update((current) => ({
            ...current,
            ...Object.fromEntries(list.map((holiday) => [holiday.date, holiday])),
          })),
        // The calendar still works without the icons. The server refuses closed days anyway.
        error: () => undefined,
      });
    }
  }

  protected loadBranches(): void {
    this.loadingBranches.set(true);
    this.branchesError.set(null);
    this.api.branches().subscribe({
      next: (branches) => {
        this.branches.set(branches);
        this.loadingBranches.set(false);
      },
      error: () => {
        this.loadingBranches.set(false);
        this.branchesError.set('We could not load the branches. Please check your connection and try again.');
      },
    });
  }

  protected onBranchChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.branchId.set(value ? Number(value) : null);
    this.resetSelection();
    this.loadSlots();
  }

  protected pickBranch(id: number): void {
    if (this.confirmation()) {
      return;
    }
    this.form.controls.branchId.setValue(String(id));
    this.branchId.set(id);
    this.resetSelection();
    this.loadSlots();
  }

  protected onDatePicked(iso: string): void {
    this.form.controls.date.setValue(iso);
    this.date.set(iso);
    this.resetSelection();
    this.loadSlots();
  }

  protected selectSlot(slot: Slot): void {
    if (!slot.available) {
      return;
    }
    this.selectedSlot.set(slot);
    this.attemptKey = null;
    this.errorMessage.set(null);
  }

  protected submit(): void {
    const slot = this.selectedSlot();
    if (!slot || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const key = (this.attemptKey ??= newIdempotencyKey());
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.api
      .book({
        branchId: Number(value.branchId),
        date: value.date,
        startTime: slot.startTime,
        customerName: value.customerName.trim(),
        customerEmail: value.customerEmail.trim(),
        idempotencyKey: key,
      })
      .subscribe({
        next: (booking) => {
          this.submitting.set(false);
          this.confirmation.set(booking);
          this.attemptKey = null;
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.errorMessage.set(describeError(err));
          if (err.status !== 0 && err.status < 500) {
            this.attemptKey = null;
          }
          if (err.status === 409) {
            // Someone else got there first. Refresh so the taken slot shows as unavailable.
            this.selectedSlot.set(null);
            this.loadSlots();
          }
        },
      });
  }

  protected newBooking(): void {
    this.confirmation.set(null);
    this.selectedSlot.set(null);
    this.errorMessage.set(null);
    this.form.controls.customerName.reset();
    this.form.controls.customerEmail.reset();
    this.loadSlots();
  }

  protected async copyReference(reference: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(reference);
      this.copied.set(true);
      window.setTimeout(() => this.copied.set(false), 2000);
    } catch {
      // Clipboard access can be unavailable (for example on plain http). Nothing to do.
    }
  }

  protected downloadCalendar(booking: Booking): void {
    const branch = this.selectedBranch();
    const location = branch ? `${branch.name}, ${branch.address}, ${branch.city}` : booking.branchName;
    const blob = new Blob([buildIcs(booking, location)], { type: 'text/calendar;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `appointment-${booking.date}.ics`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  protected time(value: string): string {
    return value.slice(0, 5);
  }

  protected photoFor(branchName: string): BranchPhoto | null {
    return BRANCH_PHOTOS[branchName.trim().toLowerCase()] ?? null;
  }

  protected prettyDate(iso: string): string {
    return parseIsoDate(iso).toLocaleDateString('en-GB', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
  }

  private resetSelection(): void {
    this.selectedSlot.set(null);
    this.errorMessage.set(null);
  }

  private loadSlots(): void {
    const branchId = this.branchId();
    const date = this.date();
    const request = ++this.slotRequest;

    this.slots.set([]);
    this.slotsMessage.set(null);

    if (!branchId || !date) {
      this.loadingSlots.set(false);
      return;
    }
    if (isWeekend(date)) {
      this.loadingSlots.set(false);
      this.slotsMessage.set('Branches are closed at weekends. Please pick a weekday.');
      return;
    }

    this.loadingSlots.set(true);
    this.api.slots(branchId, date).subscribe({
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

function describeError(err: HttpErrorResponse): string {
  if (err.status === 0) {
    return 'We could not reach the booking service. Please try again in a moment.';
  }
  if (err.status === 409) {
    return 'Sorry, that time was just booked by someone else. Please choose another time.';
  }
  const fieldErrors = err.error?.errors as Record<string, string> | undefined;
  if (fieldErrors) {
    return Object.entries(fieldErrors)
      .map(([field, message]) => `${field}: ${message}`)
      .join('. ');
  }
  if (typeof err.error?.detail === 'string') {
    return err.error.detail;
  }
  return 'Something went wrong on our side. Please try again.';
}

function parseIsoDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function isWeekend(iso: string): boolean {
  const day = parseIsoDate(iso).getDay();
  return day === 0 || day === 6;
}

function addDays(date: Date, days: number): Date {
  const copy = new Date(date);
  copy.setDate(copy.getDate() + days);
  return copy;
}

/** Formats using local date parts, so the result never shifts a day because of UTC conversion. */
function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** A random key for one booking attempt. Falls back for pages served over plain http, where randomUUID is missing. */
function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}