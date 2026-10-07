package com.alertops.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import com.alertops.flow_execution_engine.model.FlowExecutionState;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class NotificationTest {
    @SuppressWarnings("unchecked")
    @Test
    // Verifies alert emails retain safe plain-text and HTML action links.
    void sendsMarkdownFallbackAndStyledSafeHtmlAlternative() throws Exception {
        ObjectProvider<JavaMailSender> senderProvider = mock(ObjectProvider.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(senderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        UUID escalationId = UUID.fromString("7f000101-a0e3-1299-81a0-e303e5670000");
        FlowExecutionState state = new FlowExecutionState();
        state.setUserEmail("user@example.com");
        state.setProcessId(escalationId);
        state.setTaskDetails("Queue **backlog** <img src=x onerror=alert(1)>");

        Notification notification = new Notification(senderProvider, "alerts@example.com");

        assertTrue(notification.sendEmail(
                state,
                "https://alerts.example.com/acknowledge?token=sample-token",
                "https://alerts.example.com/escalate?token=escalate-token"));

        verify(mailSender).send(mimeMessage);
        Multipart parts = assertInstanceOf(Multipart.class, mimeMessage.getContent());
        assertEquals(2, parts.getCount());

        String markdown = parts.getBodyPart(0).getContent().toString();
        String html = parts.getBodyPart(1).getContent().toString();
        assertTrue(markdown.contains("## Task"));
        assertTrue(markdown.contains("**backlog**"));
        assertTrue(markdown.contains(escalationId.toString()));
        assertTrue(markdown.contains("https://alerts.example.com/acknowledge?token=sample-token"));
        assertTrue(html.contains("<strong style="));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertTrue(html.contains("ESCALATION NOTIFICATION"));
        assertTrue(html.contains("Acknowledge escalation"));
        assertTrue(html.contains("href=\"https://alerts.example.com/acknowledge?token=sample-token\""));
        assertTrue(html.contains("Escalate now and notify the next person"));
        assertTrue(html.contains("width:100%;"));
        assertTrue(!html.contains("<img src=x"));
    }
}
