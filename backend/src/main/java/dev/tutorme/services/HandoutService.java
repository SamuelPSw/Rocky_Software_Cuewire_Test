package dev.tutorme.services;

import dev.tutorme.dto.ApiModels.PdfInfo;
import dev.tutorme.entities.Handout;
import dev.tutorme.repositories.HandoutRepository;
import org.apache.pdfbox.Loader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class HandoutService {
    private static final long LIMIT = 5 * 1024 * 1024;
    private final HandoutRepository repository;

    public HandoutService(HandoutRepository repository) {
        this.repository = repository;
    }

    public List<PdfInfo> list() {
        return repository.listMetadata();
    }

    public PdfInfo upload(MultipartFile file) throws IOException {
        if (file.getSize() > LIMIT) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PDFs are limited to 5 MB.");
        }
        byte[] bytes = file.getBytes();
        String signature = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
        int tailStart = Math.max(0, bytes.length - 1024);
        String tail = new String(bytes, tailStart, bytes.length - tailStart, StandardCharsets.US_ASCII);
        if (!signature.matches("%PDF-[12]\\.[0-9]") || !tail.stripTrailing().endsWith("%%EOF")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PDF with a valid header and end marker.");
        }
        try (var pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload an unencrypted PDF with at least one page.");
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The PDF is damaged or password protected.");
        }
        String name = file.getOriginalFilename() == null ? "handout.pdf" : file.getOriginalFilename();
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9._ -]", "_").strip();
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf") || name.length() > 120) name = "handout.pdf";
        Handout handout = new Handout();
        handout.name = name;
        handout.size = bytes.length;
        handout.content = bytes;
        repository.save(handout);
        return new PdfInfo(handout.id, handout.name, handout.size);
    }

    public Handout get(long id) {
        return repository.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found."));
    }
}
