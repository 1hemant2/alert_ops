package com.alertops.messaging;

import com.alertops.team.model.Invite;
import com.alertops.team.model.Team;
import com.alertops.team.service.InviteMailDeliveryException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class TeamInvitationMailer {
    private static final Logger logger = LoggerFactory.getLogger(TeamInvitationMailer.class);
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter
            .ofPattern("MMM d, yyyy 'at' h:mm a z")
            .withZone(ZoneId.of("UTC"));

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    public TeamInvitationMailer(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${alertops.email.from:}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
    }

    // Sends a team invitation with a one-time acceptance link.
    public void sendInvitation(Invite invite, Team team, String invitationUrl) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || fromAddress.isBlank()) {
            throw new InviteMailDeliveryException();
        }

        try {
            String teamName = team.getName();
            String role = invite.getRole().replace('_', ' ').toLowerCase();
            String expiresAt = EXPIRY_FORMAT.format(invite.getCreatedAt().plus(invite.getTtl()));
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(invite.getEmail());
            helper.setSubject("You're invited to " + safeSubject(teamName) + " on ReplyTrail");
            helper.setText(
                    "You've been invited to join " + teamName + " as a " + role + ".\n\n"
                            + "Accept this invitation before " + expiresAt + ":\n" + invitationUrl
                            + "\n\nIf you weren't expecting this invitation, you can ignore this email.",
                    """
                            <!doctype html>
                            <html lang="en">
                            <body style="margin:0;padding:28px 12px;background:#f2f5f9;color:#314156;font-family:Arial,Helvetica,sans-serif;">
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                                <tr><td align="center">
                                  <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="max-width:600px;background:#fff;border:1px solid #e3eaf1;border-radius:14px;overflow:hidden;">
                                    <tr><td style="height:5px;background:#8acb3f;font-size:0;">&nbsp;</td></tr>
                                    <tr><td style="padding:22px 30px;background:#14283f;color:#fff;font-weight:700;letter-spacing:2px;">REPLYTRAIL</td></tr>
                                    <tr><td style="padding:32px 34px;">
                                      <p style="margin:0 0 8px;color:#718196;font-size:12px;font-weight:700;letter-spacing:1px;">TEAM INVITATION</p>
                                      <h1 style="margin:0 0 16px;color:#14283f;font-size:26px;">You're invited to %s</h1>
                                      <p style="font-size:15px;line-height:1.65;">You've been invited to join this ReplyTrail workspace as a <strong>%s</strong>.</p>
                                      <p style="margin:24px 0;">
                                        <a href="%s" style="display:inline-block;padding:13px 20px;border-radius:8px;background:#14283f;color:#fff;text-decoration:none;font-weight:700;">Accept invitation</a>
                                      </p>
                                      <p style="color:#718196;font-size:13px;line-height:1.6;">This invitation expires on %s UTC. If you weren't expecting it, you can ignore this email.</p>
                                    </td></tr>
                                    <tr><td style="padding:16px 30px;border-top:1px solid #e8edf3;background:#f8fafc;color:#708095;font-size:12px;">Reliable escalation, made explicit.</td></tr>
                                  </table>
                                </td></tr>
                              </table>
                            </body>
                            </html>
                            """.formatted(escapeHtml(teamName), escapeHtml(role), escapeHtml(invitationUrl), escapeHtml(expiresAt)));
            mailSender.send(message);
        } catch (InviteMailDeliveryException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Team invitation email failed ({})", e.getClass().getSimpleName());
            throw new InviteMailDeliveryException();
        }
    }

    private String safeSubject(String value) {
        return value == null ? "your team" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
