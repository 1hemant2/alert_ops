package com.alertops.messaging;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import com.alertops.team.model.Invite;
import com.alertops.team.model.Team;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class TeamInvitationMailerTest {
    @SuppressWarnings("unchecked")
    @Test
    // Verifies team invitation copy uses the ReplyTrail public identity.
    void sendsReplyTrailInvitationCopy() throws Exception {
        ObjectProvider<JavaMailSender> senderProvider = mock(ObjectProvider.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(senderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        Invite invite = new Invite();
        invite.setEmail("user@example.com");
        invite.setRole("RESPONDER");
        invite.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        invite.setTtl(Duration.ofDays(3));
        Team team = new Team();
        team.setName("Platform Team");

        new TeamInvitationMailer(senderProvider, "no-reply@example.com")
                .sendInvitation(invite, team, "https://replytrail.example/join?token=test");

        verify(mailSender).send(mimeMessage);
        assertTrue(mimeMessage.getSubject().contains("ReplyTrail"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mimeMessage.writeTo(output);
        String rawMessage = output.toString(StandardCharsets.UTF_8);
        assertTrue(rawMessage.contains("Platform Team"));
        assertTrue(rawMessage.contains("REPLYTRAIL"));
        assertTrue(rawMessage.contains("ReplyTrail workspace"));
    }
}
