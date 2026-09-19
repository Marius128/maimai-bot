package per.kokabot.security;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenCipherTest {

    @Test
    void encryptsWithRandomIvAndDecrypts() {
        TokenCipher cipher = new TokenCipher(randomKey());

        String first = cipher.encrypt("refresh-token");
        String second = cipher.encrypt("refresh-token");

        assertNotEquals(first, second);
        assertEquals("refresh-token", cipher.decrypt(first));
        assertEquals("refresh-token", cipher.decrypt(second));
    }

    @Test
    void rejectsMissingOrInvalidKeys() {
        TokenCipher missing = new TokenCipher("");
        assertThrows(
                IllegalStateException.class,
                () -> missing.encrypt("token")
        );
        assertThrows(
                IllegalStateException.class,
                () -> new TokenCipher(Base64.getEncoder()
                        .encodeToString(new byte[16]))
        );
    }

    @Test
    void detectsTamperedCiphertext() {
        TokenCipher cipher = new TokenCipher(randomKey());
        byte[] encrypted = Base64.getDecoder().decode(
                cipher.encrypt("refresh-token")
        );
        encrypted[encrypted.length - 1] ^= 1;

        assertThrows(
                IllegalStateException.class,
                () -> cipher.decrypt(
                        Base64.getEncoder().encodeToString(encrypted)
                )
        );
    }

    private String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
