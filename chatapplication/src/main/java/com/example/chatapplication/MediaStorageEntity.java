package com.example.chatapplication;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class MediaStorageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Basic(fetch = jakarta.persistence.FetchType.LAZY)
    private byte[] mediaData;

    private byte[] mediaEncryptionIv;

    private String mediaContentType; // e.g. image/jpeg
    
    private Long connectionId; // To authorize downloads
}
