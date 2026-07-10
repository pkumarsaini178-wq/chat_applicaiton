package com.example.chatapplication;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class UserStatus {
    @Id
    private String userEmail;
    private Boolean isOnline = false;
    private LocalDateTime lastSeen;
    private Integer activeConnections = 0;
}
