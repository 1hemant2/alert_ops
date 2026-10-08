package com.alertops.messaging;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import com.alertops.auth.model.User;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class EmailVerificationMailerTest {
    @SuppressWarnings("unchecked")
    @Test
    // Verifies account email copy uses the ReplyTrail public identity.
    void sendsReplyTrailVerificationCopy() throws Exception {
        ObjectProvider<JavaMailSender> senderProvider = mock(ObjectProvider.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(senderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        User user = new User();
        user.setEmail("user@example.com");
        new EmailVerificationMailer(senderProvider, "no-reply@example.com")
                .sendVerificationEmail(user, "https://replytrail.example/verify?token=test");

        verify(mailSender).send(mimeMessage);
        assertTrue(mimeMessage.getSubject().contains("ReplyTrail"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mimeMessage.writeTo(output);
        String rawMessage = output.toString(StandardCharsets.UTF_8);
        assertTrue(rawMessage.contains("ReplyTrail email"));
        assertTrue(rawMessage.contains("Verify your ReplyTrail email"));
        assertTrue(rawMessage.contains("ReplyTrail account"));
    }
}
