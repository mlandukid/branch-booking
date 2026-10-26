package com.loyiso.booking.branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;

@Entity
@Table(name = "branches")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private LocalTime opensAt;

    @Column(nullable = false)
    private LocalTime closesAt;

    @Column(nullable = false)
    private int slotMinutes;

    protected Branch() {
        // required by JPA
    }

    public Branch(String name, String address, String city,
                  LocalTime opensAt, LocalTime closesAt, int slotMinutes) {
        this.name = name;
        this.address = address;
        this.city = city;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.slotMinutes = slotMinutes;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public LocalTime getOpensAt() { return opensAt; }
    public LocalTime getClosesAt() { return closesAt; }
    public int getSlotMinutes() { return slotMinutes; }
}