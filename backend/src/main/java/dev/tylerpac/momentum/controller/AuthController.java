package dev.tylerpac.momentum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.tylerpac.momentum.dto.common.UserDto;
import dev.tylerpac.momentum.mapper.UserDtoMapper;
import dev.tylerpac.momentum.model.Users;
import dev.tylerpac.momentum.repository.UsersRepository;

// Signing in and out go through Spring Security's OpenID Connect endpoints; this only reports who is signed in.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsersRepository usersRepository;
    private final UserDtoMapper userDtoMapper;

    public AuthController(UsersRepository usersRepository, UserDtoMapper userDtoMapper) {
        this.usersRepository = usersRepository;
        this.userDtoMapper = userDtoMapper;
    }

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        Users user = usersRepository.findByExternalSubject(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
        return userDtoMapper.toDto(user);
    }
}
