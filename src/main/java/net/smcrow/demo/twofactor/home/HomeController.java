package net.smcrow.demo.twofactor.home;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HomeController {

    @RequestMapping("/")
    @PreAuthorize("permitAll()")
    public String index() {
        return "home";
    }

    @RequestMapping("/secret")
    @PreAuthorize("hasRole('USER')")
    public String secret() {
        return "secret";
    }
}

