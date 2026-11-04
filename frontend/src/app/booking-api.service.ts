import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, retry, throwError, timer } from 'rxjs';

import { Booking, Branch, CreateBookingRequest, Holiday, Reason, Slot } from './booking.models';

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

  view(reference: string, token: string): Observable<Booking> {
    return this.http.get<Booking>(`${this.base}/bookings/${encodeURIComponent(reference)}/manage`, {
      headers: { 'X-Manage-Token': token },
    });
  }

  move(reference: string, token: string, date: string, startTime: string): Observable<Booking> {
    return this.http.patch<Booking>(
      `${this.base}/bookings/${encodeURIComponent(reference)}`,
      { date, startTime },
      { headers: { 'X-Manage-Token': token } },
    );
  }

  cancel(reference: string, token: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/bookings/${encodeURIComponent(reference)}`, {
      headers: { 'X-Manage-Token': token },
    });
  }

  reasons(): Observable<Reason[]> {
    return this.http.get<Reason[]>(`${this.base}/reasons`);
  }

  holidays(year: number): Observable<Holiday[]> {
    return this.http.get<Holiday[]>(`${this.base}/holidays`, { params: { year } });
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