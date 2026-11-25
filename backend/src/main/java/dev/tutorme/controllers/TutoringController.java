package dev.tutorme.controllers;

import dev.tutorme.dto.ApiModels.*;
import dev.tutorme.services.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import static dev.tutorme.services.DemoAccess.HEADER;

@RestController
@RequestMapping("/api")
public class TutoringController {
    private final LessonService lessons;
    private final HandoutService handouts;

    public TutoringController(LessonService lessons, HandoutService handouts) {
        this.lessons = lessons;
        this.handouts = handouts;
    }

    @GetMapping("/sessions")
    List<LessonView> sessions(@RequestHeader(value = HEADER, required = false) String role) {
        DemoAccess.read(role);
        return lessons.list();
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    LessonView create(@RequestHeader(value = HEADER, required = false) String role, @RequestBody LessonInput input) {
        DemoAccess.tutor(role);
        return lessons.create(input);
    }

    @PutMapping("/sessions/{id}")
    LessonView edit(@RequestHeader(value = HEADER, required = false) String role, @PathVariable long id,
                    @RequestBody LessonInput input) {
        DemoAccess.tutor(role);
        return lessons.edit(id, input);
    }

    @PostMapping("/sessions/{id}/cancel")
    LessonView cancel(@RequestHeader(value = HEADER, required = false) String role, @PathVariable long id) {
        DemoAccess.tutor(role);
        return lessons.cancel(id);
    }

    @PutMapping("/sessions/{id}/payment")
    LessonView payment(@RequestHeader(value = HEADER, required = false) String role, @PathVariable long id,
                       @RequestBody Payment payment) {
        DemoAccess.tutor(role);
        return lessons.payment(id, payment.amount());
    }

    @PostMapping("/sessions/{id}/reminder")
    LessonView reminder(@RequestHeader(value = HEADER, required = false) String role, @PathVariable long id) {
        DemoAccess.tutor(role);
        return lessons.remind(id);
    }

    @PutMapping("/sessions/{id}/notes")
    LessonView shareNotes(@RequestHeader(value = HEADER, required = false) String role, @PathVariable long id,
                          @RequestBody NotesInput input) {
        DemoAccess.tutor(role);
        return lessons.shareNotes(id, input.text());
    }

    @GetMapping("/handouts")
    List<PdfInfo> handouts(@RequestHeader(value = HEADER, required = false) String role) {
        DemoAccess.read(role);
        return handouts.list();
    }

    @PostMapping("/handouts")
    @ResponseStatus(HttpStatus.CREATED)
    PdfInfo upload(@RequestHeader(value = HEADER, required = false) String role,
                   @RequestParam MultipartFile file) throws IOException {
        DemoAccess.tutor(role);
        return handouts.upload(file);
    }

    @GetMapping("/handouts/{id}")
    ResponseEntity<byte[]> pdf(@RequestHeader(value = HEADER, required = false) String role, @PathVariable long id,
                               @RequestParam(defaultValue = "false") boolean download) {
        DemoAccess.read(role);
        var handout = handouts.get(id);
        var disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
            .filename(handout.name).build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .contentLength(handout.size)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "sandbox")
            .cacheControl(CacheControl.noStore()).body(handout.content);
    }
}
