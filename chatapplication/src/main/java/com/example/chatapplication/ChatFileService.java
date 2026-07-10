package com.example.chatapplication;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatFileService {

    private final String CHAT_DIR = "chats";
    private final String SEPARATOR = "|||";
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public ChatFileService() {
        try {
            Files.createDirectories(Paths.get(CHAT_DIR));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Path getFilePath(Long connectionId) {
        return Paths.get(CHAT_DIR, "chat_" + connectionId + ".txt");
    }

    public void appendMessage(Long connectionId, String sender, String message) {
        Path path = getFilePath(connectionId);
        String timestamp = LocalDateTime.now().format(formatter);
        // Replace line breaks in message to keep it on one line if needed, or encode
        String safeMessage = message.replace("\n", "\\n").replace("\r", "");
        String line = sender + SEPARATOR + timestamp + SEPARATOR + safeMessage + System.lineSeparator();
        
        try {
            Files.write(path, line.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.example.chatapplication.ResigtrationFolder.ChatSinginRepo chatSinginRepo;

    public List<ChatMessageDto> getChatHistory(Long connectionId) {
        Path path = getFilePath(connectionId);
        List<ChatMessageDto> history = new ArrayList<>();

        if (!Files.exists(path)) {
            return history;
        }

        java.util.Map<String, String> nameCache = new java.util.HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|\\|\\|", 3);
                if (parts.length == 3) {
                    String senderEmail = parts[0];
                    ChatMessageDto dto = new ChatMessageDto();
                    dto.setSender(senderEmail);
                    
                    if (!nameCache.containsKey(senderEmail)) {
                        java.util.Optional<com.example.chatapplication.ResigtrationFolder.ChatSingin> userOpt = chatSinginRepo.findByuseremail(senderEmail);
                        if (userOpt.isPresent() && userOpt.get().getUsername() != null) {
                            nameCache.put(senderEmail, userOpt.get().getUsername());
                        } else {
                            nameCache.put(senderEmail, senderEmail);
                        }
                    }
                    dto.setSenderName(nameCache.get(senderEmail));

                    dto.setTimestamp(parts[1]);
                    dto.setMessage(parts[2].replace("\\n", "\n"));
                    history.add(dto);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return history;
    }
}
