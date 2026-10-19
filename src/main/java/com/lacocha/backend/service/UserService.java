package com.lacocha.backend.service;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.lacocha.backend.dto.Auth.PasswordChange;
import com.lacocha.backend.dto.Auth.TemporaryPassword;
import com.lacocha.backend.dto.Auth.UserResponse;
import com.lacocha.backend.dto.Auth.UserUpdate;
import com.lacocha.backend.model.ServerClock;
import com.lacocha.backend.model.User;
import com.lacocha.backend.repository.UserRepository;

/**
 * The people of the farm, as the producer manages them.
 *
 * Nobody is asked for an email when they register, because several caretakers do not have one. That
 * leaves whoever forgets their password with no way back, so the way back is the one the farm
 * already uses: the producer resets it, says the temporary password out loud, and the caretaker
 * changes it the first time they sign in.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** No vowels and no 0/O/1/l: this gets read out loud across a pond and written down by hand. */
    private static final String ALPHABET = "bcdfghjkmnpqrstvwxyz23456789";
    private static final int TEMPORARY_LENGTH = 10;

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                .map(UserResponse::from)
                .toList();
    }

    /** Deactivating or promoting someone. The producer cannot do either one to themselves. */
    @Transactional
    public UserResponse update(UUID id, UserUpdate data, User producer) {
        User user = find(id);
        if (user.getId().equals(producer.getId())) {
            // Otherwise the only producer could lock the farm out of its own administration
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes cambiar tu propia cuenta");
        }
        if (data.role() != null) {
            user.setRole(data.role());
        }
        if (data.active() != null) {
            user.setActive(data.active());
        }
        return UserResponse.from(user);
    }

    /**
     * Hands the producer a temporary password for someone who forgot theirs. It travels once, in
     * this answer: the server keeps only its hash, like any other password.
     */
    @Transactional
    public TemporaryPassword resetPassword(UUID id, User producer) {
        User user = find(id);
        String temporary = randomPassword();
        user.setPasswordHash(encoder.encode(temporary));
        user.setMustChangePassword(true);
        user.setPasswordResetAt(ServerClock.now());
        user.setPasswordResetBy(producer.getId());
        // Written down because with this the producer can sign in as someone else
        log.info("password reset for {} by {}", user.getDocument(), producer.getDocument());
        return new TemporaryPassword(temporary, UserResponse.from(user));
    }

    /** Changing your own password, which is also how a temporary one stops being temporary. */
    @Transactional
    public void changePassword(User user, PasswordChange data) {
        if (!encoder.matches(data.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La clave actual no es correcta");
        }
        if (encoder.matches(data.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La clave nueva tiene que ser distinta de la actual");
        }
        User stored = find(user.getId());
        stored.setPasswordHash(encoder.encode(data.newPassword()));
        stored.setMustChangePassword(false);
    }

    private User find(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Esa persona no existe"));
    }

    private String randomPassword() {
        StringBuilder out = new StringBuilder(TEMPORARY_LENGTH);
        for (int i = 0; i < TEMPORARY_LENGTH; i++) {
            out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }
}
