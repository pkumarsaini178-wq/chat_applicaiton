package com.example.chatapplication.ResigtrationFolder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.chatapplication.ChatClearStatus;
import com.example.chatapplication.ChatClearStatusRepo;
import com.example.chatapplication.ChatEntity;
import com.example.chatapplication.ChatRepository;
import com.example.chatapplication.UserStatus;
import com.example.chatapplication.UserStatusRepo;

import java.util.List;
import java.util.Optional;

@Service
public class Chatservicefile {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ChatSinginRepo chatSinginRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public List<ChatEntity> getAllMessages() {
        return chatRepository.findAll();
    }

    public ChatEntity saveMessage(ChatEntity message) {
        return chatRepository.save(message);
    }

    public ChatSingin registerUser(ChatSingin user) {
        return chatSinginRepo.save(user);
    }

    public ChatSingin loginUser(String useremail, String password) {
        Optional<ChatSingin> user = chatSinginRepo.findByuseremail(useremail);
        if (user.isPresent() && passwordEncoder.matches(password, user.get().getPassword())) {
            return user.get();
        }
        return null;
    }

    public List<ChatSingin> getAllUsers() {
        return chatSinginRepo.findAll();
    }

    @Autowired
    private com.example.chatapplication.NotificationRepo notificationRepo;

    @Autowired
    private com.example.chatapplication.ChatConnectionRepo chatConnectionRepo;

    @Autowired
    private ChatClearStatusRepo chatClearStatusRepo;

    @Autowired
    private UserStatusRepo userStatusRepo;

    @Transactional
    public com.example.chatapplication.Notification sendRequest(String senderEmail, String receiverEmail) {
        if (chatConnectionRepo.connectionExists(senderEmail, receiverEmail)) {
            throw new RuntimeException("You are already friends");
        }
        if (notificationRepo.existsBySenderEmailAndReceiverEmailAndStatus(senderEmail, receiverEmail, "PENDING")) {
            throw new RuntimeException("Request already sent");
        }
        if (notificationRepo.existsBySenderEmailAndReceiverEmailAndStatus(receiverEmail, senderEmail, "PENDING")) {
            throw new RuntimeException("They have already sent you a request");
        }

        com.example.chatapplication.Notification notification = new com.example.chatapplication.Notification();
        notification.setSenderEmail(senderEmail);
        notification.setReceiverEmail(receiverEmail);
        notification.setStatus("PENDING");
        com.example.chatapplication.Notification saved = notificationRepo.save(notification);

        java.util.Map<String, Object> notifMsg = new java.util.HashMap<>();
        notifMsg.put("type", "FRIEND_REQUEST");
        notifMsg.put("id", saved.getId());
        notifMsg.put("senderEmail", senderEmail);
        notifMsg.put("receiverEmail", receiverEmail);
        notifMsg.put("status", "PENDING");
        messagingTemplate.convertAndSend("/topic/notifications/" + receiverEmail, (Object) notifMsg);

        return saved;
    }

    public List<com.example.chatapplication.Notification> getPendingNotifications(String receiverEmail) {
        return notificationRepo.findByReceiverEmailAndStatus(receiverEmail, "PENDING");
    }

    @Transactional
    public String acceptRequest(Long notificationId) {
        Optional<com.example.chatapplication.Notification> opt = notificationRepo.findById(notificationId);
        if (opt.isPresent()) {
            com.example.chatapplication.Notification notif = opt.get();
            notif.setStatus("ACCEPTED");
            notificationRepo.save(notif);

            com.example.chatapplication.ChatConnection connection = new com.example.chatapplication.ChatConnection();
            connection.setUser1Email(notif.getSenderEmail());
            connection.setUser2Email(notif.getReceiverEmail());
            chatConnectionRepo.save(connection);

            java.util.Map<String, Object> notifMsg = new java.util.HashMap<>();
            notifMsg.put("type", "REQUEST_ACCEPTED");
            notifMsg.put("acceptedBy", notif.getReceiverEmail());
            notifMsg.put("acceptedByName", notif.getReceiverEmail());
            messagingTemplate.convertAndSend("/topic/notifications/" + notif.getSenderEmail(), (Object) notifMsg);

            return "SUCCESS";
        }
        return "FAILED";
    }

    @Transactional
    public String rejectRequest(Long notificationId) {
        Optional<com.example.chatapplication.Notification> opt = notificationRepo.findById(notificationId);
        if (opt.isPresent()) {
            com.example.chatapplication.Notification notif = opt.get();
            notif.setStatus("REJECTED");
            notificationRepo.save(notif);
            return "SUCCESS";
        }
        return "FAILED";
    }

    public List<com.example.chatapplication.ChatConnection> getFriends(String email) {
        return chatConnectionRepo.findByUser1EmailOrUser2Email(email, email);
    }

    @Autowired
    private com.example.chatapplication.EncryptionService encryptionService;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("messageExecutor")
    private org.springframework.core.task.TaskExecutor messageExecutor;

    public void appendMessage(Long connectionId, String sender, String message, String messageType, Long mediaId) {
        // Phase 1: Publish plaintext immediately (FAST - no encryption, no DB)
        com.example.chatapplication.ChatMessageDto fastDto = new com.example.chatapplication.ChatMessageDto();
        fastDto.setId(-1L);
        fastDto.setSender(sender);
        Optional<ChatSingin> senderOpt = chatSinginRepo.findByuseremail(sender);
        fastDto.setSenderName(senderOpt.isPresent() && senderOpt.get().getUsername() != null ? senderOpt.get().getUsername() : sender);
        fastDto.setMessage(message);
        fastDto.setMessageType(messageType);
        fastDto.setMediaData(mediaId != null ? "/api/chat/media/" + mediaId : null);
        fastDto.setTimestamp(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        fastDto.setIsDelivered(false);
        messagingTemplate.convertAndSend("/topic/chat/" + connectionId, fastDto);

        // Notify receiver via personal notification channel
        Optional<com.example.chatapplication.ChatConnection> connForNotif = chatConnectionRepo.findById(connectionId);
        if (connForNotif.isPresent()) {
            com.example.chatapplication.ChatConnection conn = connForNotif.get();
            String receiverEmail = conn.getUser1Email().equals(sender) ? conn.getUser2Email() : conn.getUser1Email();
            java.util.Map<String, Object> newMsgNotif = new java.util.HashMap<>();
            newMsgNotif.put("type", "NEW_MESSAGE");
            newMsgNotif.put("connectionId", connectionId);
            newMsgNotif.put("sender", sender);
            messagingTemplate.convertAndSend("/topic/notifications/" + receiverEmail, (Object) newMsgNotif);
        }

        // Phase 2: Encrypt + save to DB + deliver in background
        messageExecutor.execute(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    try {
                        ChatEntity entity = new ChatEntity();
                        entity.setConnectionId(connectionId);
                        entity.setSender(sender);
                        entity.setMessageType(messageType);
                        entity.setMediaId(mediaId);
                        entity.setTimestamp(java.time.LocalDateTime.now());

                        byte[] messageBytes = message != null ? message.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                : new byte[0];
                        com.example.chatapplication.EncryptionService.EncryptedData encryptedMsgData = encryptionService
                                .encrypt(messageBytes);
                        entity.setEncryptedMessage(encryptedMsgData.getCiphertext());
                        entity.setEncryptionIv(encryptedMsgData.getIv());

                        Optional<com.example.chatapplication.ChatConnection> connOpt = chatConnectionRepo.findById(connectionId);
                        if (connOpt.isPresent()) {
                            com.example.chatapplication.ChatConnection conn = connOpt.get();
                            String recipient = conn.getUser1Email().equals(sender) ? conn.getUser2Email() : conn.getUser1Email();
                            Optional<UserStatus> recipientStatus = userStatusRepo.findById(recipient);
                            if (recipientStatus.isPresent() && Boolean.TRUE.equals(recipientStatus.get().getIsOnline())
                                    && recipientStatus.get().getActiveConnections() > 0) {
                                entity.setIsDelivered(true);
                                entity.setDeliveredAt(java.time.LocalDateTime.now());
                            }
                        }

                        ChatEntity savedEntity = chatRepository.save(entity);
                        long realId = savedEntity.getId();
                        boolean isDelivered = savedEntity.getIsDelivered() != null && savedEntity.getIsDelivered();

                        // Publish real ID + delivery update
                        java.util.Map<String, Object> update = new java.util.HashMap<>();
                        update.put("type", "MESSAGE_SAVED");
                        update.put("connectionId", connectionId);
                        update.put("realId", realId);
                        update.put("isDelivered", isDelivered);

                        if (isDelivered) {
                            java.util.Map<String, Object> receipt = new java.util.HashMap<>();
                            receipt.put("type", "DELIVERY_RECEIPT");
                            receipt.put("messageId", realId);
                            receipt.put("connectionId", connectionId);
                            messagingTemplate.convertAndSend("/topic/chat/" + connectionId, (Object) receipt);
                        }

                        messagingTemplate.convertAndSend("/topic/chat/" + connectionId, (Object) update);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(getClass().getName())
                    .severe("Background message save failed: " + e.getMessage());
            }
        });
    }

    private static final int PAGE_SIZE = 50;

    public List<com.example.chatapplication.ChatMessageDto> getChatHistory(Long connectionId, String currentUserEmail) {
        return getChatHistory(connectionId, currentUserEmail, 0);
    }

    public List<com.example.chatapplication.ChatMessageDto> getChatHistory(Long connectionId, String currentUserEmail, int page) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, PAGE_SIZE);
        List<ChatEntity> entities = chatRepository.findByConnectionIdOrderByTimestampDesc(connectionId, pageable);
        java.util.Collections.reverse(entities);

        List<com.example.chatapplication.ChatMessageDto> dtoList = new java.util.ArrayList<>();

        java.util.Map<String, String> nameCache = new java.util.HashMap<>();

        Optional<ChatClearStatus> clearStatus = chatClearStatusRepo.findByConnectionIdAndUserEmail(connectionId, currentUserEmail);
        java.time.LocalDateTime clearedAt = clearStatus.isPresent() ? clearStatus.get().getClearedAt() : null;

        List<ChatEntity> markDelivered = new java.util.ArrayList<>();
        boolean hasNewDeliveries = false;

        for (ChatEntity entity : entities) {
            if (clearedAt != null && entity.getTimestamp() != null && !entity.getTimestamp().isAfter(clearedAt)) {
                continue;
            }
            dtoList.add(mapToDto(entity, nameCache));

            if (!entity.getSender().equals(currentUserEmail)
                    && (entity.getIsDelivered() == null || !entity.getIsDelivered())) {
                entity.setIsDelivered(true);
                entity.setDeliveredAt(java.time.LocalDateTime.now());
                markDelivered.add(entity);
                hasNewDeliveries = true;
            }
        }

        if (!markDelivered.isEmpty()) {
            chatRepository.saveAll(markDelivered);
        }

        if (hasNewDeliveries) {
            java.util.Map<String, Object> receipt = new java.util.HashMap<>();
            receipt.put("type", "DELIVERY_RECEIPT");
            receipt.put("connectionId", connectionId);
            messagingTemplate.convertAndSend("/topic/chat/" + connectionId, (Object) receipt);

            java.util.Map<String, Object> readReceipt = new java.util.HashMap<>();
            readReceipt.put("type", "READ_RECEIPT");
            readReceipt.put("connectionId", connectionId);
            readReceipt.put("readBy", currentUserEmail);
            messagingTemplate.convertAndSend("/topic/chat/" + connectionId, (Object) readReceipt);
        }

        return dtoList;
    }

    private com.example.chatapplication.ChatMessageDto mapToDto(ChatEntity entity,
            java.util.Map<String, String> nameCache) {
        com.example.chatapplication.ChatMessageDto dto = new com.example.chatapplication.ChatMessageDto();
        dto.setId(entity.getId());
        String senderEmail = entity.getSender();
        dto.setSender(senderEmail);

        if (nameCache != null && !nameCache.containsKey(senderEmail)) {
            Optional<ChatSingin> userOpt = chatSinginRepo.findByuseremail(senderEmail);
            if (userOpt.isPresent() && userOpt.get().getUsername() != null) {
                nameCache.put(senderEmail, userOpt.get().getUsername());
            } else {
                nameCache.put(senderEmail, senderEmail);
            }
        }

        if (nameCache != null) {
            dto.setSenderName(nameCache.get(senderEmail));
        } else {
            Optional<ChatSingin> userOpt2 = chatSinginRepo.findByuseremail(senderEmail);
            dto.setSenderName(userOpt2.isPresent() && userOpt2.get().getUsername() != null ? userOpt2.get().getUsername()
                    : senderEmail);
        }

        dto.setTimestamp(entity.getTimestamp() != null
                ? entity.getTimestamp().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);
        dto.setMessageType(entity.getMessageType());

        try {
            if (entity.getEncryptedMessage() != null && entity.getEncryptionIv() != null) {
                byte[] decryptedMsg = encryptionService.decrypt(entity.getEncryptedMessage(),
                        entity.getEncryptionIv());
                dto.setMessage(new String(decryptedMsg, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            dto.setMessage("[Message could not be decrypted]");
        }

        // Expose media URL from Asset Store
        if (entity.getMediaId() != null) {
            dto.setMediaData("/api/chat/media/" + entity.getMediaId());
        }

        dto.setIsDelivered(entity.getIsDelivered());

        return dto;
    }

    @Transactional
    public ChatEntity getMessageById(Long messageId) {
        return chatRepository.findById(messageId).orElse(null);
    }

    @Transactional
    public com.example.chatapplication.Notification getNotificationById(Long notificationId) {
        return notificationRepo.findById(notificationId).orElse(null);
    }

    @Transactional
    public void deleteChatHistory(Long connectionId, String timeframe) {
        String currentUser = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<ChatClearStatus> existing = chatClearStatusRepo.findByConnectionIdAndUserEmail(connectionId, currentUser);
        ChatClearStatus status;
        if (existing.isPresent()) {
            status = existing.get();
        } else {
            status = new ChatClearStatus();
            status.setConnectionId(connectionId);
            status.setUserEmail(currentUser);
        }
        if ("1DAY".equals(timeframe)) {
            status.setClearedAt(java.time.LocalDateTime.now().minusDays(1));
        } else if ("7DAYS".equals(timeframe)) {
            status.setClearedAt(java.time.LocalDateTime.now().minusDays(7));
        } else {
            status.setClearedAt(java.time.LocalDateTime.now());
        }
        chatClearStatusRepo.save(status);
    }

    @Transactional
    public void clearChatHistory(Long connectionId, String userEmail) {
        Optional<ChatClearStatus> existing = chatClearStatusRepo.findByConnectionIdAndUserEmail(connectionId, userEmail);
        ChatClearStatus status;
        if (existing.isPresent()) {
            status = existing.get();
        } else {
            status = new ChatClearStatus();
            status.setConnectionId(connectionId);
            status.setUserEmail(userEmail);
        }
        status.setClearedAt(java.time.LocalDateTime.now());
        chatClearStatusRepo.save(status);
    }

    public void clearChatHistory(Long connectionId) {
        String currentUser = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        clearChatHistory(connectionId, currentUser);
    }
}
