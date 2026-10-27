import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { BookingApi } from './booking-api.service';
import { Booking, Branch, Slot } from './booking.models';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './booking-page.component.html',
  styleUrl: './booking-page.component.css',
})
export class BookingPageComponent implements OnInit {
  private readonly api = inject(BookingApi);
  private readonly fb = inject(FormBuilder);

  /** Incremented on every slot request so a slow, older response can never overwrite a newer one. */
  private slotRequest = 0;

  protected readonly branches = signal<Branch[]>([]);
  protected readonly branchesError = signal<string | null>(null);
  protected readonly loadingBranches = signal(true);

  protected readonly branchId = signal<number | null>(null);
  protected readonly date = signal<string | null>(null);

  protected readonly slots = signal<Slot[]>([]);
  protected readonly loadingSlots = signal(false);
  protected readonly slotsMessage = signal<string | null>(null);
  protected readonly selectedSlot = signal<Slot | null>(null);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly confirmation = signal<Booking | null>(null);

  protected readonly minDate = toIsoDate(new Date());
  protected readonly maxDate = toIsoDate(addDays(new Date(), 30));

  protected readonly selectedBranch = computed(
    () => this.branches().find((branch) => branch.id === this.branchId()) ?? null,
  );
  protected readonly availableCount = computed(() => this.slots().filter((slot) => slot.available).length);

  protected readonly form = this.fb.nonNullable.group({
    branchId: ['', [Validators.required]],
    date: ['', [Validators.required]],
    customerName: ['', [Validators.required, Validators.maxLength(120)]],
    customerEmail: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  ngOnInit(): void {
    this.loadBranches();
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

  protected onDateChange(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.date.set(value || null);
    this.resetSelection();
    this.loadSlots();
  }

  protected selectSlot(slot: Slot): void {
    if (!slot.available) {
      return;
    }
    this.selectedSlot.set(slot);
    this.errorMessage.set(null);
  }

  protected submit(): void {
    const slot = this.selectedSlot();
    if (!slot || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.api
      .book({
        branchId: Number(value.branchId),
        date: value.date,
        startTime: slot.startTime,
        customerName: value.customerName.trim(),
        customerEmail: value.customerEmail.trim(),
      })
      .subscribe({
        next: (booking) => {
          this.submitting.set(false);
          this.confirmation.set(booking);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.errorMessage.set(describeError(err));
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

  protected time(value: string): string {
    return value.slice(0, 5);
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