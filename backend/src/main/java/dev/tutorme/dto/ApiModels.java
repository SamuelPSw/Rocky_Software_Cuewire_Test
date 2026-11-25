package dev.tutorme.dto;

import java.math.BigDecimal;
import java.time.Instant;

public final class ApiModels {
    private ApiModels() {}

    public record LessonInput(String subject, Instant startsAt, Integer minutes,
                              BigDecimal hourlyRate, String zoomUrl) {}
    public record LessonView(Long id, String subject, Instant startsAt, int minutes,
                             BigDecimal hourlyRate, String zoomUrl, boolean cancelled,
                             BigDecimal fee, BigDecimal paid, BigDecimal balance, Instant remindedAt,
                             LessonNotes notes) {}
    public record NotesInput(String text) {}
    public record LessonNotes(String text, String revision) {}
    public record Payment(BigDecimal amount) {}
    public record PdfInfo(Long id, String name, long size) {}
}
