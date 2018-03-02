package net.smcrow.demo.twofactor.verify;

import net.smcrow.demo.twofactor.user.StandardUserDetails;
import net.smcrow.demo.twofactor.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class VerificationController {
    @Autowired
    private NexmoVerificationService verificationService;

    @PreAuthorize("hasRole('PRE_VERIFICATION_USER')")
    @GetMapping("/verify")
    public String index() {
        return "verify";
    }

    @PreAuthorize("hasRole('PRE_VERIFICATION_USER')")
    @PostMapping("/verify")
    public String verify(@RequestParam("code") String code, Authentication authentication) {
        User user = ((StandardUserDetails) authentication.getPrincipal()).getUser();
        try {
            if (verificationService.verify(user.getPhone(), code)) {
                verificationService.updateAuthentication(authentication);
                return "redirect:/";
            }

            return "redirect:verify?error";
        } catch (VerificationRequestFailedException e) {
            // Having issues generating keys let them through.
            verificationService.updateAuthentication(authentication);
            return "redirect:/";
        }
    }
}
