package com.loyiso.booking.common;

import com.loyiso.booking.booking.ManageTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards the admin API. With no ADMIN_API_KEY configured the endpoints answer 404, so they do not
 * advertise themselves. With a key, only a matching X-Admin-Key header is let through, compared as
 * hashes in constant time. It runs before the request body is read, so an unauthorised caller learns
 * nothing from validation errors.
 */
@Component
public class AdminKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Admin-Key";

    private final String keyHash;

    public AdminKeyInterceptor(@Value("${booking.admin.api-key:}") String key) {
        this.keyHash = key == null || key.isBlank() ? null : ManageTokens.hash(key);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (keyHash == null) {
            throw new ResourceNotFoundException("Not found");
        }
        if (!ManageTokens.matches(request.getHeader(HEADER), keyHash)) {
            throw new UnauthorizedException();
        }
        return true;
    }
}