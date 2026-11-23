package dev.tutorme.controllers;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail invalid(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail largeUpload() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "PDFs are limited to 5 MB.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformed() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Check the submitted values and date format.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentEdit() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This session changed. Refresh and try again.");
    }
}
