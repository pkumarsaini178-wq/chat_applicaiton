package com.example.chatapplication;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    public void sendOtp(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setSubject("OTP verification for ChatApp");
        message.setTo(toEmail);
        message.setText(otp + " is your OTP for ChatApp password reset. It is valid for 10 minutes.");
        javaMailSender.send(message);
    }
}
