package com.cardplatform.core.card.service;

import com.cardplatform.core.card.config.CardProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class CardCrypto {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String HMAC = "HmacSHA256";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey encryptionKey;
    private final SecretKey hashKey;
    private final SecretKey cvvKey;
    private final SecureRandom random = new SecureRandom();

    public CardCrypto(CardProperties properties) {
        this.encryptionKey = new SecretKeySpec(decodeKey(properties.panEncryptionKey(), "pan-encryption-key"), "AES");
        this.hashKey = new SecretKeySpec(decodeKey(properties.panHashKey(), "pan-hash-key"), HMAC);
        this.cvvKey = new SecretKeySpec(decodeKey(properties.cvvKey(), "cvv-key"), HMAC);
    }

    // AES-GCM encrypts and adds a tamper-detecting tag. The card ID is bound in as associated data,
    // so a ciphertext copied onto another card's row fails to decrypt.
    public byte[] encryptPan(String pan, UUID cardId) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData(cardId));
            byte[] ciphertext = cipher.doFinal(pan.getBytes(StandardCharsets.US_ASCII));
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Card number encryption failed", e);
        }
    }

    public String decryptPan(byte[] stored, UUID cardId) {
        ByteBuffer buffer = ByteBuffer.wrap(stored);
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData(cardId));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Card number decryption failed", e);
        }
    }

    // Same input always gives the same hash, so a card can be found by its number without decrypting
    public String panHash(String pan) {
        return HexFormat.of().formatHex(hmac(hashKey, pan));
    }

    // Derived, never stored: the same card always shows the same CVV
    public String cvv(String pan, YearMonth expiry) {
        byte[] mac = hmac(cvvKey, pan + "|" + expiry);
        int value = (((mac[0] & 0xff) << 8) | (mac[1] & 0xff)) % 1000;
        return String.format("%03d", value);
    }

    private static byte[] hmac(SecretKey key, String input) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    private static byte[] associatedData(UUID cardId) {
        return cardId.toString().getBytes(StandardCharsets.US_ASCII);
    }

    // Fail at startup if a key is missing or the wrong size, not on the first card
    private static byte[] decodeKey(String base64, String name) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("cardplatform.card." + name + " is not valid base64", e);
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException("cardplatform.card." + name + " must be " + KEY_BYTES + " bytes");
        }
        return key;
    }
}