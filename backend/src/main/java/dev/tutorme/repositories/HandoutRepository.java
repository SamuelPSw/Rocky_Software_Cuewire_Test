package dev.tutorme.repositories;

import dev.tutorme.entities.Handout;
import dev.tutorme.dto.ApiModels.PdfInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface HandoutRepository extends JpaRepository<Handout, Long> {
    @Query("select new dev.tutorme.dto.ApiModels$PdfInfo(h.id, h.name, h.size) from Handout h order by h.id desc")
    List<PdfInfo> listMetadata();
}
