package com.example.chatapplication.ResigtrationFolder;

import com.example.chatapplication.MediaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketChatController {

    @Autowired
    private Chatservicefile chatService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public static class ChatMessagePayload {
        private Long connectionId;
        private String message;
        private String messageType;
        private Long mediaId;
        private Long parentMessageId;

        public Long getConnectionId() { return connectionId; }
        public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getMessageType() { return messageType; }
        public void setMessageType(String messageType) { this.messageType = messageType; }

        public Long getMediaId() { return mediaId; }
        public void setMediaId(Long mediaId) { this.mediaId = mediaId; }

        public Long getParentMessageId() { return parentMessageId; }
        public void setParentMessageId(Long parentMessageId) { this.parentMessageId = parentMessageId; }
    }

    private String extractUserEmail(SimpMessageHeaderAccessor headerAccessor) {
        java.security.Principal userPrincipal = headerAccessor.getUser();
        if (userPrincipal != null) {
            return userPrincipal.getName();
        }
        if (headerAccessor.getSessionAttributes() != null) {
            return (String) headerAccessor.getSessionAttributes().get("userEmail");
        }
        return null;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessagePayload payload, SimpMessageHeaderAccessor headerAccessor) {
        String senderEmail = extractUserEmail(headerAccessor);
        if (senderEmail == null) {
            throw new RuntimeException("Unauthenticated user cannot send message");
        }

        String messageType = payload.getMessageType();
        if (messageType == null || messageType.isEmpty()) {
            messageType = "TEXT";
        }

        chatService.appendMessage(payload.getConnectionId(), senderEmail, payload.getMessage(), messageType, payload.getMediaId(), payload.getParentMessageId());
    }

    @MessageMapping("/chat.read")
    public void sendReadReceipt(@Payload java.util.Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String userEmail = extractUserEmail(headerAccessor);
        if (userEmail == null) return;

        Object connId = payload.get("connectionId");
        if (connId == null) return;

        java.util.Map<String, Object> receipt = new java.util.HashMap<>();
        receipt.put("type", "READ_RECEIPT");
        receipt.put("connectionId", connId);
        receipt.put("readBy", userEmail);
        messagingTemplate.convertAndSend("/topic/chat/" + connId, (Object) receipt);
    }

    @MessageMapping("/chat.typing")
    public void sendTypingStatus(@Payload java.util.Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String userEmail = extractUserEmail(headerAccessor);
        if (userEmail == null) return;

        Object connId = payload.get("connectionId");
        if (connId == null) return;

        Object isTyping = payload.get("isTyping");
        if (isTyping == null) return;

        java.util.Map<String, Object> typingEvent = new java.util.HashMap<>();
        typingEvent.put("type", "TYPING_STATUS");
        typingEvent.put("connectionId", connId);
        typingEvent.put("user", userEmail);
        typingEvent.put("isTyping", isTyping);

        messagingTemplate.convertAndSend("/topic/chat/" + connId, (Object) typingEvent);
    }

    @MessageMapping("/request.send")
    public void sendFriendRequest(@Payload java.util.Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String senderEmail = extractUserEmail(headerAccessor);
        if (senderEmail == null) return;

        String receiverEmail = (String) payload.get("receiverEmail");
        if (receiverEmail == null || receiverEmail.isEmpty()) return;

        java.util.Map<String, Object> confirm = new java.util.HashMap<>();
        confirm.put("type", "REQUEST_CONFIRM");

        try {
            chatService.sendRequest(senderEmail, receiverEmail);
            confirm.put("status", "SUCCESS");
            confirm.put("message", "Friend request sent!");
        } catch (Exception e) {
            confirm.put("status", "ERROR");
            confirm.put("message", e.getMessage());
        }

        messagingTemplate.convertAndSend("/topic/request.confirm/" + senderEmail, (Object) confirm);
    }

    @MessageMapping("/request.accept")
    public void acceptFriendRequest(@Payload java.util.Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String userEmail = extractUserEmail(headerAccessor);
        if (userEmail == null) return;

        Object notifIdObj = payload.get("notificationId");
        if (notifIdObj == null) return;

        Long notificationId;
        try {
            notificationId = Long.parseLong(notifIdObj.toString());
        } catch (NumberFormatException e) {
            java.util.Map<String, Object> errConfirm = new java.util.HashMap<>();
            errConfirm.put("type", "REQUEST_CONFIRM");
            errConfirm.put("status", "ERROR");
            errConfirm.put("message", "Invalid notification ID");
            messagingTemplate.convertAndSend("/topic/request.confirm/" + userEmail, (Object) errConfirm);
            return;
        }

        java.util.Map<String, Object> confirm = new java.util.HashMap<>();
        confirm.put("type", "REQUEST_CONFIRM");

        try {
            String result = chatService.acceptRequest(notificationId);
            confirm.put("status", result);

            if ("SUCCESS".equals(result)) {
                confirm.put("message", "Request accepted!");
                com.example.chatapplication.Notification notif = chatService.getNotificationById(notificationId);
                if (notif != null) {
                    String senderEmail = notif.getSenderEmail();
                    String receiverEmail = notif.getReceiverEmail();

                    java.util.Map<String, Object> friendUpdate = new java.util.HashMap<>();
                    friendUpdate.put("type", "FRIEND_LIST_UPDATE");
                    messagingTemplate.convertAndSend("/topic/friends/" + senderEmail, (Object) friendUpdate);
                    messagingTemplate.convertAndSend("/topic/friends/" + receiverEmail, (Object) friendUpdate);
                }
            } else {
                confirm.put("message", "Failed to accept request.");
            }
        } catch (Exception e) {
            confirm.put("status", "ERROR");
            confirm.put("message", e.getMessage());
        }

        messagingTemplate.convertAndSend("/topic/request.confirm/" + userEmail, (Object) confirm);
    }

    @MessageMapping("/request.reject")
    public void rejectFriendRequest(@Payload java.util.Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String userEmail = extractUserEmail(headerAccessor);
        if (userEmail == null) return;

        Object notifIdObj = payload.get("notificationId");
        if (notifIdObj == null) return;

        Long notificationId;
        try {
            notificationId = Long.parseLong(notifIdObj.toString());
        } catch (NumberFormatException e) {
            java.util.Map<String, Object> errConfirm = new java.util.HashMap<>();
            errConfirm.put("type", "REQUEST_CONFIRM");
            errConfirm.put("status", "ERROR");
            errConfirm.put("message", "Invalid notification ID");
            messagingTemplate.convertAndSend("/topic/request.confirm/" + userEmail, (Object) errConfirm);
            return;
        }

        java.util.Map<String, Object> confirm = new java.util.HashMap<>();
        confirm.put("type", "REQUEST_CONFIRM");

        try {
            String result = chatService.rejectRequest(notificationId);
            confirm.put("status", result);
            confirm.put("message", "SUCCESS".equals(result) ? "Request rejected!" : "Failed to reject request.");
        } catch (Exception e) {
            confirm.put("status", "ERROR");
            confirm.put("message", e.getMessage());
        }

        messagingTemplate.convertAndSend("/topic/request.confirm/" + userEmail, (Object) confirm);
    }
}
