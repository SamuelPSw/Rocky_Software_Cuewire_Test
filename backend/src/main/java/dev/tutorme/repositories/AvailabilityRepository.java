package dev.tutorme.repositories;

import dev.tutorme.entities.WeeklyAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AvailabilityRepository extends JpaRepository<WeeklyAvailability, Long> {
    List<WeeklyAvailability> findAllByOrderByWeekdayAscStartsAtAscIdAsc();
}
