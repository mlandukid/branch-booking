export interface Branch {
  id: number;
  name: string;
  address: string;
  city: string;
  opensAt: string;
  closesAt: string;
  slotMinutes: number;
}

export interface Slot {
  startTime: string;
  endTime: string;
  available: boolean;
}

export interface CreateBookingRequest {
  branchId: number;
  date: string;
  startTime: string;
  customerName: string;
  customerEmail: string;
}

export interface Booking {
  reference: string;
  branchId: number;
  branchName: string;
  date: string;
  startTime: string;
  endTime: string;
  customerName: string;
  customerEmail: string;
  createdAt: string;
}
export interface Holiday {
  date: string;
  code: string;
  name: string;
}