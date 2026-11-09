package dev.tylerpac.momentum.security;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import dev.tylerpac.momentum.model.Users;
import dev.tylerpac.momentum.repository.UsersRepository;
import dev.tylerpac.momentum.units.UnitSystem;

// Momentum keeps its own row per person for workouts and preferences, linked to the shared account's stable id.
@Service
public class LocalAccountService {

    // The password column is still NOT NULL in existing databases. Nothing reads it now.
    static final String NO_LOCAL_PASSWORD = "shared-login";

    private static final int MAX_USERNAME = 20;

    private final UsersRepository users;

    public LocalAccountService(UsersRepository users) {
        this.users = users;
    }

    public Users ensureAccount(String subject, String displayName) {
        return users.findByExternalSubject(subject).orElseGet(() -> create(subject, displayName));
    }

    private Users create(String subject, String displayName) {
        try {
            Users user = new Users();
            user.setExternalSubject(subject);
            user.setUsername(availableUsername(displayName));
            user.setPasswordHash(NO_LOCAL_PASSWORD);
            user.setUnitSystem(UnitSystem.METRIC);
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // A simultaneous first sign-in won the insert
            return users.findByExternalSubject(subject).orElseThrow(() -> e);
        }
    }

    private String availableUsername(String displayName) {
        String base = displayName == null ? "" : displayName.replaceAll("[^A-Za-z0-9_]", "");
        if (base.length() < 3) {
            base = "user";
        }
        base = base.substring(0, Math.min(base.length(), MAX_USERNAME));
        String candidate = base;
        for (int suffix = 2; users.existsByUsername(candidate); suffix++) {
            String ending = String.format(Locale.ROOT, "%d", suffix);
            candidate = base.substring(0, Math.min(base.length(), MAX_USERNAME - ending.length())) + ending;
        }
        return candidate;
    }
}
