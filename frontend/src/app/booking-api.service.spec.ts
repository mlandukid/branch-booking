import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { BookingApi } from './booking-api.service';

describe('BookingApi', () => {
  let api: BookingApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(BookingApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests the slots for a branch and date', () => {
    let count = 0;
    api.slots(1, '2026-11-02').subscribe((slots) => (count = slots.length));

    const req = http.expectOne((r) => r.url === '/api/v1/branches/1/slots');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('date')).toBe('2026-11-02');
    req.flush([{ startTime: '09:00:00', endTime: '09:30:00', available: true }]);

    expect(count).toBe(1);
  });

  it('sends the idempotency key as a header and keeps it out of the body', () => {
    api
      .book({
        branchId: 1,
        date: '2026-11-02',
        startTime: '09:00:00',
        customerName: 'Thandi Nkosi',
        customerEmail: 'thandi@example.com',
        idempotencyKey: 'attempt-12345678',
      })
      .subscribe();

    const req = http.expectOne('/api/v1/bookings');
    expect(req.request.headers.get('Idempotency-Key')).toBe('attempt-12345678');
    expect(req.request.body.idempotencyKey).toBeUndefined();
    req.flush({});
  });

  it('requests the public holidays of a year', () => {
    let names: string[] = [];
    api.holidays(2026).subscribe((holidays) => (names = holidays.map((holiday) => holiday.name)));

    const req = http.expectOne((r) => r.url === '/api/v1/holidays');
    expect(req.request.params.get('year')).toBe('2026');
    req.flush([{ date: '2026-12-25', code: 'CHRISTMAS_DAY', name: 'Christmas Day' }]);

    expect(names).toEqual(['Christmas Day']);
  });

  it('posts a booking request', () => {
    const body = {
      branchId: 1,
      date: '2026-11-02',
      startTime: '09:00:00',
      customerName: 'Thandi Nkosi',
      customerEmail: 'thandi@example.com',
    };
    api.book(body).subscribe();

    const req = http.expectOne('/api/v1/bookings');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });
});