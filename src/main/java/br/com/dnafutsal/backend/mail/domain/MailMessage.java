package br.com.dnafutsal.backend.mail.domain;

public record MailMessage(String recipient, String subject, String htmlBody) {
}
