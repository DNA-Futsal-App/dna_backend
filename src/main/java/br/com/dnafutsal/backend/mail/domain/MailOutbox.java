package br.com.dnafutsal.backend.mail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mail_outbox")
public class MailOutbox {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "html_body", columnDefinition = "TEXT")
    private String htmlBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MailOutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected MailOutbox() {
    }

    public MailOutbox(MailMessage message, Instant now) {
        this.id = UUID.randomUUID();
        this.recipient = message.recipient();
        this.subject = message.subject();
        this.htmlBody = message.htmlBody();
        this.status = MailOutboxStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public boolean canProcess(Instant now) {
        return (status == MailOutboxStatus.PENDING || status == MailOutboxStatus.RETRY)
                && !nextAttemptAt.isAfter(now);
    }

    public void processing() {
        this.status = MailOutboxStatus.PROCESSING;
        this.attempts++;
    }

    public void sent(Instant now) {
        this.status = MailOutboxStatus.SENT;
        this.sentAt = now;
        this.lastError = null;
        this.htmlBody = null;
    }

    public void failed(Instant now, int maxAttempts, String error) {
        this.lastError = error == null ? "Unknown provider error" : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= maxAttempts) {
            this.status = MailOutboxStatus.DEAD;
            this.htmlBody = null;
            return;
        }
        this.status = MailOutboxStatus.RETRY;
        long delaySeconds = Math.min(3600, 15L * (1L << Math.min(attempts - 1, 8)));
        this.nextAttemptAt = now.plus(Duration.ofSeconds(delaySeconds));
    }

    public UUID getId() {
        return id;
    }

    public MailMessage toMessage() {
        return new MailMessage(recipient, subject, htmlBody);
    }
}
