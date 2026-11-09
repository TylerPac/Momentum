package dev.tylerpac.momentum.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.tylerpac.momentum.dto.workout.WorkoutDto;
import dev.tylerpac.momentum.dto.workout.WorkoutUpsertRequest;
import dev.tylerpac.momentum.exception.ValidationException;
import dev.tylerpac.momentum.model.Users;
import dev.tylerpac.momentum.model.Workout;
import dev.tylerpac.momentum.repository.UsersRepository;
import dev.tylerpac.momentum.repository.WorkoutRepository;
import dev.tylerpac.momentum.units.UnitSystem;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutsController {

    private final UsersRepository usersRepository;
    private final WorkoutRepository workoutRepository;

    public WorkoutsController(UsersRepository usersRepository, WorkoutRepository workoutRepository) {
        this.usersRepository = usersRepository;
        this.workoutRepository = workoutRepository;
    }

    @GetMapping("/history")
    public Map<String, Object> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String workoutType,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Authentication authentication
    ) {
        Users user = requireUser(authentication);
        int clampedSize = Math.min(Math.max(size, 1), 200);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampedSize, Sort.by(Sort.Direction.DESC, "workoutDate", "workoutId"));

        java.sql.Date sqlFrom = (dateFrom != null && !dateFrom.isBlank()) ? parseDateOrThrow(dateFrom, "dateFrom") : null;
        java.sql.Date sqlTo = (dateTo != null && !dateTo.isBlank()) ? parseDateOrThrow(dateTo, "dateTo") : null;

        Page<Workout> result = workoutRepository.findHistoryFiltered(user, search, workoutType, sqlFrom, sqlTo, pageRequest);

        List<WorkoutDto> items = result.getContent().stream().map(workout -> toDto(workout, user)).toList();
        return Map.of(
                "items", items,
                "page", result.getNumber(),
                "pageSize", result.getSize(),
                "totalItems", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        );
    }

    @GetMapping("/names")
    public List<String> workoutNames(Authentication authentication) {
        Users user = requireUser(authentication);
        return workoutRepository.findDistinctWorkoutNamesByUser(user);
    }

    @GetMapping("/last")
    public WorkoutDto lastByName(@RequestParam String name, Authentication authentication) {
        Users user = requireUser(authentication);
        return workoutRepository
                .findFirstByUserAndWorkoutNameOrderByWorkoutDateDescWorkoutIdDesc(user, name)
            .map(workout -> toDto(workout, user))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No previous workout found"));
    }

    @GetMapping("/{id}")
    public WorkoutDto getById(@PathVariable int id, Authentication authentication) {
        Users user = requireUser(authentication);
        Workout workout = workoutRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found"));

        if (workout.getUser() == null || workout.getUser().getUserId() != user.getUserId()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found");
        }

        return toDto(workout, user);
    }

    @PostMapping("/batch")
    public List<WorkoutDto> createBatch(@RequestBody List<WorkoutUpsertRequest> reqs, Authentication authentication) {
        Users user = requireUser(authentication);
        if (reqs == null || reqs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No workouts provided");
        }
        if (reqs.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many workouts in one submission (max 20)");
        }
        List<Workout> saved = reqs.stream().map(req -> {
            Workout w = new Workout();
            w.setUser(user);
            applyRequest(w, req, user);
            return workoutRepository.save(w);
        }).toList();
        return saved.stream().map(workout -> toDto(workout, user)).toList();
    }

    @PostMapping
    public WorkoutDto create(@RequestBody WorkoutUpsertRequest req, Authentication authentication) {
        Users user = requireUser(authentication);

        Workout workout = new Workout();
        workout.setUser(user);
        applyRequest(workout, req, user);

        Workout saved = workoutRepository.save(workout);
        return toDto(saved, user);
    }

    @PutMapping("/{id}")
    public WorkoutDto update(@PathVariable int id, @RequestBody WorkoutUpsertRequest req, Authentication authentication) {
        Users user = requireUser(authentication);
        Workout workout = workoutRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found"));

        if (workout.getUser() == null || workout.getUser().getUserId() != user.getUserId()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found");
        }

        applyRequest(workout, req, user);
        Workout saved = workoutRepository.save(workout);
        return toDto(saved, user);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable int id, Authentication authentication) {
        Users user = requireUser(authentication);
        Workout workout = workoutRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found"));

        if (workout.getUser() == null || workout.getUser().getUserId() != user.getUserId()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found");
        }

        workoutRepository.delete(workout);
    }

    private Users requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in");
        }
        return usersRepository.findByExternalSubject(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
    }

    private static void applyRequest(Workout workout, WorkoutUpsertRequest req, Users user) {
        Map<String, String> fieldErrors = validateWorkoutRequest(req);
        if (!fieldErrors.isEmpty()) {
            throw new ValidationException(HttpStatus.BAD_REQUEST, "Invalid workout payload", fieldErrors);
        }

        String type = req.workoutType().trim();
        String name = req.workoutName().trim();

        workout.setWorkoutType(type);
        workout.setWorkoutName(name);
        workout.setWorkoutDate(req.workoutDate() != null && !req.workoutDate().isBlank()
                ? parseDateOrThrow(req.workoutDate(), "workoutDate")
                : null);
        workout.setDistance(UnitSystem.distanceToCanonical(req.distance(), user.getUnitSystem()));
        workout.setTime(req.time());
        workout.setWeight(UnitSystem.weightToCanonical(req.weight(), user.getUnitSystem()));
        workout.setSets(req.sets());
        workout.setReps(req.reps());
        workout.setNotes(req.notes() != null ? req.notes().trim() : null);
    }

        private static WorkoutDto toDto(Workout w, Users user) {
        String date = w.getWorkoutDate() != null ? w.getWorkoutDate().toString() : null;
        return new WorkoutDto(
                w.getWorkoutId(),
                w.getWorkoutType(),
                w.getWorkoutName(),
                date,
            UnitSystem.distanceFromCanonical(w.getDistance(), user.getUnitSystem()),
                w.getTime(),
            UnitSystem.weightFromCanonical(w.getWeight(), user.getUnitSystem()),
                w.getSets(),
                w.getReps(),
                w.getNotes()
        );
    }

    private static java.sql.Date parseDateOrThrow(String s, String fieldName) {
        try {
            return java.sql.Date.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid date",
                    Map.of(fieldName, "Use YYYY-MM-DD")
            );
        }
    }

    private static Map<String, String> validateWorkoutRequest(WorkoutUpsertRequest req) {
        Map<String, String> errors = new HashMap<>();
        if (req == null) {
            return Map.of("request", "Required");
        }

        String type = req.workoutType() != null ? req.workoutType().trim() : "";
        String name = req.workoutName() != null ? req.workoutName().trim() : "";

        if (type.isBlank()) {
            errors.put("workoutType", "Required");
        } else if (!"Cardio".equals(type) && !"Weightlifting".equals(type)) {
            errors.put("workoutType", "Must be Cardio or Weightlifting");
        }

        if (name.isBlank()) {
            errors.put("workoutName", "Required");
        }

        if (req.distance() != null && req.distance() < 0) {
            errors.put("distance", "Must be 0 or greater");
        }
        if (req.time() != null && req.time() < 0) {
            errors.put("time", "Must be 0 or greater");
        }
        if (req.weight() != null && req.weight() < 0) {
            errors.put("weight", "Must be 0 or greater");
        }
        if (req.sets() != null && req.sets() < 1) {
            errors.put("sets", "Must be at least 1");
        }
        if (req.reps() != null && req.reps() < 1) {
            errors.put("reps", "Must be at least 1");
        }

        if ("Cardio".equals(type)) {
            if (req.distance() == null || req.distance() <= 0) {
                errors.put("distance", "Required and must be greater than 0 for Cardio");
            }
            if (req.time() == null || req.time() <= 0) {
                errors.put("time", "Required and must be greater than 0 for Cardio");
            }
            if (req.weight() != null || req.sets() != null || req.reps() != null) {
                errors.put("workoutType", "Cardio workouts cannot include weight/sets/reps");
            }
        }

        if ("Weightlifting".equals(type)) {
            if (req.reps() == null || req.reps() < 1) {
                errors.put("reps", "Required and must be at least 1 for Weightlifting");
            }
            if (req.distance() != null || req.time() != null) {
                errors.put("workoutType", "Weightlifting workouts cannot include distance/time");
            }
        }

        return errors;
    }
}

