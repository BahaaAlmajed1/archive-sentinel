package com.archivesentinel.config

import com.archivesentinel.domain.AdminUser
import com.archivesentinel.domain.AdminUserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class TokenStore {
    private val tokens = ConcurrentHashMap<String, String>()
    fun issue(username: String): String = UUID.randomUUID().toString().also { tokens[it] = username }
    fun username(token: String?): String? = token?.let(tokens::get)
}

@Component
class BearerTokenFilter(private val tokenStore: TokenStore) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val path = request.requestURI
        if (
            path.startsWith("/api/auth/login") ||
            path.startsWith("/api/setup/status") ||
            path.startsWith("/api/health") ||
            path.startsWith("/api/reports/artifacts/") ||
            path.startsWith("/api/paths/reveal") ||
            request.method.equals("OPTIONS", ignoreCase = true)
        ) {
            filterChain.doFilter(request, response)
            return
        }
        val token = request.getHeader("Authorization")?.removePrefix("Bearer ")?.trim()
        if (tokenStore.username(token) == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }
        filterChain.doFilter(request, response)
    }
}

@Configuration
class SecurityConfig(
    private val bearerTokenFilter: BearerTokenFilter,
    private val adminUserRepository: AdminUserRepository,
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: String,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/login", "/api/setup/status", "/api/health", "/api/reports/artifacts/**", "/api/paths/reveal").permitAll()
                    .anyRequest().permitAll()
            }
            .addFilterBefore(bearerTokenFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = allowedOrigins.split(",").map { it.trim() }.filter { it.isNotBlank() }
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun bootstrapAdmin(passwordEncoder: PasswordEncoder) = org.springframework.boot.ApplicationRunner {
        if (adminUserRepository.findByUsername("admin") == null) {
            adminUserRepository.save(
                AdminUser(
                    username = "admin",
                    passwordHash = passwordEncoder.encode("admin"),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        }
    }
}
