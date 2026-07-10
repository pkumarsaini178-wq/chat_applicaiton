package com.example.chatapplication;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "chat_entity", indexes = {
    @Index(name = "idx_connection_timestamp", columnList = "connectionId, timestamp"),
    @Index(name = "idx_sender", columnList = "sender"),
    @Index(name = "idx_delivered", columnList = "isDelivered")
})
@Data
public class ChatEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long connectionId;
    private String sender;

    private byte[] encryptedMessage;

    private byte[] encryptionIv;

    private Long mediaId;
    
    private String messageType;

    private java.time.LocalDateTime timestamp;

    private Boolean isDelivered = false;
    private java.time.LocalDateTime deliveredAt;

    private String deletedBy;
    private Long parentMessageId;
    private Boolean isEdited = false;
}
