package com.example.chatapplication.SecurityConfigration;

import com.example.chatapplication.UserStatus;
import com.example.chatapplication.UserStatusRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class WebSocketEventListener {

    @Autowired
    private UserStatusRepo userStatusRepo;

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private org.springframework.messaging.simp.user.SimpUserRegistry simpUserRegistry;

    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final Map<String, Instant> pendingOffline = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            String userEmail = (String) sessionAttributes.get("userEmail");
            if (userEmail != null) {
                pendingOffline.remove(userEmail);

                String sessionId = headerAccessor.getSessionId();
                Set<String> sessions = userSessions.computeIfAbsent(userEmail, k -> new CopyOnWriteArraySet<>());
                boolean wasEmpty = sessions.isEmpty();
                if (sessionId != null) {
                    sessions.add(sessionId);
                }

                // Stale sessions cleanup using SimpUserRegistry
                if (simpUserRegistry != null) {
                    org.springframework.messaging.simp.user.SimpUser simpUser = simpUserRegistry.getUser(userEmail);
                    if (simpUser != null) {
                        Set<String> activeSessionIds = new HashSet<>();
                        for (org.springframework.messaging.simp.user.SimpSession session : simpUser.getSessions()) {
                            activeSessionIds.add(session.getId());
                        }
                        sessions.retainAll(activeSessionIds);
                    }
                }

                if (wasEmpty || sessions.isEmpty()) {
                    updateUserStatus(userEmail, true);
                } else {
                    updateUserStatus(userEmail, true);
                }
            }
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String userEmail = null;

        if (sessionId != null) {
            for (Map.Entry<String, Set<String>> entry : userSessions.entrySet()) {
                if (entry.getValue().remove(sessionId)) {
                    userEmail = entry.getKey();
                    break;
                }
            }
        }

        if (userEmail == null && headerAccessor.getSessionAttributes() != null) {
            userEmail = (String) headerAccessor.getSessionAttributes().get("userEmail");
            if (userEmail != null) {
                Set<String> sessions = userSessions.get(userEmail);
                if (sessions != null) {
                    if (sessionId != null) {
                        sessions.remove(sessionId);
                    }
                }
            }
        }

        if (userEmail != null) {
            Set<String> remaining = userSessions.get(userEmail);
            if (remaining == null || remaining.isEmpty()) {
                pendingOffline.put(userEmail, Instant.now());
            }
        }
    }

    @Scheduled(fixedRate = 15000)
    public void processPendingOffline() {
        Instant cutoff = Instant.now().minusSeconds(15);
        Set<String> toOffline = new HashSet<>();
        for (Map.Entry<String, Instant> entry : pendingOffline.entrySet()) {
            if (entry.getValue().isBefore(cutoff)) {
                toOffline.add(entry.getKey());
            }
        }
        for (String userEmail : toOffline) {
            pendingOffline.remove(userEmail);

            Set<String> remaining = userSessions.get(userEmail);
            if (remaining == null || remaining.isEmpty()) {
                userSessions.remove(userEmail);
                updateUserStatus(userEmail, false);
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupStaleSessions() {
        Set<String> toRemove = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : userSessions.entrySet()) {
            if (entry.getValue().isEmpty()) {
                toRemove.add(entry.getKey());
            }
        }
        for (String key : toRemove) {
            userSessions.remove(key);
            pendingOffline.remove(key);
        }
    }

    private void updateUserStatus(String userEmail, boolean isConnect) {
        UserStatus status = userStatusRepo.findById(userEmail).orElseGet(() -> {
            UserStatus newStatus = new UserStatus();
            newStatus.setUserEmail(userEmail);
            newStatus.setIsOnline(false);
            newStatus.setActiveConnections(0);
            return newStatus;
        });

        boolean wasOnline = status.getIsOnline() != null && status.getIsOnline();
        boolean changed = false;

        if (isConnect) {
            int count = userSessions.getOrDefault(userEmail, Set.of()).size();
            status.setActiveConnections(count);
            status.setIsOnline(true);
            if (!wasOnline) changed = true;
        } else {
            int remaining = userSessions.getOrDefault(userEmail, Set.of()).size();
            status.setActiveConnections(remaining);
            if (remaining == 0) {
                status.setIsOnline(false);
                status.setLastSeen(LocalDateTime.now());
                if (wasOnline) changed = true;
            }
        }

        userStatusRepo.save(status);

        if (changed) {
            Map<String, Object> statusMsg = new java.util.HashMap<>();
            statusMsg.put("email", userEmail);
            statusMsg.put("online", status.getIsOnline());
            statusMsg.put("lastSeen", status.getLastSeen() != null
                    ? status.getLastSeen().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : null);
            messagingTemplate.convertAndSend("/topic/status", (Object) statusMsg);
        }
    }
}
