package net.smcrow.demo.twofactor.verify;

import com.nexmo.client.NexmoClientException;
import com.nexmo.client.verify.VerifyClient;
import com.nexmo.client.verify.VerifyResult;
import net.smcrow.demo.twofactor.user.Role;
import net.smcrow.demo.twofactor.user.User;
import net.smcrow.demo.twofactor.user.UserRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;

@Service
public class NexmoVerificationService {
    private static final String APPLICATION_BRAND = "2FA Demo";
    private static final int EXPIRATION_INTERVALS = Calendar.MINUTE;
    private static final int EXPIRATION_INCREMENT = 5;
    @Autowired
    private VerificationRepository verificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerifyClient verifyClient;

    /**
     * Request verification code.
     * @throws VerificationRequestFailedException if there is an issue with the Nexmo service or we are
     * unable to generate a new code.
     */
    public Verification requestVerification(String phone) throws VerificationRequestFailedException {
        Optional<Verification> matches = verificationRepository.findByPhone(phone);
        if (matches.isPresent()) {
            return matches.get();
        }

        return generateAndSaveNewVerification(phone);
    }

    /**
     * Verify that the phone number and code are valid.
     * @throws VerificationRequestFailedException if there is an issue with the Nexmo service or we are
     * unable to generate a new code.
     */
    public boolean verify(String phone, String code) throws VerificationRequestFailedException {
        try {
            Verification verification = retrieveVerification(phone);
            if (verifyClient.check(verification.getRequestId(), code).getStatus() == 0) {
                verificationRepository.delete(phone);
                return true;
            }

            return false;
        } catch (VerificationNotFoundException e) {
            requestVerification(phone);
            return false;
        } catch (IOException | NexmoClientException e) {
            throw new VerificationRequestFailedException();
        }
    }

    private Verification retrieveVerification(String phone) throws VerificationNotFoundException {
        Optional<Verification> matches = verificationRepository.findByPhone(phone);
        if (matches.isPresent()) {
            return matches.get();
        }

        throw new VerificationNotFoundException();
    }

    private Verification generateAndSaveNewVerification(String phone) throws VerificationRequestFailedException {
        try {
            VerifyResult result = verifyClient.verify(phone, APPLICATION_BRAND);
            if (StringUtils.isBlank(result.getErrorText())) {
                String requestId = result.getRequestId();
                Calendar now = Calendar.getInstance();
                now.add(EXPIRATION_INTERVALS, EXPIRATION_INCREMENT);

                Verification verification = new Verification(phone, requestId, now.getTime());
                System.out.println("New Verification Registered: " + phone + " " + requestId + " " + now.getTime());
                return verificationRepository.save(verification);
            }
        } catch (IOException | NexmoClientException e) {
            throw new VerificationRequestFailedException();
        }

        throw new VerificationRequestFailedException();
    }


    public void updateAuthentication(Authentication authentication) {
        Role role = retrieveRoleFromDatabase(authentication.getName());
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(role);

        Authentication newAuthentication = new UsernamePasswordAuthenticationToken(
                authentication.getPrincipal(),
                authentication.getCredentials(),
                authorities
        );

        SecurityContextHolder.getContext().setAuthentication(newAuthentication);
    }

    private Role retrieveRoleFromDatabase(String username) {
        Optional<User> match = userRepository.findByUsername(username);
        if (match.isPresent()) {
            return match.get().getRole();
        }

        throw new UsernameNotFoundException("Username not found.");
    }
}
