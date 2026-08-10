package br.com.dnafutsal.backend.identity.api;

import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.application.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final CurrentUserService currentUser;
    private final ProfileService profiles;

    public ProfileController(CurrentUserService currentUser, ProfileService profiles) {
        this.currentUser = currentUser;
        this.profiles = profiles;
    }

    @GetMapping
    UserProfileResponse get() {
        return profiles.get(currentUser.userId());
    }

    @PutMapping
    UserProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(currentUser.userId(), request);
    }
}
