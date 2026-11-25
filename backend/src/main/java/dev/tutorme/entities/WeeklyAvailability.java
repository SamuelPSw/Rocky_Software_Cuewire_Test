package dev.tutorme.entities;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
public class WeeklyAvailability {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public int weekday;

    @Column(nullable = false)
    public LocalTime startsAt;

    @Column(nullable = false)
    public LocalTime endsAt;

    @Column(nullable = false, length = 100)
    public String timeZone;
}
