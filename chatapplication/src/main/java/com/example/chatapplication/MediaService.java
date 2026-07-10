package com.example.chatapplication;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class MediaService {

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private EncryptionService encryptionService;

    public Long uploadMedia(MultipartFile file, Long connectionId) {
        try {
            MediaStorageEntity entity = new MediaStorageEntity();
            entity.setMediaContentType(file.getContentType());
            entity.setConnectionId(connectionId);

            byte[] mediaBytes = file.getBytes();
            if (mediaBytes != null && mediaBytes.length > 0) {
                EncryptionService.EncryptedData encryptedMedia = encryptionService.encrypt(mediaBytes);
                entity.setMediaData(encryptedMedia.getCiphertext());
                entity.setMediaEncryptionIv(encryptedMedia.getIv());
            }

            MediaStorageEntity saved = mediaRepository.save(entity);
            return saved.getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload and encrypt media", e);
        }
    }

    public byte[] getDecryptedMedia(Long mediaId) {
        MediaStorageEntity entity = mediaRepository.findById(mediaId).orElse(null);
        if (entity == null || entity.getMediaData() == null) {
            return null;
        }
        try {
            return encryptionService.decrypt(entity.getMediaData(), entity.getMediaEncryptionIv());
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt media", e);
        }
    }
    
    @org.springframework.transaction.annotation.Transactional
    public MediaStorageEntity getMediaEntity(Long mediaId) {
        MediaStorageEntity entity = mediaRepository.findById(mediaId).orElse(null);
        if (entity != null) {
            // Initialize lazy media data to avoid LazyInitializationException outside transaction
            byte[] dummy = entity.getMediaData();
        }
        return entity;
    }
}
