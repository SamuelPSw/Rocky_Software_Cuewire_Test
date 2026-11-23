package dev.tutorme;

import dev.tutorme.dto.ApiModels.LessonInput;
import dev.tutorme.repositories.LessonRepository;
import dev.tutorme.services.LessonService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;

@Component
@ConditionalOnProperty(name = "tutorme.seed", havingValue = "true", matchIfMissing = true)
public class DemoData implements CommandLineRunner {
    private final LessonRepository repository;
    private final LessonService lessons;

    public DemoData(LessonRepository repository, LessonService lessons) {
        this.repository = repository;
        this.lessons = lessons;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() != 0) return;
        LocalDate today = LocalDate.now();
        seed("Algebra foundations", today.plusDays(1), 16, 60, "50");
        seed("Essay structure", today.plusDays(4), 17, 45, "48");
        var geometry = seed("Geometry review", today.minusDays(4), 15, 60, "50");
        lessons.payment(geometry.id(), new BigDecimal("20"));
        lessons.remind(geometry.id());
    }

    private dev.tutorme.dto.ApiModels.LessonView seed(String subject, LocalDate date, int hour, int minutes, String rate) {
        Instant start = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant();
        return lessons.create(new LessonInput(subject, start, minutes, new BigDecimal(rate), null));
    }
}
