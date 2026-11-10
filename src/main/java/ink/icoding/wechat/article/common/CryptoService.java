package ink.icoding.wechat.article.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public CryptoService(@Value("${app.security.secret-key}") String secret) {
        // fail-fast：APP_SECRET_KEY 缺失时拒绝启动（2026-11-10 工程复查 C-1）。
        // 改造前 application.yaml 给它一个众所周知的默认值，而 SHA-256 对空串也能算出密钥，
        // 于是「忘记注入」表现为**应用正常启动**、库里的密文却被一个可猜的密钥保护着。
        // 这里必须抛而不是回退：它是主密钥，静默降级等于让全部 API Key 失去保护。
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "APP_SECRET_KEY 未配置：请在环境变量（或 app.security.secret-key）中提供。"
                            + "它是加密 API Key 与微信 AppSecret 的主密钥，缺失时拒绝启动，"
                            + "以免已保存的密文被空密钥保护。");
        }
        try {
            this.key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("初始化密钥失败", exception);
        }
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception exception) {
            throw new IllegalStateException("敏感信息加密失败", exception);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(value);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("敏感信息解密失败，请检查 APP_SECRET_KEY", exception);
        }
    }
}
