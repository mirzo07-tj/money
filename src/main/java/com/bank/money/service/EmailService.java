package com.bank.money.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.reset-password.frontend-url}")
    private String frontendUrl;
    public void sendEmailChangeConfirmation(String toNewEmail, String rawToken) {
        String link = "http://localhost:3000/confirm-email-change?token=" + rawToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toNewEmail);
        message.setSubject("Подтверждение смены email");
        message.setText("""
            Вы запросили смену email в личном кабинете.
            
            Перейдите по ссылке, чтобы подтвердить этот адрес как новый email:
            %s
            
            Ссылка действительна 30 минут. Если вы не запрашивали смену email — проигнорируйте это письмо.
            """.formatted(link));

        mailSender.send(message);
    }

    public void sendVerificationEmail(String toEmail, String rawToken) {
        String link = "http://localhost:3000/verify-email?token=" + rawToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Подтверждение email");
        message.setText("""
            Спасибо за регистрацию!
            
            Перейдите по ссылке, чтобы подтвердить email и активировать аккаунт:
            %s
            
            Ссылка действительна 30 минут.
            """.formatted(link));

        mailSender.send(message);
    }

    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        String link = frontendUrl + "?token=" + rawToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Восстановление пароля");
        message.setText("""
                Вы запросили восстановление пароля.
                
                Перейдите по ссылке, чтобы установить новый пароль:
                %s
                
                Ссылка действительна 30 минут. Если вы не запрашивали восстановление — проигнорируйте это письмо.
                """.formatted(link));

        mailSender.send(message);
    }
}