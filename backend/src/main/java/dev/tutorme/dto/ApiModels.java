package dev.tutorme.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

public final class ApiModels {
    private ApiModels() {}

    public record LessonInput(String subject, Instant startsAt, Integer minutes,
                              BigDecimal hourlyRate, String zoomUrl) {}
    public record LessonView(Long id, String subject, Instant startsAt, int minutes,
                             BigDecimal hourlyRate, String zoomUrl, boolean cancelled,
                             BigDecimal fee, BigDecimal paid, BigDecimal balance, Instant remindedAt) {}
    public record Payment(BigDecimal amount) {}
    public record AvailabilityInput(Integer weekday, LocalTime startsAt, LocalTime endsAt, String timeZone) {}
    public record AvailabilityView(Long id, int weekday, LocalTime startsAt, LocalTime endsAt, String timeZone) {}
    public record PdfInfo(Long id, String name, long size) {}
}
