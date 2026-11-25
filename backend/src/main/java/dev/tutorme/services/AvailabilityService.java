package dev.tutorme.services;

import dev.tutorme.dto.ApiModels.AvailabilityInput;
import dev.tutorme.dto.ApiModels.AvailabilityView;
import dev.tutorme.entities.WeeklyAvailability;
import dev.tutorme.repositories.AvailabilityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.List;

@Service
@Transactional
public class AvailabilityService {
    private final AvailabilityRepository repository;

    public AvailabilityService(AvailabilityRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityView> list() {
        return repository.findAllByOrderByWeekdayAscStartsAtAscIdAsc().stream()
            .map(AvailabilityService::view).toList();
    }

    public AvailabilityView create(AvailabilityInput input) {
        var window = new WeeklyAvailability();
        apply(window, input);
        return view(repository.save(window));
    }

    public AvailabilityView edit(long id, AvailabilityInput input) {
        var window = find(id);
        apply(window, input);
        return view(window);
    }

    public void remove(long id) {
        repository.delete(find(id));
    }

    private WeeklyAvailability find(long id) {
        return repository.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Availability window not found."));
    }

    private static void apply(WeeklyAvailability window, AvailabilityInput input) {
        if (input.weekday() == null || input.weekday() < 0 || input.weekday() > 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a day of the week.");
        }
        if (input.startsAt() == null || input.endsAt() == null || !input.startsAt().isBefore(input.endsAt())
            || input.startsAt().getSecond() != 0 || input.endsAt().getSecond() != 0
            || input.startsAt().getNano() != 0 || input.endsAt().getNano() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter start and end times on the same day, with the end after the start.");
        }
        if (input.timeZone() == null || !ZoneId.getAvailableZoneIds().contains(input.timeZone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid time zone.");
        }
        window.weekday = input.weekday();
        window.startsAt = input.startsAt();
        window.endsAt = input.endsAt();
        window.timeZone = input.timeZone();
    }

    private static AvailabilityView view(WeeklyAvailability window) {
        return new AvailabilityView(window.id, window.weekday, window.startsAt, window.endsAt, window.timeZone);
    }
}
