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
    private final AvailabilityService availability;

    public TutoringController(LessonService lessons, HandoutService handouts, AvailabilityService availability) {
        this.lessons = lessons;
        this.handouts = handouts;
        this.availability = availability;
    }

    @GetMapping("/availability")
    List<AvailabilityView> availability(@RequestHeader(value = HEADER, required = false) String role) {
        DemoAccess.read(role);
        return availability.list();
    }

    @PostMapping("/availability")
    @ResponseStatus(HttpStatus.CREATED)
    AvailabilityView createAvailability(@RequestHeader(value = HEADER, required = false) String role,
                                        @RequestBody AvailabilityInput input) {
        DemoAccess.tutor(role);
        return availability.create(input);
    }

    @PutMapping("/availability/{id}")
    AvailabilityView editAvailability(@RequestHeader(value = HEADER, required = false) String role,
                                      @PathVariable long id, @RequestBody AvailabilityInput input) {
        DemoAccess.tutor(role);
        return availability.edit(id, input);
    }

    @DeleteMapping("/availability/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeAvailability(@RequestHeader(value = HEADER, required = false) String role, @PathVariable long id) {
        DemoAccess.tutor(role);
        availability.remove(id);
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
