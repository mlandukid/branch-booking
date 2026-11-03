package com.loyiso.booking.booking;

/** Why a customer is visiting, with a short note on what to bring. */
public enum BookingReason {
    OPEN_ACCOUNT("Open an account",
            "Bring your South African ID or passport and a recent proof of address."),
    REPLACE_CARD("Replace a card",
            "Bring your ID. If the card was lost or stolen, block it in your banking app or by phone before you come in."),
    TROUBLESHOOTING("Get help with a problem",
            "Bring your ID, and your phone or card if the problem involves them, plus a short note of what went wrong."),
    UPDATE_DETAILS("Update my personal details",
            "Bring your ID and proof of the new details, such as a recent proof of address."),
    LOANS_AND_SAVINGS("Loans and savings advice",
            "Bring your ID and, if you have them, recent payslips or bank statements."),
    OTHER("Something else",
            "Bring your ID.");

    private final String label;
    private final String bring;

    BookingReason(String label, String bring) {
        this.label = label;
        this.bring = bring;
    }

    public String label() {
        return label;
    }

    public String bring() {
        return bring;
    }
}