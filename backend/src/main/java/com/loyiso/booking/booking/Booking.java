package com.loyiso.booking.booking;

import com.loyiso.booking.branch.Branch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerEmail;

    @Column(nullable = false)
    private Instant createdAt;

    /** SHA-256 of the private token. The token itself is never stored. */
    @Column(name = "manage_token_hash", length = 64)
    private String manageTokenHash;

    @Version
    private long version;

    protected Booking() {
        // required by JPA
    }

    public Booking(Branch branch, LocalDate appointmentDate, LocalTime startTime,
                   String customerName, String customerEmail, Instant createdAt) {
        this.reference = UUID.randomUUID().toString();
        this.branch = branch;
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.createdAt = createdAt;
    }

    public void reschedule(LocalDate newDate, LocalTime newStart) {
        this.appointmentDate = newDate;
        this.startTime = newStart;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public Branch getBranch() { return branch; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public LocalTime getStartTime() { return startTime; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public Instant getCreatedAt() { return createdAt; }
    public String getManageTokenHash() { return manageTokenHash; }
    public void setManageTokenHash(String manageTokenHash) { this.manageTokenHash = manageTokenHash; }
}