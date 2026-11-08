package th.ac.kmitl.connect.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.kmitl.connect.entity.OtpToken;
import th.ac.kmitl.connect.entity.User;
import th.ac.kmitl.connect.exception.ApiException;
import th.ac.kmitl.connect.repository.OtpTokenRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.otp.ttl-minutes:10}")
    private long ttlMinutes;

    /** true = write the OTP to the log instead of sending an email. */
    @Value("${app.otp.dev-mode:true}")
    private boolean devMode;

    @Value("${app.otp.from:no-reply@boardkub.local}")
    private String fromAddress;

    public OtpService(OtpTokenRepository otpTokenRepository,
                      PasswordEncoder passwordEncoder,
                      ObjectProvider<JavaMailSender> mailSender) {
        this.otpTokenRepository = otpTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    @Transactional
    public void issue(User user) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        OtpToken token = new OtpToken();
        token.setUser(user);
        token.setCodeHash(passwordEncoder.encode(code));
        token.setExpiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES));
        token.setUsed(false);
        otpTokenRepository.save(token);

        deliver(user, code);
    }

    @Transactional
    public void verify(User user, String code) {
        OtpToken token = otpTokenRepository.findTopByUserAndUsedFalseOrderByIdDesc(user)
                .orElseThrow(() -> ApiException.badRequest("ไม่พบรหัส OTP กรุณาขอรหัสใหม่"));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("รหัส OTP หมดอายุแล้ว กรุณาขอรหัสใหม่");
        }
        if (!passwordEncoder.matches(code, token.getCodeHash())) {
            throw ApiException.badRequest("รหัส OTP ไม่ถูกต้อง");
        }

        token.setUsed(true);
        otpTokenRepository.save(token);
    }

    private void deliver(User user, String code) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (devMode || sender == null) {
            // Dev only (OTP_DEV_MODE=true): the code is logged so the flow works without SMTP.
            // The email is masked so logs carry no full personal address.
            log.info("[DEV OTP] email={} code={} (expires in {} minutes)", mask(user.getEmail()), code, ttlMinutes);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject("BoardKub - รหัสยืนยันอีเมล");
            message.setText("รหัสยืนยันของคุณคือ " + code
                    + "\nรหัสนี้จะหมดอายุใน " + ttlMinutes + " นาที"
                    + "\n\nหากคุณไม่ได้เป็นผู้ขอรหัสนี้ กรุณาเพิกเฉยต่ออีเมลฉบับนี้");
            sender.send(message);
        } catch (Exception ex) {
            log.warn("Failed to send OTP email to {}: {}", mask(user.getEmail()), ex.getMessage());
            throw ApiException.badRequest("ไม่สามารถส่งอีเมลได้ในขณะนี้ กรุณาลองใหม่อีกครั้ง");
        }
    }

    /** somchai@kmitl.ac.th -> so****@kmitl.ac.th */
    static String mask(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 0) return "****";
        return email.substring(0, Math.min(2, at)) + "****" + email.substring(at);
    }
}
