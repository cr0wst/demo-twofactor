package net.smcrow.demo.twofactor.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class StandardUserDetailService implements UserDetailsService{
    private UserRepository userRepository;

    @Autowired
    public StandardUserDetailService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> match = userRepository.findByUsername(username);

        if (!match.isPresent()) {
            throw new UsernameNotFoundException("The requested username was not found.");
        }

        return new StandardUserDetails(match.get());
    }
}
