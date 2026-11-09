package dev.tylerpac.momentum.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.tylerpac.momentum.dto.settings.SettingsActionResponse;
import dev.tylerpac.momentum.dto.settings.SettingsUnitSystemUpdateRequest;
import dev.tylerpac.momentum.dto.settings.SettingsUsernameUpdateRequest;
import dev.tylerpac.momentum.dto.common.UserDto;
import dev.tylerpac.momentum.exception.ValidationException;
import dev.tylerpac.momentum.mapper.UserDtoMapper;
import dev.tylerpac.momentum.model.Users;
import dev.tylerpac.momentum.repository.UsersRepository;
import dev.tylerpac.momentum.repository.WorkoutRepository;
import dev.tylerpac.momentum.units.UnitSystem;
import dev.tylerpac.momentum.validation.ValidationRules;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final UsersRepository usersRepository;
    private final WorkoutRepository workoutRepository;
    private final UserDtoMapper userDtoMapper;

    public SettingsController(
            UsersRepository usersRepository,
            WorkoutRepository workoutRepository,
            UserDtoMapper userDtoMapper
    ) {
        this.usersRepository = usersRepository;
        this.workoutRepository = workoutRepository;
        this.userDtoMapper = userDtoMapper;
    }

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        Users user = requireUser(authentication);
        return userDtoMapper.toDto(user);
    }

    @GetMapping("/account/username/check")
    public Map<String, Boolean> checkUsernameAvailability(@RequestParam("username") String username, Authentication authentication) {
        Users user = requireUser(authentication);

        String candidate = username != null ? username.trim() : "";
        if (candidate.isBlank()) {
            return Map.of("available", false);
        }
        if (candidate.equals(user.getUsername())) {
            // Treat as available so the UI can show a green check, even though submitting would be a no-op.
            return Map.of("available", true);
        }
        if (!ValidationRules.isValidUsername(candidate)) {
            return Map.of("available", false);
        }

        return Map.of("available", !usersRepository.existsByUsername(candidate));
    }

    @PostMapping("/account/username")
    public SettingsActionResponse updateUsername(@RequestBody SettingsUsernameUpdateRequest req, Authentication authentication) {
        Users user = requireUser(authentication);

        String newUsername = req != null && req.newUsername() != null ? req.newUsername().trim() : "";
        if (newUsername.isBlank()) {
            throw new ValidationException(HttpStatus.BAD_REQUEST, "Please enter a username", Map.of("newUsername", "Required"));
        }
        if (!ValidationRules.isValidUsername(newUsername)) {
            throw new ValidationException(
                    HttpStatus.BAD_REQUEST,
                    "Username must be 3–20 characters and contain only letters, numbers, or underscore",
                    Map.of("newUsername", "3–20 characters, letters/numbers/underscore")
            );
        }
        if (newUsername.equals(user.getUsername())) {
            return new SettingsActionResponse(true, "Username unchanged", null, userDtoMapper.toDto(user), null);
        }
        if (usersRepository.existsByUsername(newUsername)) {
            throw new ValidationException(HttpStatus.CONFLICT, "Username already taken", Map.of("newUsername", "Username already taken"));
        }

        user.setUsername(newUsername);
        try {
            Users saved = usersRepository.save(user);

            return new SettingsActionResponse(true, "Username updated", null, userDtoMapper.toDto(saved), null);
        } catch (DataIntegrityViolationException ex) {
            // In case the DB collation/constraints treat usernames differently than the existsByUsername check.
            throw new ValidationException(HttpStatus.CONFLICT, "Username already taken", Map.of("newUsername", "Username already taken"));
        }
    }

    @PostMapping("/preferences/unit-system")
    public SettingsActionResponse updateUnitSystem(@RequestBody SettingsUnitSystemUpdateRequest req, Authentication authentication) {
        Users user = requireUser(authentication);

        String unitSystem = req != null ? UnitSystem.normalize(req.unitSystem()) : "";
        if (!UnitSystem.isSupported(unitSystem)) {
            throw new ValidationException(
                    HttpStatus.BAD_REQUEST,
                    "Please choose metric or imperial",
                    Map.of("unitSystem", "Must be metric or imperial")
            );
        }

        if (unitSystem.equals(user.getUnitSystem())) {
            return new SettingsActionResponse(true, "Units unchanged", null, userDtoMapper.toDto(user), null);
        }

        user.setUnitSystem(unitSystem);
        Users saved = usersRepository.save(user);
        return new SettingsActionResponse(true, "Units updated", null, userDtoMapper.toDto(saved), null);
    }

    @DeleteMapping("/account")
    @Transactional
    public SettingsActionResponse deleteAccount(Authentication authentication) {
        Users user = requireUser(authentication);

        workoutRepository.deleteByUser(user);
        usersRepository.delete(user);
        usersRepository.flush();

        return new SettingsActionResponse(true, "Account deleted", null, null, null);
    }

    // Fallback for environments where proxies/WAFs block HTTP DELETE.
    @PostMapping("/account/delete")
    @Transactional
    public SettingsActionResponse deleteAccountPost(Authentication authentication) {
        return deleteAccount(authentication);
    }

    private @NonNull Users requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in");
        }
        Users user = usersRepository.findByExternalSubject(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
        return Objects.requireNonNull(user, "Authenticated user lookup returned null");
    }
}
