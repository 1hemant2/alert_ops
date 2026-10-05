package com.alertops.messaging;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.alertops.flow_execution_engine.model.FlowExecutionState;

import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;

@Component
public class Notification {
    private static final Logger logger = LoggerFactory.getLogger(Notification.class);
    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    public Notification(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${alertops.email.from:}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
    }

    public boolean sendEmail(FlowExecutionState flowExecutionState, String acknowledgementUrl) {
        if (flowExecutionState == null || isBlank(flowExecutionState.getUserEmail())) {
            logger.warn("Email notification skipped because the recipient address is missing");
            return false;
        }
        if (isBlank(acknowledgementUrl)) {
            logger.warn("Email notification skipped because the acknowledgement link is missing");
            return false;
        }

        try {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                logger.warn("Email notification skipped because SMTP is not configured");
                return false;
            }
            if (isBlank(fromAddress)) {
                logger.warn("Email notification skipped because ALERTOPS_EMAIL_FROM is not configured");
                return false;
            }

            String taskDetails = Objects.toString(flowExecutionState.getTaskDetails(), "").trim();
            String taskName = sanitizeSubject(Objects.toString(flowExecutionState.getTaskName(), "").trim());
            String taskSource = sanitizeSubject(Objects.toString(flowExecutionState.getTaskSource(), "Manual").trim());
            if (taskSource.isEmpty()) {
                taskSource = "Manual";
            }
            String subjectDetails = taskName.isEmpty()
                    ? taskDetails.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim()
                    : taskName;
            if (subjectDetails.isEmpty()) {
                subjectDetails = taskName.isEmpty() ? "Response needed" : taskName;
            }
            if (subjectDetails.length() > 100) {
                subjectDetails = subjectDetails.substring(0, 97) + "...";
            }

            String markdown = buildMarkdown(flowExecutionState, taskName, taskSource, taskDetails, acknowledgementUrl);
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(flowExecutionState.getUserEmail().trim());
            helper.setSubject("AlertOps · " + taskSource + " · " + subjectDetails);

            var alternative = new MimeMultipart("alternative");
            var plainPart = new MimeBodyPart();
            plainPart.setText(markdown, StandardCharsets.UTF_8.name());
            alternative.addBodyPart(plainPart);

            var htmlPart = new MimeBodyPart();
            htmlPart.setContent(buildHtml(markdown, acknowledgementUrl), "text/html; charset=" + StandardCharsets.UTF_8.name());
            alternative.addBodyPart(htmlPart);
            message.setContent(alternative);

            mailSender.send(message);
            return true;
        } catch (Exception e) {
            logger.warn("Email notification failed for process {} ({})",
                    flowExecutionState.getProcessId(), e.getClass().getSimpleName());
            return false;
        }
    }

    /** Sends the durable alert created when a scheduled escalation cannot start. */
    public boolean sendStartFailureEmail(
            UUID escalationId,
            String escalationName,
            String recipientEmail,
            String reason) {
        if (isBlank(recipientEmail)) {
            logger.warn("Start-failure notification skipped because the recipient address is missing");
            return false;
        }

        try {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null || isBlank(fromAddress)) {
                logger.warn("Start-failure notification skipped because SMTP is not configured");
                return false;
            }

            String safeName = sanitizeSubject(Objects.toString(escalationName, "Escalation"));
            if (safeName.isBlank()) {
                safeName = "Escalation";
            }
            if (safeName.length() > 100) {
                safeName = safeName.substring(0, 97) + "...";
            }
            String safeReason = Objects.toString(reason, "SCHEDULED_START_FAILED").trim();
            if (safeReason.isBlank()) {
                safeReason = "SCHEDULED_START_FAILED";
            }

            String plainText = """
                    AlertOps could not start a scheduled escalation after all retry attempts.

                    Escalation: %s
                    Escalation ID: %s
                    Reason: %s

                    Review the escalation in AlertOps and follow your team's response procedure.
                    """.formatted(safeName, Objects.toString(escalationId, "unknown"), safeReason);
            String html = """
                    <!doctype html>
                    <html lang="en">
                    <body style="margin:0;padding:28px 12px;background:#f2f5f9;color:#314156;font-family:Arial,Helvetica,sans-serif;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                        <tr><td align="center">
                          <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="max-width:600px;background:#fff;border:1px solid #e3eaf1;border-radius:14px;overflow:hidden;">
                            <tr><td style="height:5px;background:#c0392b;font-size:0;">&nbsp;</td></tr>
                            <tr><td style="padding:22px 30px;background:#14283f;color:#fff;font-size:18px;font-weight:700;">Scheduled escalation failed to start</td></tr>
                            <tr><td style="padding:30px;font-size:15px;line-height:1.65;">
                              <p style="margin:0 0 16px;">AlertOps exhausted all start attempts for this scheduled escalation.</p>
                              <p style="margin:0 0 8px;"><strong>Escalation:</strong> %s</p>
                              <p style="margin:0 0 8px;"><strong>Escalation ID:</strong> %s</p>
                              <p style="margin:0;"><strong>Reason:</strong> %s</p>
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body>
                    </html>
                    """.formatted(
                    HtmlUtils.htmlEscape(safeName),
                    HtmlUtils.htmlEscape(Objects.toString(escalationId, "unknown")),
                    HtmlUtils.htmlEscape(safeReason));

            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(recipientEmail.trim());
            helper.setSubject("AlertOps · START_FAILED · " + safeName);
            helper.setText(plainText, html);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            logger.warn("Start-failure notification failed for escalation {} ({})",
                    escalationId, e.getClass().getSimpleName());
            return false;
        }
    }

    private String buildMarkdown(FlowExecutionState state, String taskName, String taskSource,
                                 String taskDetails, String acknowledgementUrl) {
        String details = taskDetails.isBlank() ? "No task details were provided." : taskDetails;
        String recipient = Objects.toString(state.getUserEmail(), "unknown");
        String escalationId = Objects.toString(state.getProcessId(), "unknown");
        return """
                # A response needs your attention

                AlertOps has activated a response workflow.

                ## Task

                - **Title:** %s
                - **Source:** %s

                %s

                ## Response reference

                - **Escalation ID:** %s
                - **Assigned user:** %s

                [Review and acknowledge this escalation](%s)

                Review this task and follow your team's response procedure.

                ---

                *Automated notification from AlertOps. Replies may not be monitored.*
                """.formatted(taskName.isBlank() ? "Response needed" : taskName, taskSource, details,
                        escalationId, recipient, acknowledgementUrl);
    }

    private String buildHtml(String markdown, String acknowledgementUrl) {
        String renderedMarkdown = HTML_RENDERER.render(MARKDOWN_PARSER.parse(markdown))
                .replace("<h1>", "<h1 style=\"margin:0 0 14px;color:#14283f;font-size:26px;line-height:1.25;\">")
                .replace("<h2>", "<h2 style=\"margin:26px 0 8px;color:#14283f;font-size:16px;line-height:1.4;\">")
                .replace("<p>", "<p style=\"margin:0 0 16px;line-height:1.65;\">")
                .replace("<ul>", "<ul style=\"margin:0 0 18px;padding-left:22px;line-height:1.7;\">")
                .replace("<li>", "<li style=\"padding-left:3px;margin:0 0 5px;\">")
                .replace("<strong>", "<strong style=\"color:#14283f;\">")
                .replace("<code>", "<code style=\"padding:2px 6px;border-radius:4px;background:#eef3f8;color:#193e5b;font-family:Menlo,Consolas,monospace;font-size:13px;\">")
                .replace("<hr />", "<hr style=\"margin:24px 0;border:0;border-top:1px solid #e3eaf1;\">");

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>AlertOps escalation notice</title>
                  <style>
                    @media only screen and (max-width: 600px) {
                      .email-card { width:100% !important; }
                      .email-content { padding:24px 20px !important; }
                    }
                  </style>
                </head>
                <body style="margin:0;padding:0;background:#f2f5f9;color:#314156;font-family:Arial,Helvetica,sans-serif;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="width:100%;background:#f2f5f9;padding:28px 12px;">
                    <tr><td align="center">
                      <table role="presentation" class="email-card" width="620" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:620px;background:#ffffff;border:1px solid #e3eaf1;border-radius:14px;overflow:hidden;">
                        <tr><td style="height:5px;background:#8acb3f;font-size:0;line-height:0;">&nbsp;</td></tr>
                        <tr><td style="padding:22px 30px;background:#14283f;color:#ffffff;">
                          <div style="font-size:13px;font-weight:700;letter-spacing:2px;">ALERTOPS</div>
                          <div style="margin-top:7px;color:#b8c7d8;font-size:11px;font-weight:700;letter-spacing:1.5px;">ESCALATION NOTIFICATION</div>
                        </td></tr>
                        <tr><td class="email-content" style="padding:32px 34px 24px;font-size:15px;">
                          <div style="margin-bottom:24px;padding:11px 14px;border:1px solid #f1d7a8;border-radius:8px;background:#fff8eb;color:#7b4a06;font-size:13px;font-weight:700;">
                            ACTION NEEDED &nbsp;·&nbsp; A response step is assigned to you
                          </div>
                          <div style="color:#314156;font-size:15px;line-height:1.65;">{{MARKDOWN_HTML}}</div>
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin-top:24px;"><tr><td bgcolor="#4b18f5" style="border-radius:8px;background:#4b18f5;">
                            <a href="{{ACKNOWLEDGEMENT_URL}}" style="display:inline-block;padding:14px 22px;border-radius:8px;color:#ffffff;font-size:15px;font-weight:700;text-decoration:none;">Acknowledge escalation</a>
                          </td></tr></table>
                        </td></tr>
                        <tr><td style="padding:16px 30px;border-top:1px solid #e8edf3;background:#f8fafc;color:#708095;font-size:12px;line-height:1.5;">
                          Sent automatically by AlertOps. Please use your team's usual incident response channel.
                        </td></tr>
                      </table>
                      <div style="padding:16px 8px;color:#8491a2;font-size:11px;">Reliable escalation, made explicit.</div>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.replace("{{ACKNOWLEDGEMENT_URL}}", HtmlUtils.htmlEscape(acknowledgementUrl))
                .replace("{{MARKDOWN_HTML}}", renderedMarkdown);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String sanitizeSubject(String value) {
        return value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
    }
}
