package com.example.chatapplication.SecurityConfigration;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;

@Component
public class JwtUntil {

    @Value("${jwt.secret}")
    private String secrectKey;
    @Value("${jwt.expiry}")
    private Long expiry;

    private Key getsigineky() {
        return Keys.hmacShaKeyFor(secrectKey.getBytes());
    }

    public String gunrateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(getsigineky(), SignatureAlgorithm.HS256)
                .compact();

    }

    public String FechEmailfromToke(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getsigineky())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean Token_is_vailid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getsigineky())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            System.out.println("error is " + e.getMessage());
            return false;
        }
    }

}
