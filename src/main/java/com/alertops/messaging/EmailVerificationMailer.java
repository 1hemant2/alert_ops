package com.alertops.messaging;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.alertops.auth.model.User;
import com.alertops.auth.service.EmailVerificationMailDeliveryException;

@Component
public class EmailVerificationMailer {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    public EmailVerificationMailer(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${alertops.email.from:}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
    }

    public void sendVerificationEmail(User user, String verificationUrl) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || fromAddress.isBlank()) {
            throw new EmailVerificationMailDeliveryException();
        }

        try {
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(user.getEmail());
            helper.setSubject("Verify your AlertOps email");
            helper.setText(
                    "Verify your AlertOps email address by opening this link:\n\n" + verificationUrl
                            + "\n\nThis link expires soon and can be used once.",
                    """
                            <!doctype html>
                            <html lang="en">
                            <body style="margin:0;padding:28px 12px;background:#f2f5f9;color:#314156;font-family:Arial,Helvetica,sans-serif;">
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                                <tr><td align="center">
                                  <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="max-width:600px;background:#fff;border:1px solid #e3eaf1;border-radius:14px;overflow:hidden;">
                                    <tr><td style="height:5px;background:#8acb3f;font-size:0;">&nbsp;</td></tr>
                                    <tr><td style="padding:22px 30px;background:#14283f;color:#fff;font-size:18px;font-weight:700;">Verify your AlertOps email</td></tr>
                                    <tr><td style="padding:30px;font-size:15px;line-height:1.65;">
                                      <p style="margin:0 0 20px;">Confirm that you control this email address to finish setting up your account.</p>
                                      <p style="margin:0 0 24px;"><a href="%s" style="display:inline-block;padding:12px 18px;border-radius:8px;background:#5b2cff;color:#fff;text-decoration:none;font-weight:700;">Verify email address</a></p>
                                      <p style="margin:0;color:#708095;font-size:13px;">This link expires soon and can be used once. If you did not create an AlertOps account, you can ignore this email.</p>
                                    </td></tr>
                                  </table>
                                </td></tr>
                              </table>
                            </body>
                            </html>
                            """.formatted(verificationUrl));
            mailSender.send(message);
        } catch (Exception e) {
            throw new EmailVerificationMailDeliveryException(e);
        }
    }
}
