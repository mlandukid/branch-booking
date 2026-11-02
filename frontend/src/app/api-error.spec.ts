import { HttpErrorResponse } from '@angular/common/http';

import { describeError } from './api-error';

function failure(status: number, error?: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ status, error });
}

describe('describeError', () => {
  it('explains a lost connection', () => {
    expect(describeError(failure(0))).toContain('could not reach');
  });

  it('turns a lost race into a friendly message', () => {
    expect(describeError(failure(409, { title: 'Slot unavailable' }))).toContain('just booked by someone else');
  });

  it('keeps the server wording when it is too late to change a booking', () => {
    const detail = 'Appointments can only be changed or cancelled up to 2 hours before they start.';

    expect(describeError(failure(409, { title: 'Too late to change', detail }))).toBe(detail);
  });

  it('lists the fields that failed validation', () => {
    expect(describeError(failure(400, { errors: { customerName: 'must not be blank' } })))
      .toBe('customerName: must not be blank');
  });

  it('uses the problem detail when there is one', () => {
    expect(describeError(failure(400, { detail: 'Branches are closed on weekends.' })))
      .toBe('Branches are closed on weekends.');
  });

  it('falls back to a generic message', () => {
    expect(describeError(failure(500))).toContain('Something went wrong');
  });
});