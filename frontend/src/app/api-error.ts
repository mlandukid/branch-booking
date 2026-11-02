import { HttpErrorResponse } from '@angular/common/http';

/** Turns a failed API call into a message a customer can act on. */
export function describeError(err: HttpErrorResponse): string {
  if (err.status === 0) {
    return 'We could not reach the booking service. Please try again in a moment.';
  }
  const title = err.error?.title as string | undefined;
  if (err.status === 409 && (title === undefined || title === 'Slot unavailable')) {
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