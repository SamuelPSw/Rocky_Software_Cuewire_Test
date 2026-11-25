package dev.tutorme.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
public class Lesson {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Version
    private long version;
    public String subject;
    public Instant startsAt;
    public int minutes;
    @Column(precision = 12, scale = 2)
    public BigDecimal hourlyRate;
    @Column(length = 500)
    public String zoomUrl;
    public boolean cancelled;
    @Column(precision = 12, scale = 2)
    public BigDecimal paid = new BigDecimal("0.00");
    public Instant remindedAt;
    @Column(length = 10000)
    public String noteDraft;
    @Column(length = 10000)
    public String sharedNote;
    @OneToOne(fetch = FetchType.LAZY, orphanRemoval = true)
    public Handout notePdf;

    public boolean completed() {
        return !cancelled && !startsAt.plusSeconds(minutes * 60L).isAfter(Instant.now());
    }

    public boolean hasSharedNotes() {
        return sharedNote != null || notePdf != null;
    }

    public BigDecimal fee() {
        return cancelled ? new BigDecimal("0.00")
            : hourlyRate.multiply(BigDecimal.valueOf(minutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal balance() {
        return fee().subtract(paid);
    }
}

