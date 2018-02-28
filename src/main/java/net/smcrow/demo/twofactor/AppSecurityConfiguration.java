package net.smcrow.demo.twofactor;

import net.smcrow.demo.twofactor.user.StandardUserDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
public class AppSecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Autowired
    StandardUserDetailService standardUserDetailService;

    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception {
        // Webjar resources
        httpSecurity.authorizeRequests().antMatchers("/webjars/**").permitAll()
        .and().formLogin().loginPage("/login").permitAll()
        .and().logout().permitAll();
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder authenticationManagerBuilder) throws Exception {
        authenticationManagerBuilder.authenticationProvider(authenticationProvider());
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        // IMPORTANT: Not setting a password encoder here means that passwords will be stored in plain text.
        // This is _not secure_ but is being used for the sake of the demo.  Please read up on properly creating
        // a secure authentication provider before releasing to production.
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setUserDetailsService(standardUserDetailService);

        return daoAuthenticationProvider;
    }
}
