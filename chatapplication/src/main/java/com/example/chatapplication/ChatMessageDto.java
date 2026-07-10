package com.example.chatapplication;

import lombok.Data;

@Data
public class ChatMessageDto {
    private Long id;
    private String sender;
    private String senderName;
    private String timestamp;
    private String message;
    private String messageType;
    private String mediaData;
    private Boolean isDelivered;
}
