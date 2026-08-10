package br.com.dnafutsal.backend.mail.domain;

public enum MailOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    SENT,
    DEAD
}
