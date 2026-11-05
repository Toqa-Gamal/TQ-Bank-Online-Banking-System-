package com.depi.bank;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    public static class Session {
        public final long userId;
        public final String role;
        volatile long lastSeen = System.currentTimeMillis();

        Session(long userId, String role) {
            this.userId = userId;
            this.role = role;
        }
    }

    private static final long TIMEOUT_MS = 15 * 60 * 1000L;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String create(long userId, String role) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(userId, role));
        return token;
    }

    public Session touch(String token) {
        Session s = sessions.get(token);
        if (s == null) return null;
        long now = System.currentTimeMillis();
        if (now - s.lastSeen > TIMEOUT_MS) {
            sessions.remove(token);
            return null;
        }
        s.lastSeen = now;
        return s;
    }

    public void remove(String token) {
        sessions.remove(token);
    }
}
