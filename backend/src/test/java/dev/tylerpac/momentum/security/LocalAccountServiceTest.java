package dev.tylerpac.momentum.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import dev.tylerpac.momentum.model.Users;
import dev.tylerpac.momentum.repository.UsersRepository;

class LocalAccountServiceTest {

    private final UsersRepository users = mock(UsersRepository.class);
    private final LocalAccountService service = new LocalAccountService(users);

    @BeforeEach
    void savesReturnTheEntity() {
        when(users.saveAndFlush(any(Users.class))).thenAnswer(call -> call.getArgument(0));
    }

    private Users created() {
        ArgumentCaptor<Users> saved = ArgumentCaptor.forClass(Users.class);
        verify(users).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    @Test
    void anExistingAccountIsReused() {
        Users existing = new Users();
        when(users.findByExternalSubject("sub-1")).thenReturn(Optional.of(existing));
        assertSame(existing, service.ensureAccount("sub-1", "Name"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void aNewAccountIsLinkedToTheSubject() {
        when(users.findByExternalSubject("sub-1")).thenReturn(Optional.empty());
        service.ensureAccount("sub-1", "Alex_99");
        assertEquals("sub-1", created().getExternalSubject());
    }

    @Test
    void displayNamesBecomeValidUsernames() {
        when(users.findByExternalSubject("sub-1")).thenReturn(Optional.empty());
        service.ensureAccount("sub-1", "Alex Smith! [GRIT]");
        assertEquals("AlexSmithGRIT", created().getUsername());
    }

    @Test
    void takenUsernamesGetANumberWithinTheLengthLimit() {
        String twenty = "abcdefghijklmnopqrst";
        when(users.findByExternalSubject("sub-1")).thenReturn(Optional.empty());
        when(users.existsByUsername(twenty)).thenReturn(true);
        service.ensureAccount("sub-1", twenty);
        assertEquals("abcdefghijklmnopqrs2", created().getUsername());
    }

    @Test
    void unusableNamesFallBackToAGenericOne() {
        when(users.findByExternalSubject("sub-1")).thenReturn(Optional.empty());
        service.ensureAccount("sub-1", "★");
        assertEquals("user", created().getUsername());
    }

    @Test
    void losingAFirstSignInRaceReturnsTheWinner() {
        Users winner = new Users();
        when(users.findByExternalSubject("sub-1")).thenReturn(Optional.empty(), Optional.of(winner));
        when(users.saveAndFlush(any(Users.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertSame(winner, service.ensureAccount("sub-1", "Name"));
    }
}
