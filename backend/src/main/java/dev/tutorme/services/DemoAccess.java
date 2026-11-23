package dev.tutorme.services;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class DemoAccess {
    public static final String HEADER = "X-Demo-Role";

    private DemoAccess() {}

    public static void read(String role) {
        if (!"TUTOR".equals(role) && !"FAMILY".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Choose a demo role.");
        }
    }

    public static void tutor(String role) {
        if (!"TUTOR".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the demo tutor can make changes.");
        }
    }
}
