package dev.tylerpac.momentum.controller;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.tylerpac.momentum.dto.dashboard.DashboardResponse;
import dev.tylerpac.momentum.dto.workout.WorkoutDto;
import dev.tylerpac.momentum.model.Users;
import dev.tylerpac.momentum.model.Workout;
import dev.tylerpac.momentum.repository.UsersRepository;
import dev.tylerpac.momentum.repository.WorkoutRepository;
import dev.tylerpac.momentum.units.UnitSystem;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final UsersRepository usersRepository;
    private final WorkoutRepository workoutRepository;

    public DashboardController(UsersRepository usersRepository, WorkoutRepository workoutRepository) {
        this.usersRepository = usersRepository;
        this.workoutRepository = workoutRepository;
    }

    @GetMapping
    public DashboardResponse dashboard(
            @RequestParam(required = false) String workoutType,
            @RequestParam(required = false) String workoutName,
            @RequestParam(required = false, defaultValue = "all") String dateRange,
            Authentication authentication
    ) {
        Users user = requireUser(authentication);

        Map<String, List<String>> workoutMap = buildWorkoutMap(user);
        long totalWorkouts = workoutRepository.countByUser(user);

        Workout latestWorkoutEntity = workoutRepository.findFirstByUserOrderByWorkoutDateDesc(user).orElse(null);
        WorkoutDto latestWorkout = latestWorkoutEntity != null ? toDto(latestWorkoutEntity, user) : null;

        String resolvedType = workoutType;
        String resolvedName = workoutName;
        if ((resolvedType == null || resolvedType.isBlank() || resolvedName == null || resolvedName.isBlank())
                && latestWorkoutEntity != null) {
            resolvedType = latestWorkoutEntity.getWorkoutType();
            resolvedName = latestWorkoutEntity.getWorkoutName();
        }

        List<Workout> relevantWorkouts = Collections.emptyList();
        if (resolvedType != null && !resolvedType.isBlank() && resolvedName != null && !resolvedName.isBlank()) {
            relevantWorkouts = workoutRepository.findByUserAndWorkoutTypeAndWorkoutNameOrderByWorkoutDateAsc(
                    user,
                    resolvedType,
                    resolvedName
            );
        }

        // Apply date range filter
        Date cutoff = resolveCutoff(dateRange);
        if (cutoff != null) {
            final Date finalCutoff = cutoff;
            relevantWorkouts = relevantWorkouts.stream()
                    .filter(w -> w.getWorkoutDate() != null && !w.getWorkoutDate().before(finalCutoff))
                    .toList();
        }

        List<WorkoutDto> workoutDetails = new ArrayList<>();
        List<String> sortedDates = new ArrayList<>();
        List<Float> graph1Values = new ArrayList<>();
        List<Float> graph2Values = new ArrayList<>();

        for (Workout w : relevantWorkouts) {
            workoutDetails.add(toDto(w, user));
            if (w.getWorkoutDate() != null) {
                sortedDates.add(w.getWorkoutDate().toString());
            }

            if ("Cardio".equals(resolvedType)
                    && w.getDistance() != null && w.getTime() != null
                    && w.getDistance() > 0) {
                Float displayDistance = UnitSystem.distanceFromCanonical(w.getDistance(), user.getUnitSystem());
                if (displayDistance == null || displayDistance <= 0) {
                    continue;
                }
                float pace = w.getTime() / displayDistance;
                graph1Values.add(pace);
                graph2Values.add(displayDistance);
            } else if ("Weightlifting".equals(resolvedType)
                    && w.getReps() != null) {
                Float displayWeight = UnitSystem.weightFromCanonical(w.getWeight(), user.getUnitSystem());
                if (w.getWeight() != null) {
                    graph1Values.add(displayWeight);
                }

                Integer setsBoxed = w.getSets();
                int sets = setsBoxed != null ? setsBoxed : 1;
                int reps = w.getReps();
                float totalReps = Math.max(0, sets) * Math.max(0, reps);
                if (displayWeight != null) {
                    graph2Values.add(displayWeight * totalReps);
                } else {
                    graph2Values.add(totalReps);
                }
            }
        }

        return new DashboardResponse(
                resolvedType,
                resolvedName,
                totalWorkouts,
                latestWorkout,
                workoutDetails,
                sortedDates,
                graph1Values,
                graph2Values,
                workoutMap
        );
    }

    private static Date resolveCutoff(String dateRange) {
        if (dateRange == null || dateRange.isBlank() || "all".equalsIgnoreCase(dateRange)) {
            return null;
        }
        try {
            int days = Integer.parseInt(dateRange);
            if (days > 0) {
                return Date.valueOf(LocalDate.now().minusDays(days));
            }
        } catch (NumberFormatException ignored) {
            // fall through to null
        }
        return null;
    }

    private Map<String, List<String>> buildWorkoutMap(Users user) {
        List<Object[]> pairs = workoutRepository.findDistinctTypeNamePairsByUser(user);
        Map<String, List<String>> map = new HashMap<>();
        for (Object[] row : pairs) {
            String type = (String) row[0];
            String name = (String) row[1];
            if (type == null || name == null) {
                continue;
            }
            map.computeIfAbsent(type, k -> new ArrayList<>()).add(name);
        }
        map.values().forEach(list -> list.sort(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return map;
    }

    private Users requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in");
        }
        return usersRepository.findByExternalSubject(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
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
}

