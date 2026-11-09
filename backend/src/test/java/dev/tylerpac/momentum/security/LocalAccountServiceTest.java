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
        when(users.findByUsername(any())).thenReturn(Optional.empty());
    }

    private Users created() {
        ArgumentCaptor<Users> saved = ArgumentCaptor.forClass(Users.class);
        verify(users).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    private static Users account(int id, String username) {
        Users user = mock(Users.class);
        when(user.getUserId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        return user;
    }

    @Test
    void anUnchangedNameDoesNotWrite() {
        Users existing = account(1, "Name");
        when(users.findByExternalSubject("sub-1")).thenReturn(Optional.of(existing));
        when(users.findByUsername("Name")).thenReturn(Optional.of(existing));

        assertSame(existing, service.ensureAccount("sub-1", "Name"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void aChangedNameUpdatesTheUsername() {
        Users existing = new Users();
        existing.setUsername("Steamuser4762");
        when(users.findByExternalSubject("sub-1")).thenReturn(Optional.of(existing));

        service.ensureAccount("sub-1", "Sir Pacster");
        assertEquals("SirPacster", created().getUsername());
    }

    @Test
    void aNumberedUsernameStaysStableAcrossSignIns() {
        Users self = account(1, "Alex2");
        when(users.findByExternalSubject("sub-1")).thenReturn(Optional.of(self));
        Users other = account(2, "Alex");
        when(users.findByUsername("Alex")).thenReturn(Optional.of(other));
        when(users.findByUsername("Alex2")).thenReturn(Optional.of(self));

        service.ensureAccount("sub-1", "Alex");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void losingARenameRaceKeepsTheCurrentName() {
        Users existing = new Users();
        existing.setUsername("Old_Name");
        when(users.findByExternalSubject("sub-1")).thenReturn(Optional.of(existing));
        when(users.saveAndFlush(any(Users.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertEquals("Old_Name", service.ensureAccount("sub-1", "New Name").getUsername());
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
        Users other = account(2, twenty);
        when(users.findByUsername(twenty)).thenReturn(Optional.of(other));
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
