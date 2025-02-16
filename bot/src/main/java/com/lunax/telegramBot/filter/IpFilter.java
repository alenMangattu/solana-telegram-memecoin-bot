package com.lunax.telegramBot.filter;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;

@Slf4j
@Component
public class IpFilter extends OncePerRequestFilter {

    private boolean isLocalhost(String remoteAddr) {
        return remoteAddr.equals("127.0.0.1") ||
                remoteAddr.equals("0:0:0:0:0:0:0:1") || // IPv6 localhost
                remoteAddr.equals("localhost") ||
                remoteAddr.equals("::1");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String remoteAddr = request.getRemoteAddr();
        log.info("Request from IP: {}", remoteAddr);

        // Allow only requests from localhost
        if (!isLocalhost(remoteAddr)) {
            log.warn("Rejected request from non-localhost IP: {}", remoteAddr);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Only localhost is allowed");
            return;
        }

        filterChain.doFilter(request, response);
    }
}