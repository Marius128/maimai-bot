package per.kokabot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenCipher {

    private static final byte FORMAT_VERSION = 1;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    public TokenCipher(@Value("${security.token-encryption-key:}") String encodedKey) {
        this.key = decodeKey(encodedKey);
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("待加密 Token 不能为空");
        }
        SecretKeySpec configuredKey = requireKey();
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    configuredKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );
            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8)
            );
            ByteBuffer output = ByteBuffer.allocate(
                    1 + iv.length + ciphertext.length
            );
            output.put(FORMAT_VERSION);
            output.put(iv);
            output.put(ciphertext);
            return Base64.getEncoder().encodeToString(output.array());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Token 加密失败", error);
        }
    }

    public String decrypt(String encodedCiphertext) {
        if (encodedCiphertext == null || encodedCiphertext.isBlank()) {
            throw new IllegalArgumentException("待解密 Token 不能为空");
        }
        SecretKeySpec configuredKey = requireKey();
        byte[] stored;
        try {
            stored = Base64.getDecoder().decode(encodedCiphertext);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Token 密文格式无效", error);
        }
        if (stored.length <= 1 + IV_LENGTH_BYTES
                || stored[0] != FORMAT_VERSION) {
            throw new IllegalStateException("Token 密文版本无效");
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        byte[] ciphertext = new byte[stored.length - 1 - IV_LENGTH_BYTES];
        System.arraycopy(stored, 1, iv, 0, iv.length);
        System.arraycopy(
                stored,
                1 + iv.length,
                ciphertext,
                0,
                ciphertext.length
        );

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    configuredKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );
            return new String(
                    cipher.doFinal(ciphertext),
                    StandardCharsets.UTF_8
            );
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Token 解密失败", error);
        }
    }

    private SecretKeySpec requireKey() {
        if (key == null) {
            throw new IllegalStateException(
                    "管理员尚未配置 TOKEN_ENCRYPTION_KEY"
            );
        }
        return key;
    }

    private SecretKeySpec decodeKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return null;
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(
                    "TOKEN_ENCRYPTION_KEY 必须是 Base64 字符串",
                    error
            );
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "TOKEN_ENCRYPTION_KEY 解码后必须恰好为 32 字节"
            );
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
