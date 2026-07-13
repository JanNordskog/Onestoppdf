package com.pdfsuite.auth;

import com.pdfsuite.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AuthController {

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 100) String password,
                                  @NotBlank String displayName) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record UserDto(UUID id, String email, String displayName) {
        public static UserDto from(AppUser u) {
            return new UserDto(u.getId(), u.getEmail(), u.getDisplayName());
        }
    }

    public record AuthResponse(String token, UserDto user) {}

    private final AppUserRepo users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;

    public AuthController(AppUserRepo users, PasswordEncoder passwordEncoder, JwtService jwt) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
    }

    @PostMapping("/auth/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw ApiException.badRequest("An account with that email already exists");
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName().trim());
        user = users.save(user);
        return new AuthResponse(jwt.issue(user.getId()), UserDto.from(user));
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser user = users.findByEmail(req.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return new AuthResponse(jwt.issue(user.getId()), UserDto.from(user));
    }

    @GetMapping("/me")
    public UserDto me() {
        AppUser user = users.findById(CurrentUser.idOrThrow())
                .orElseThrow(() -> ApiException.unauthorized("Sign in required"));
        return UserDto.from(user);
    }
}
