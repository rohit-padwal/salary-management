package com.acme.salarymanagement.security.config;

import com.acme.salarymanagement.security.exception.SecurityProblemWriter;
import com.acme.salarymanagement.security.filter.LoginContentTypeFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(HrAccountProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService hrAccount(HrAccountProperties account) {
        return new InMemoryUserDetailsManager(User.withUsername(account.username())
                .password(account.passwordHash()).roles("HR").build());
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(secure));
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository tokens,
            SecurityProblemWriter problems) throws Exception {
        http.authorizeHttpRequests(access -> access
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.spa().csrfTokenRepository(tokens))
                .addFilterBefore(new LoginContentTypeFilter(problems), UsernamePasswordAuthenticationFilter.class)
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) -> problems.authenticationRequired(request, response))
                        .accessDeniedHandler(problems::accessDenied))
                .formLogin(login -> login.loginPage("/api/v1/auth/login")
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, failure) -> problems.invalidCredentials(request, response)))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        return http.build();
    }
}
