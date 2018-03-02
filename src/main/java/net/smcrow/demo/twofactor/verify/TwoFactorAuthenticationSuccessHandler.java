package net.smcrow.demo.twofactor.verify;

import net.smcrow.demo.twofactor.user.StandardUserDetails;
import net.smcrow.demo.twofactor.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class TwoFactorAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private static final String VERIFICATION_URL = "/verify";
    private static final String INDEX_URL = "/";

    @Autowired
    private NexmoVerificationService verificationService;

    @Autowired
    private UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String phone = ((StandardUserDetails) authentication.getPrincipal()).getUser().getPhone();
        if (phone == null || !requestAndRegisterVerification(phone)) {
            bypassVerification(request, response, authentication);
            return;
        }

        new DefaultRedirectStrategy().sendRedirect(request, response, VERIFICATION_URL);
    }

    private boolean requestAndRegisterVerification(String phone) {
        try {
            return verificationService.requestVerification(phone) != null;
        } catch (VerificationRequestFailedException e) {
            return false;
        }
    }

    private void bypassVerification(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        verificationService.updateAuthentication(authentication);
        new DefaultRedirectStrategy().sendRedirect(request, response, INDEX_URL);
    }
}
