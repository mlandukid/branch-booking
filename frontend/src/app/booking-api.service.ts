import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Booking, Branch, CreateBookingRequest, Slot } from './booking.models';

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

  book(request: CreateBookingRequest): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings`, request);
  }
}