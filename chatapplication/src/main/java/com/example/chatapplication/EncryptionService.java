package com.example.chatapplication;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BIT = 128;
    private static final int GCM_IV_LENGTH_BYTE = 12;

    @Value("${chat.encryption.key}")
    private String encryptionKeyStr;

    private SecretKey getSecretKey() {
        if (encryptionKeyStr == null || encryptionKeyStr.length() < 32) {
            throw new RuntimeException("Encryption key must be at least 32 characters long");
        }
        byte[] keyBytes = encryptionKeyStr.substring(0, 32).getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static class EncryptedData {
        private final byte[] iv;
        private final byte[] ciphertext;

        public EncryptedData(byte[] iv, byte[] ciphertext) {
            this.iv = iv;
            this.ciphertext = ciphertext;
        }

        public byte[] getIv() {
            return iv;
        }

        public byte[] getCiphertext() {
            return ciphertext;
        }
    }

    public EncryptedData encrypt(byte[] plaintext) throws Exception {
        if (plaintext == null) return null;

        byte[] iv = new byte[GCM_IV_LENGTH_BYTE];
        ThreadLocalRandom.current().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), gcmParameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext);
        return new EncryptedData(iv, ciphertext);
    }

    public byte[] decrypt(byte[] ciphertext, byte[] iv) throws Exception {
        if (ciphertext == null || iv == null) return null;

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), gcmParameterSpec);

        return cipher.doFinal(ciphertext);
    }
}
