package com.alertops.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

import com.alertops.flow_execution_engine.model.Escalation;
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

    // Sends an escalation alert with context, response timing, and recipient actions.
    public boolean sendEmail(
            FlowExecutionState flowExecutionState,
            Escalation escalation,
            Duration responseWindow,
            String acknowledgementUrl,
            String escalateNowUrl) {
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
            String taskPriority = sanitizeMetadata(flowExecutionState.getTaskPriority());
            String taskCategory = sanitizeMetadata(flowExecutionState.getTaskCategory());
            String taskReferenceUrl = sanitizeMetadata(flowExecutionState.getTaskReferenceUrl());
            String escalationName = sanitizeSubject(
                    escalation == null ? "" : Objects.toString(escalation.getName(), ""));
            UUID escalationIdentifier = escalation == null || escalation.getId() == null
                    ? flowExecutionState.getProcessId()
                    : escalation.getId();
            String escalationId = Objects.toString(escalationIdentifier, "unknown");
            String responseWindowText = formatResponseWindow(responseWindow);
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

            String subjectContext = escalationName.isBlank() ? subjectDetails : escalationName + " · " + subjectDetails;
            if (subjectContext.length() > 100) {
                subjectContext = subjectContext.substring(0, 97) + "...";
            }
            String markdown = buildMarkdown(
                    flowExecutionState, escalationName, escalationId, responseWindowText, taskName, taskSource,
                    taskPriority, taskCategory, taskReferenceUrl, taskDetails, acknowledgementUrl, escalateNowUrl);
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(flowExecutionState.getUserEmail().trim());
            helper.setSubject("ReplyTrail · " + taskSource + " · " + subjectContext);

            var alternative = new MimeMultipart("alternative");
            var plainPart = new MimeBodyPart();
            plainPart.setText(markdown, StandardCharsets.UTF_8.name());
            alternative.addBodyPart(plainPart);

            var htmlPart = new MimeBodyPart();
            htmlPart.setContent(
                    buildHtml(markdown, acknowledgementUrl, escalateNowUrl),
                    "text/html; charset=" + StandardCharsets.UTF_8.name());
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
                    ReplyTrail could not start a scheduled escalation after all retry attempts.

                    Escalation: %s
                    Escalation ID: %s
                    Reason: %s

                    Review the escalation in ReplyTrail and follow your team's response procedure.
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
                              <p style="margin:0 0 16px;">ReplyTrail exhausted all start attempts for this scheduled escalation.</p>
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
            helper.setSubject("ReplyTrail · START_FAILED · " + safeName);
            helper.setText(plainText, html);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            logger.warn("Start-failure notification failed for escalation {} ({})",
                    escalationId, e.getClass().getSimpleName());
            return false;
        }
    }

    // Builds the plain-text action content for one alert email.
    private String buildMarkdown(
            FlowExecutionState state,
            String escalationName,
            String escalationId,
            String responseWindow,
            String taskName,
            String taskSource,
            String taskPriority,
            String taskCategory,
            String taskReferenceUrl,
            String taskDetails,
            String acknowledgementUrl,
            String escalateNowUrl) {
        String details = taskDetails.isBlank() ? "No task details were provided." : taskDetails;
        String recipient = Objects.toString(state.getUserEmail(), "unknown");
        String escalateNowAction = isBlank(escalateNowUrl)
                ? ""
                : "[Escalate now and notify the next person](%s)\n".formatted(escalateNowUrl);
        return """
                # A response needs your attention

                ReplyTrail has activated a response workflow.

                ## Escalation

                - **Name:** %s
                - **Escalation ID:** %s
                - **Response window:** %s

                ## Task

                - **Title:** %s
                - **Source:** %s
                - **Priority:** %s
                - **Category:** %s
                - **Reference URL:** %s

                ## Task details

                %s

                **Assigned user:** %s

                [Review and acknowledge this escalation](%s)

                %s

                Review this task and follow your team's response procedure.

                ---

                *Automated notification from ReplyTrail. Replies may not be monitored.*
                """.formatted(
                        escalationName.isBlank() ? "Unnamed escalation" : escalationName,
                        escalationId,
                        responseWindow,
                        taskName.isBlank() ? "Response needed" : taskName,
                        taskSource,
                        taskPriority,
                        taskCategory,
                        taskReferenceUrl,
                        details,
                        recipient,
                        acknowledgementUrl,
                        escalateNowAction);
    }

    // Builds the safe HTML action content for one alert email.
    private String buildHtml(String markdown, String acknowledgementUrl, String escalateNowUrl) {
        String renderedMarkdown = HTML_RENDERER.render(MARKDOWN_PARSER.parse(markdown))
                .replace("<h1>", "<h1 style=\"margin:0 0 14px;color:#edf5ff;font-size:26px;line-height:1.25;\">")
                .replace("<h2>", "<h2 style=\"margin:26px 0 8px;color:#dceafa;font-size:16px;line-height:1.4;\">")
                .replace("<p>", "<p style=\"margin:0 0 16px;line-height:1.65;\">")
                .replace("<ul>", "<ul style=\"margin:0 0 18px;padding-left:22px;line-height:1.7;\">")
                .replace("<li>", "<li style=\"padding-left:3px;margin:0 0 5px;\">")
                .replace("<strong>", "<strong style=\"color:#f4f8ff;\">")
                .replace("<a href=", "<a style=\"color:#8fe8b4;font-weight:700;text-decoration:none;word-break:break-word;overflow-wrap:anywhere;\" href=")
                .replace("<code>", "<code style=\"padding:2px 6px;border-radius:4px;background:#223650;color:#d8eaff;font-family:Menlo,Consolas,monospace;font-size:13px;\">")
                .replace("<hr />", "<hr style=\"margin:24px 0;border:0;border-top:1px solid #2a3b55;\">");

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>ReplyTrail escalation notice</title>
                  <style>
                    .email-card { box-shadow:0 22px 52px rgba(0,0,0,.35); }
                    .email-panel { background:#172740; }
                    @media only screen and (max-width: 600px) {
                      .email-card { width:100% !important; }
                      .email-content { padding:24px 20px !important; }
                      .email-panel-cell { padding:20px !important; }
                    }
                  </style>
                </head>
                <body style="margin:0;padding:0;background:#0b1220;color:#dbe7f4;font-family:Arial,Helvetica,sans-serif;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="width:100%;background:#0b1220;padding:40px 12px;">
                    <tr><td align="center">
                      <table role="presentation" class="email-card" width="560" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:560px;table-layout:fixed;background:#111c30;border:1px solid #2a3b55;border-radius:18px;overflow:hidden;">
                        <tr><td style="height:6px;background:#7ce7b2;font-size:0;line-height:0;">&nbsp;</td></tr>
                        <tr><td style="padding:26px 32px 28px;background:#101828;color:#ffffff;">
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                            <tr>
                              <td style="width:38px;vertical-align:middle;">
                                <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
                                  <td style="width:5px;height:23px;border-radius:4px;background:#7ce7b2;font-size:0;">&nbsp;</td>
                                  <td style="width:5px;height:31px;border-radius:4px;background:#a6f4c5;font-size:0;">&nbsp;</td>
                                  <td style="width:5px;height:18px;border-radius:4px;background:#7ce7b2;font-size:0;">&nbsp;</td>
                                </tr></table>
                              </td>
                              <td style="vertical-align:middle;">
                                <div style="font-size:14px;font-weight:800;letter-spacing:2.5px;">REPLY<span style="color:#7ce7b2;">TRAIL</span></div>
                                <div style="margin-top:7px;color:#b9c7d8;font-size:11px;font-weight:700;letter-spacing:1.4px;">ESCALATION NOTIFICATION</div>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td class="email-content" style="padding:28px 28px 24px;background:#111c30;font-size:15px;word-break:break-word;overflow-wrap:anywhere;">
                          <div style="display:block;margin-bottom:22px;padding:14px 16px;border:1px solid #276f5e;border-left:4px solid #7ce7b2;border-radius:10px;background:#133b34;color:#daf9e7;">
                            <div style="font-size:11px;font-weight:800;letter-spacing:1.3px;color:#8fe8b4;">ACTION NEEDED</div>
                            <div style="margin-top:5px;font-size:14px;font-weight:700;line-height:1.45;">A response step is assigned to you.</div>
                          </div>
                          <table role="presentation" class="email-panel" width="100%" cellpadding="0" cellspacing="0" border="0" style="width:100%;background:#172740;border:1px solid #2a3b55;border-radius:14px;">
                            <tr><td class="email-panel-cell" style="padding:22px 22px 16px;color:#dbe7f4;font-size:15px;line-height:1.65;word-break:break-word;overflow-wrap:anywhere;">{{MARKDOWN_HTML}}</td></tr>
                          </table>
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:24px;"><tr><td>
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr><td bgcolor="#7ce7b2" style="border-radius:10px;background:#7ce7b2;box-shadow:0 5px 14px rgba(124,231,178,.2);">
                              <a href="{{ACKNOWLEDGEMENT_URL}}" style="display:inline-block;padding:14px 22px;border-radius:10px;color:#10261c;font-size:15px;font-weight:800;text-decoration:none;">Acknowledge escalation</a>
                            </td></tr></table>
                          </td></tr></table>
                          {{ESCALATE_NOW_ACTION}}
                        </td></tr>
                        <tr><td style="padding:18px 28px;border-top:1px solid #263852;background:#0f1a2b;color:#90a4bd;font-size:12px;line-height:1.5;">
                          <span style="display:inline-block;width:7px;height:7px;margin-right:7px;border-radius:50%;background:#7ce7b2;">&nbsp;</span>Sent automatically by ReplyTrail. Please use your team's usual incident response channel.
                        </td></tr>
                      </table>
                      <div style="padding:18px 8px;color:#71849d;font-size:11px;letter-spacing:.2px;">Keep every response on track.</div>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.replace("{{ACKNOWLEDGEMENT_URL}}", HtmlUtils.htmlEscape(acknowledgementUrl))
                .replace("{{ESCALATE_NOW_ACTION}}", buildEscalateNowHtmlAction(escalateNowUrl))
                .replace("{{MARKDOWN_HTML}}", renderedMarkdown);
    }

    // Builds the optional HTML action without emitting an unusable empty link.
    private String buildEscalateNowHtmlAction(String escalateNowUrl) {
        if (isBlank(escalateNowUrl)) {
            return "";
        }
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:12px;\"><tr><td><a href=\""
                + HtmlUtils.htmlEscape(escalateNowUrl)
                + "\" style=\"display:inline-block;padding:11px 17px;border:1px solid #3a506e;border-radius:10px;color:#dbe7f4;background:#172740;font-size:14px;font-weight:700;text-decoration:none;\">Escalate now and notify the next person</a></td></tr></table>";
    }

    // Formats the saved response duration for a recipient-facing email sentence.
    private String formatResponseWindow(Duration responseWindow) {
        if (responseWindow == null || responseWindow.isNegative()) {
            return "Not specified";
        }
        long totalSeconds = responseWindow.getSeconds();
        if (totalSeconds == 0) {
            return "Immediately";
        }
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder formatted = new StringBuilder();
        appendDurationPart(formatted, days, "day");
        appendDurationPart(formatted, hours, "hour");
        appendDurationPart(formatted, minutes, "minute");
        if (formatted.isEmpty() || seconds > 0) {
            appendDurationPart(formatted, seconds, "second");
        }
        return formatted.toString();
    }

    // Adds one nonzero duration unit with readable singular and plural wording.
    private void appendDurationPart(StringBuilder formatted, long amount, String unit) {
        if (amount == 0) {
            return;
        }
        if (!formatted.isEmpty()) {
            formatted.append(' ');
        }
        formatted.append(amount).append(' ').append(unit);
        if (amount != 1) {
            formatted.append('s');
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // Normalizes optional task metadata before it is placed into an email line.
    private String sanitizeMetadata(String value) {
        String normalized = Objects.toString(value, "")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return normalized.isEmpty() ? "Not provided" : normalized;
    }

    private String sanitizeSubject(String value) {
        return value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
    }
}
