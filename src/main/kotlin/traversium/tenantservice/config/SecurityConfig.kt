package traversium.tenantservice.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/rest/**").permitAll()
                    .requestMatchers(
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/swagger-ui.html"
                    ).permitAll()
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/actuator/prometheus/**").permitAll()
                    .requestMatchers("/actuator/prometheus").permitAll()
                    .anyRequest().permitAll()
            }
        return http.build()
    }
}

