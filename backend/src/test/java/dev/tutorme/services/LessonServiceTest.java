package dev.tutorme.services;

import dev.tutorme.dto.ApiModels.LessonInput;
import dev.tutorme.entities.Lesson;
import dev.tutorme.repositories.LessonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LessonServiceTest {
    @Test
    void roundsSessionFeesToCents() {
        Lesson lesson = new Lesson();
        lesson.hourlyRate = new BigDecimal("55.55");
        lesson.minutes = 45;
        lesson.paid = new BigDecimal("10.00");
        assertEquals(new BigDecimal("41.66"), lesson.fee());
        assertEquals(new BigDecimal("31.66"), lesson.balance());
    }

    @Test
    void allowsZoomMeetingUrlsAndRejectsLookalikes() {
        assertEquals("https://us02web.zoom.us/j/123?pwd=fictional",
            LessonService.zoom("https://us02web.zoom.us/j/123?pwd=fictional"));
        assertThrows(ResponseStatusException.class, () -> LessonService.zoom("https://zoom.us.example.test/j/123"));
        assertThrows(ResponseStatusException.class, () -> LessonService.zoom("https://evil@zoom.us/j/123"));
        assertThrows(ResponseStatusException.class, () -> LessonService.zoom("http://zoom.us/j/123"));
        assertNull(LessonService.zoom(" "));
    }

    @Test
    void rejectsInvalidDurationBeforeSaving() {
        LessonRepository repository = mock(LessonRepository.class);
        LessonService service = new LessonService(repository);
        assertThrows(ResponseStatusException.class, () -> service.create(
            new LessonInput("Algebra", Instant.now(), 10, new BigDecimal("50"), null)));
        verifyNoInteractions(repository);
    }

    @Test
    void preventsOverpayment() {
        LessonRepository repository = mock(LessonRepository.class);
        Lesson lesson = new Lesson();
        lesson.hourlyRate = new BigDecimal("40");
        lesson.minutes = 30;
        when(repository.findById(1L)).thenReturn(Optional.of(lesson));
        LessonService service = new LessonService(repository);
        assertThrows(ResponseStatusException.class, () -> service.payment(1L, new BigDecimal("20.01")));
        assertEquals(new BigDecimal("0.00"), lesson.paid);
    }
}
