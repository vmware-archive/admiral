/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Simple encryption service that provides methods to encrypt and decrypt byte arrays and strings
 * based on the provided symmetric key. The key can be provided directly as a byte array or through
 * a file which contains it.
 *
 * The service's default settings require to have the "Java Cryptography Extension (JCE) Unlimited
 * Strength Jurisdiction Policy Files 8" installed in ${java.home}/jre/lib/security/
 * See http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html
 */
public final class EncryptorService {

    private final byte[] keyBytes;

    /**
     * Creates a new {@link EncryptorService} instance from the provided encryption key.
     *
     * @param encryptionKeyBytes
     *            Encryption key as byte array
     */
    public EncryptorService(byte[] encryptionKeyBytes) {
        assertNotNull(encryptionKeyBytes, "encryptionKeyBytes");
        this.keyBytes = encryptionKeyBytes.clone();
    }

    /**
     * Creates a new {@link EncryptorService} instance from the provided encryption key file.
     *
     * @param encryptionKeyFile
     *            File containing the encryption key (as byte array)
     */
    public EncryptorService(File encryptionKeyFile) {
        assertNotNull(encryptionKeyFile, "encryptionKeyFile");
        try {
            this.keyBytes = Files.readAllBytes(Paths.get(encryptionKeyFile.toURI()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid encryption key file!", e);
        }
    }

    /**
     * Encrypts the provided string.
     *
     * @param input
     *            String (UTF-8 encoded) to be encrypted
     * @return The encrypted version of the input string.
     */
    public String encrypt(String input) {
        if (input == null || input.length() == 0) {
            return input;
        }
        byte[] inputBytes = input.getBytes(UTF_8);
        byte[] outputBytes = encrypt(inputBytes);
        return new String(outputBytes, UTF_8);
    }

    /**
     * Encrypts the provided byte array.
     *
     * @param input
     *            Byte array to be encrypted
     * @return The encrypted version of the input byte array (in base 64).
     */
    public byte[] encrypt(final byte[] input) {
        if (input == null || input.length == 0) {
            return input;
        }
        try {
            Cipher cipher = getCipher(ENCRYPT_MODE);
            byte[] output = cipher.doFinal(input);
            byte[] output64 = Base64.getEncoder().encode(output);
            return output64;
        } catch (Exception e) {
            throw new IllegalStateException("Encryption error!", e);
        }
    }

    /**
     * Decrypts the provided string.
     *
     * @param input
     *            String (UTF-8 encoded) to be decrypted
     * @return The decrypted version of the input string.
     */
    public String decrypt(String input) {
        if (input == null || input.length() == 0) {
            return input;
        }

        byte[] inputBytes = input.getBytes(UTF_8);
        byte[] outputBytes = decrypt(inputBytes);
        return new String(outputBytes, UTF_8);
    }

    /**
     * Decrypts the provided byte array.
     *
     * @param input
     *            Byte array (in base 64) to be decrypted
     * @return The decrypted version of the input byte array.
     */
    public byte[] decrypt(final byte[] input) {
        if (input == null || input.length == 0) {
            return input;
        }
        try {
            byte[] input64 = Base64.getDecoder().decode(input);
            Cipher cipher = getCipher(DECRYPT_MODE);
            byte[] output = cipher.doFinal(input64);
            return output;
        } catch (Exception e) {
            throw new IllegalStateException("Decryption error!", e);
        }
    }

    /*
     * Secure random settings
     */

    private static final String ALGORITH_SECURE_RANDOM = "SHA1PRNG";

    // SecureRandom is thread-safe
    private static SecureRandom secureRandom;

    static {
        if (secureRandom == null) {
            try {
                secureRandom = SecureRandom.getInstance(ALGORITH_SECURE_RANDOM);
            } catch (NoSuchAlgorithmException e) {
                // this should not happen at all (Sun provides this algorithm)
                throw new IllegalStateException(e);
            }
        }
    }

    /*
     * Symmetric key settings
     */

    private static final int KEY_LENGTH = 32; // 256 bit length for AES
    private static final int IV_LENGTH = 16;

    /**
     * Generates a new symmetric encryption key.
     *
     * @return The generated key as byte array.
     */
    public static byte[] generateKey() {

        byte[] keyData = new byte[KEY_LENGTH];
        secureRandom.nextBytes(keyData);

        byte[] ivData = new byte[IV_LENGTH];
        secureRandom.nextBytes(ivData);

        byte[] key = new byte[IV_LENGTH + KEY_LENGTH];
        System.arraycopy(ivData, 0, key, 0, IV_LENGTH);
        System.arraycopy(keyData, 0, key, IV_LENGTH, KEY_LENGTH);
        return key;
    }

    private static byte[] getKeyData(byte[] key) {
        byte[] data = new byte[KEY_LENGTH];
        System.arraycopy(key, IV_LENGTH, data, 0, KEY_LENGTH);
        return data;
    }

    private static byte[] getIvData(byte[] key) {
        byte[] data = new byte[IV_LENGTH];
        System.arraycopy(key, 0, data, 0, IV_LENGTH);
        return data;
    }

    /*
     * Cipher settings
     */

    private static final String KEY_SPEC = "AES";
    private static final String CIPHER_SPEC = "AES/CBC/PKCS5Padding";

    private Cipher getCipher(int mode) throws Exception {
        Key key = new SecretKeySpec(getKeyData(keyBytes), KEY_SPEC);
        IvParameterSpec iv = new IvParameterSpec(getIvData(keyBytes));

        Cipher cipher = Cipher.getInstance(CIPHER_SPEC);
        cipher.init(mode, key, iv);
        return cipher;
    }

}
