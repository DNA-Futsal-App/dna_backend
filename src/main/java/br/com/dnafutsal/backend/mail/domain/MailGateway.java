package br.com.dnafutsal.backend.mail.domain;

public interface MailGateway {
    void send(MailMessage message);
}
