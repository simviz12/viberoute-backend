package com.routeoptimizer.security;

import com.routeoptimizer.service.JwtService;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("Token JWT requerido para WebSocket");
        }

        try {
            String jwt = authHeader.substring(7);
            String userEmail = jwtService.extractUsername(jwt);
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
            if (!jwtService.isTokenValid(jwt, userDetails)) {
                throw new AccessDeniedException("Token JWT invalido o expirado");
            }

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            accessor.setUser(authToken);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new AccessDeniedException("Autenticacion WebSocket fallida");
        }

        return message;
    }
}
