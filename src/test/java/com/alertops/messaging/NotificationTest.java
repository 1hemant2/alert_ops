package com.alertops.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;
import java.util.UUID;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import com.alertops.flow_execution_engine.model.Escalation;
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
        state.setTaskPriority("P1");
        state.setTaskCategory("Platform <on-call>");
        state.setTaskReferenceUrl("https://example.com/requests/123?view=full&tab=alerts");
        Escalation escalation = new Escalation();
        escalation.setId(escalationId);
        escalation.setName("Production checkout failure");

        Notification notification = new Notification(senderProvider, "alerts@example.com");

        assertTrue(notification.sendEmail(
                state,
                escalation,
                Duration.ofMinutes(5),
                "https://alerts.example.com/acknowledge?token=sample-token",
                "https://alerts.example.com/escalate?token=escalate-token"));

        verify(mailSender).send(mimeMessage);
        Multipart parts = assertInstanceOf(Multipart.class, mimeMessage.getContent());
        assertEquals(2, parts.getCount());

        String markdown = parts.getBodyPart(0).getContent().toString();
        String html = parts.getBodyPart(1).getContent().toString();
        assertTrue(mimeMessage.getSubject().startsWith("ReplyTrail · "));
        assertTrue(markdown.contains("## Task"));
        assertTrue(markdown.contains("## Task details"));
        assertTrue(markdown.contains("ReplyTrail has activated a response workflow."));
        assertTrue(markdown.contains("Production checkout failure"));
        assertTrue(markdown.contains("**Response window:** 5 minutes"));
        assertTrue(markdown.contains("Automated notification from ReplyTrail."));
        assertTrue(markdown.contains("**backlog**"));
        assertTrue(markdown.contains("P1"));
        assertTrue(markdown.contains("Platform <on-call>"));
        assertTrue(markdown.contains("https://example.com/requests/123?view=full&tab=alerts"));
        assertTrue(markdown.contains(escalationId.toString()));
        assertTrue(markdown.contains("https://alerts.example.com/acknowledge?token=sample-token"));
        assertTrue(html.contains("<strong style="));
        assertTrue(html.contains("Task details"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertTrue(html.contains("P1"));
        assertTrue(html.contains("Platform &lt;on-call&gt;"));
        assertTrue(html.contains("https://example.com/requests/123?view=full&amp;tab=alerts"));
        assertTrue(html.contains("ESCALATION NOTIFICATION"));
        assertTrue(html.contains("REPLY<span style=\"color:#7ce7b2;\">TRAIL"));
        assertTrue(html.contains("background:#101828"));
        assertTrue(html.contains("background:#0b1220"));
        assertTrue(html.contains("max-width:560px"));
        assertTrue(html.contains("overflow-wrap:anywhere"));
        assertTrue(html.contains("background:#7ce7b2"));
        assertTrue(html.contains("Sent automatically by ReplyTrail."));
        assertTrue(html.contains("Acknowledge escalation"));
        assertTrue(html.contains("href=\"https://alerts.example.com/acknowledge?token=sample-token\""));
        assertTrue(html.contains("Escalate now and notify the next person"));
        assertTrue(html.contains("width:100%;"));
        assertTrue(!html.contains("<img src=x"));
    }
}
