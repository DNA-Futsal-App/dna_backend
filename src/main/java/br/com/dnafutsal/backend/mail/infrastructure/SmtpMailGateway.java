package br.com.dnafutsal.backend.mail.infrastructure;

import br.com.dnafutsal.backend.config.MailDeliveryProperties;
import br.com.dnafutsal.backend.mail.domain.MailDeliveryException;
import br.com.dnafutsal.backend.mail.domain.MailGateway;
import br.com.dnafutsal.backend.mail.domain.MailMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class SmtpMailGateway implements MailGateway {

    private final JavaMailSender mailSender;
    private final MailDeliveryProperties properties;

    public SmtpMailGateway(JavaMailSender mailSender, MailDeliveryProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(MailMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.fromEmail(), properties.fromName());
            helper.setTo(message.recipient());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException | MailException | java.io.UnsupportedEncodingException exception) {
            throw new MailDeliveryException("SMTP provider rejected the message", exception);
        }
    }
}
