package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.identity.api.RegisterRequest;
import br.com.dnafutsal.backend.identity.application.RegistrationLifecycleHook;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import org.springframework.stereotype.Component;

@Component
public class CoachAccessRegistrationHook implements RegistrationLifecycleHook {
    private final CoachSharedAccessService access;
    public CoachAccessRegistrationHook(CoachSharedAccessService access){this.access=access;}
    @Override public void afterRegistration(UserAccount user, RegisterRequest request){
        String token=request.coachAccessToken();
        if(token!=null&&!token.isBlank()) access.claimForUser(user.getId(),token);
    }
}
