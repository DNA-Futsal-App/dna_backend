package br.com.dnafutsal.backend.mail.application;

import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.mail.domain.MailMessage;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class MailTemplateFactory {

    private final AppProperties properties;

    public MailTemplateFactory(AppProperties properties) {
        this.properties = properties;
    }

    public MailMessage emailVerification(String name, String email, String rawToken) {
        String link = properties.frontendBaseUrl() + "/confirmar-email?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return new MailMessage(email, "Confirme seu cadastro no DNA Futsal",
                layout(name, "Confirme seu e-mail",
                        "Seu cadastro está quase pronto. Confirme o e-mail para acessar o aplicativo.",
                        "Confirmar e-mail", link));
    }

    public MailMessage passwordReset(String name, String email, String rawToken) {
        String link = properties.frontendBaseUrl() + "/redefinir-senha?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return new MailMessage(email, "Redefinição de senha do DNA Futsal",
                layout(name, "Redefina sua senha",
                        "Recebemos uma solicitação para alterar sua senha. O link expira em breve. "
                                + "Se não foi você, ignore esta mensagem.",
                        "Criar nova senha", link));
    }

    private String layout(String name, String title, String text, String button, String link) {
        return """
                <!doctype html><html lang="pt-BR"><body style="font-family:Arial,sans-serif;background:#f4f7fb;padding:24px">
                <div style="max-width:600px;margin:auto;background:#fff;border-radius:12px;padding:32px">
                  <h1 style="color:#113d78">%s</h1>
                  <p>Olá, %s.</p><p>%s</p>
                  <p style="margin:32px 0"><a href="%s" style="background:#0b63ce;color:#fff;padding:14px 22px;border-radius:8px;text-decoration:none">%s</a></p>
                  <p style="color:#667085;font-size:12px">DNA Futsal — acompanhe a base do futsal paulista.</p>
                </div></body></html>
                """.formatted(escape(title), escape(name), escape(text), link, escape(button));
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
