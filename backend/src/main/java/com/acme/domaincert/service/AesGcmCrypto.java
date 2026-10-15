package com.acme.domaincert.service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 统一封装 AES-GCM 字符串格式，供证书私钥和动态配置加密复用。 */
public final class AesGcmCrypto {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private AesGcmCrypto() { }

    /** 对明文生成独立随机 IV，再将 IV 与密文一起进行 Base64 编码。 */
    public static String encrypt(byte[] clearText, byte[] key) throws Exception {
        validateKey(key);
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(clearText);
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    /** 校验 GCM 认证标签后返回明文；任何篡改或密钥错误都会抛出异常。 */
    public static byte[] decrypt(String encoded, byte[] key) throws Exception {
        validateKey(key);
        byte[] combined = Base64.getDecoder().decode(encoded);
        if (combined.length <= IV_BYTES) throw new IllegalArgumentException("无效的 AES-GCM 密文");
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
        byte[] cipherText = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(cipherText);
    }

    /** AES-256 密钥必须恰好为 32 字节。 */
    public static void validateKey(byte[] key) {
        if (key == null || key.length != 32) throw new IllegalArgumentException("AES-256 密钥必须为 32 字节");
    }
}
