package dev.pioruocco.wacchat.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${application.mail.from:}")
    private String fromAddress;

    public void sendWelcome(User user) {
        if (fromAddress.isBlank()) {
            log.warn("MAIL_FROM not configured — skipping welcome email for {}", user.getEmail());
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(user.getEmail());
            msg.setSubject("Benvenuto su WacChat!");
            msg.setText(
                "Ciao " + user.getFirstName() + ",\n\n" +
                "Benvenuto su WacChat! Sono Giuseppe Pio Ruocco, il creatore di questa app.\n" +
                "L'ho realizzata puramente per passione, come hobby, ma anche per attrarre qualche occasione professionale.\n" +
                "Spero possa piacerti ;)\n\n" +
                "Puoi iniziare a chattare visitando https://wacchat.win\n\n" +
                "Se vuoi contattarmi:\n" +
                "Email: dev.pioruocco@gmail.com\n" +
                "LinkedIn: https://www.linkedin.com/in/giuseppe-pio-ruocco-7b4367267/\n\n" +
                "A presto!"
            );
            mailSender.send(msg);
            log.info("Welcome email sent to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
