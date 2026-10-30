import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, retry, throwError, timer } from 'rxjs';

import { Booking, Branch, CreateBookingRequest, Slot } from './booking.models';

/** A booking request plus an optional key that makes repeating the request safe. */
export interface BookingAttempt extends CreateBookingRequest {
  idempotencyKey?: string;
}

/** Thin client for the booking REST API. All paths are relative, so a proxy or reverse proxy serves them. */
@Injectable({ providedIn: 'root' })
export class BookingApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1';

  branches(): Observable<Branch[]> {
    return this.http.get<Branch[]>(`${this.base}/branches`);
  }

  slots(branchId: number, date: string): Observable<Slot[]> {
    return this.http.get<Slot[]>(`${this.base}/branches/${branchId}/slots`, { params: { date } });
  }

  book(attempt: BookingAttempt): Observable<Booking> {
    const { idempotencyKey, ...request } = attempt;
    const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined;
    const post$ = this.http.post<Booking>(`${this.base}/bookings`, request, { headers });
    if (!idempotencyKey) {
      return post$;
    }
    // With a key, repeating the request is safe, so one dropped connection is retried automatically.
    return post$.pipe(
      retry({
        count: 1,
        delay: (error: unknown) =>
          error instanceof HttpErrorResponse && error.status === 0 ? timer(800) : throwError(() => error),
      }),
    );
  }
}