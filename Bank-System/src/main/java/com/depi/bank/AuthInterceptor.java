package com.depi.bank;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {
    private final SessionStore sessions;

    public AuthInterceptor(SessionStore sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if ("OPTIONS".equals(req.getMethod())) return true;
        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) {
            throw new ApiException(401, "Authentication required.");
        }
        String token = h.substring(7).trim();
        SessionStore.Session s = sessions.touch(token);
        if (s == null) {
            throw new ApiException(401, "Your session has expired. Please log in again.");
        }
        req.setAttribute("userId", s.userId);
        req.setAttribute("role", s.role);
        return true;
    }
}
