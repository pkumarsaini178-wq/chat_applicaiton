package com.example.chatapplication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatConnectionRepo extends JpaRepository<ChatConnection, Long> {
    List<ChatConnection> findByUser1EmailOrUser2Email(String user1Email, String user2Email);
    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ChatConnection c WHERE (c.user1Email = :email1 AND c.user2Email = :email2) OR (c.user1Email = :email2 AND c.user2Email = :email1)")
    boolean connectionExists(@org.springframework.data.repository.query.Param("email1") String email1, @org.springframework.data.repository.query.Param("email2") String email2);
}
