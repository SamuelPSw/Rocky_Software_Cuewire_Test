package dev.tutorme.services;

import dev.tutorme.dto.ApiModels.*;
import dev.tutorme.entities.Lesson;
import dev.tutorme.repositories.LessonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class LessonService {
    private final LessonRepository repository;

    public LessonService(LessonRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<LessonView> list() {
        return repository.findAllByOrderByStartsAtAscIdAsc().stream().map(LessonService::view).toList();
    }

    public LessonView create(LessonInput input) {
        Lesson lesson = new Lesson();
        apply(lesson, input);
        return view(repository.save(lesson));
    }

    public LessonView edit(long id, LessonInput input) {
        Lesson lesson = active(id);
        apply(lesson, input);
        if (lesson.balance().signum() < 0) {
            throw conflict("The revised fee would be lower than the amount paid.");
        }
        return view(lesson);
    }

    public LessonView cancel(long id) {
        Lesson lesson = find(id);
        if (lesson.paid.signum() > 0) {
            throw conflict("Clear the recorded payment before cancelling this session.");
        }
        lesson.cancelled = true;
        lesson.remindedAt = null;
        return view(lesson);
    }

    public LessonView payment(long id, BigDecimal amount) {
        Lesson lesson = active(id);
        money(amount, "Enter a valid total paid with up to two decimal places.");
        if (amount.compareTo(lesson.fee()) > 0) {
            throw bad("The total paid cannot exceed the session fee.");
        }
        lesson.paid = amount.setScale(2);
        if (lesson.balance().signum() == 0) {
            lesson.remindedAt = null;
        }
        return view(lesson);
    }

    public LessonView remind(long id) {
        Lesson lesson = active(id);
        if (lesson.balance().signum() <= 0) {
            throw conflict("This session has no outstanding balance.");
        }
        lesson.remindedAt = Instant.now();
        return view(lesson);
    }

    public LessonView shareNotes(long id, String text) {
        Lesson lesson = active(id);
        if (!lesson.completedAt(Instant.now())) {
            throw conflict("Lesson notes can be shared after the session ends.");
        }
        if (text == null || text.isBlank() || text.strip().length() > 5000) {
            throw bad("Enter lesson notes of 1 to 5000 characters.");
        }
        String notes = text.strip();
        if (!Objects.equals(lesson.notes, notes)) {
            lesson.notes = notes;
            lesson.notesRevision = UUID.randomUUID().toString();
        }
        return view(lesson);
    }

    private Lesson find(long id) {
        return repository.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found."));
    }

    private Lesson active(long id) {
        Lesson lesson = find(id);
        if (lesson.cancelled) {
            throw conflict("This session has been cancelled.");
        }
        return lesson;
    }

    private static void apply(Lesson lesson, LessonInput input) {
        if (input.subject() == null || input.subject().isBlank() || input.subject().strip().length() > 100) {
            throw bad("Enter a subject of 1 to 100 characters.");
        }
        if (input.startsAt() == null || input.startsAt().isBefore(Instant.parse("2000-01-01T00:00:00Z"))
            || !input.startsAt().isBefore(Instant.parse("2100-01-01T00:00:00Z"))) {
            throw bad("Enter a session date between 2000 and 2099.");
        }
        if (input.minutes() == null || input.minutes() < 15 || input.minutes() > 480) {
            throw bad("Enter a duration of 15 to 480 minutes.");
        }
        money(input.hourlyRate(), "Enter a valid hourly rate with up to two decimal places.");
        if (input.hourlyRate().compareTo(new BigDecimal("10000")) > 0) {
            throw bad("The hourly rate cannot exceed 10,000.");
        }
        lesson.subject = input.subject().strip();
        lesson.startsAt = input.startsAt();
        lesson.minutes = input.minutes();
        lesson.hourlyRate = input.hourlyRate().setScale(2);
        lesson.zoomUrl = zoom(input.zoomUrl());
        if (lesson.balance().signum() == 0) {
            lesson.remindedAt = null;
        }
    }

    static String zoom(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.strip();
        if (url.length() <= 500) {
            try {
                URI uri = URI.create(url);
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && (host.equals("zoom.us") || host.endsWith(".zoom.us"))
                    && uri.getPath().matches("/(j|my|w)/[^/]+/?")) {
                    return url;
                }
            } catch (IllegalArgumentException ignored) {
                // Report malformed and unsupported URLs consistently.
            }
        }
        throw bad("Enter an HTTPS Zoom meeting link.");
    }

    private static void money(BigDecimal amount, String message) {
        if (amount == null || amount.signum() < 0 || amount.scale() > 2) throw bad(message);
    }

    private static LessonView view(Lesson l) {
        LessonNotes notes = l.notes != null && l.completedAt(Instant.now())
            ? new LessonNotes(l.notes, l.notesRevision) : null;
        return new LessonView(l.id, l.subject, l.startsAt, l.minutes, l.hourlyRate, l.zoomUrl,
            l.cancelled, l.fee(), l.paid, l.balance(), l.remindedAt, notes);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
