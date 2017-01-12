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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.junit.Test;

import com.vmware.xenon.common.LocalizableValidationException;

/**
 * The encryption and decryption operations from the {@link EncryptorService} can be validated
 * and/or combined with the commands openssl-encrypt.sh and openssl-decrypt.sh from the folder
 * src/test/resources. They use standard OpenSSL operations and don't depend on Java libraries.
 */
public class EncryptorServiceTest {

    private static final File KEY_FILE = getKeyFile();

    @Test
    public void testBytesEncryptionWithKey() {

        byte[] key = EncryptorService.generateKey();
        EncryptorService service = new EncryptorService(key);

        byte[] plainBytes = generatePlainText().getBytes(UTF_8);

        byte[] encryptedBytes = service.encrypt(plainBytes);
        assertNotNull(encryptedBytes);
        assertFalse(Arrays.equals(plainBytes, encryptedBytes));

        byte[] decryptedBytes = service.decrypt(encryptedBytes);
        assertNotNull(decryptedBytes);

        assertArrayEquals(plainBytes, decryptedBytes);
    }

    @Test
    public void testStringEncryptionWithKey() {

        byte[] key = EncryptorService.generateKey();
        EncryptorService service = new EncryptorService(key);

        String plainText = generatePlainText();

        String encryptedString = service.encrypt(plainText);
        assertNotNull(encryptedString);
        assertNotEquals(plainText, encryptedString);

        String decryptedString = service.decrypt(encryptedString);
        assertNotNull(decryptedString);

        assertEquals(plainText, decryptedString);
    }

    @Test
    public void testBytesEncryptionWithKeyFile() {

        EncryptorService service = new EncryptorService(KEY_FILE);

        byte[] plainBytes = generatePlainText().getBytes(UTF_8);

        byte[] encryptedBytes = service.encrypt(plainBytes);
        assertNotNull(encryptedBytes);
        assertFalse(Arrays.equals(plainBytes, encryptedBytes));

        byte[] decryptedBytes = service.decrypt(encryptedBytes);
        assertNotNull(decryptedBytes);

        assertArrayEquals(plainBytes, decryptedBytes);
    }

    @Test
    public void testStringEncryptionWithKeyFile() {

        EncryptorService service = new EncryptorService(KEY_FILE);

        String plainText = generatePlainText();

        String encryptedString = service.encrypt(plainText);
        assertNotNull(encryptedString);
        assertNotEquals(plainText, encryptedString);

        String decryptedString = service.decrypt(encryptedString);
        assertNotNull(decryptedString);

        assertEquals(plainText, decryptedString);
    }

    @Test
    public void testBadKey() {

        byte[] key = "bad".getBytes(UTF_8);

        EncryptorService serviceBad = new EncryptorService(key);

        String plainText = generatePlainText();

        try {
            serviceBad.encrypt(plainText);
            fail("It shouldn't get here");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("Encryption error!"));
        }

        EncryptorService service = new EncryptorService(EncryptorService.generateKey());
        String encryptedString = service.encrypt(plainText);

        try {
            serviceBad.decrypt(encryptedString);
            fail("It shouldn't get here");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("Decryption error!"));
        }
    }

    @Test
    public void testWrongKey() {

        byte[] keyOne = EncryptorService.generateKey();
        byte[] keyTwo = EncryptorService.generateKey();
        assertFalse(Arrays.equals(keyOne, keyTwo));

        EncryptorService serviceOne = new EncryptorService(keyOne);
        EncryptorService serviceTwo = new EncryptorService(keyTwo);

        byte[] plainBytesOne = (generatePlainText() + " One").getBytes(UTF_8);
        byte[] plainBytesTwo = (generatePlainText() + " Two").getBytes(UTF_8);

        byte[] encryptedBytesOne = serviceOne.encrypt(plainBytesOne);
        byte[] encryptedBytesTwo = serviceTwo.encrypt(plainBytesTwo);
        assertNotNull(encryptedBytesOne);
        assertNotNull(encryptedBytesTwo);
        assertFalse(Arrays.equals(encryptedBytesOne, encryptedBytesTwo));

        try {
            byte[] decryptedBytesWrong = serviceTwo.decrypt(encryptedBytesOne);

            // it shouldn't get here (because of key incompatibility), but just in case...
            if (Arrays.equals(plainBytesOne, decryptedBytesWrong)) {
                fail("It shouldn't get here, really");
            }
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("Decryption error!"));
        }

        byte[] decryptedBytesOne = serviceOne.decrypt(encryptedBytesOne);
        assertNotNull(decryptedBytesOne);

        assertArrayEquals(plainBytesOne, decryptedBytesOne);
    }

    @Test
    public void testBadFile() throws IOException {

        File keyFile = File.createTempFile("encription.key", null); // it's an empty file!

        EncryptorService serviceBad = new EncryptorService(keyFile);

        String plainText = generatePlainText();

        try {
            serviceBad.encrypt(plainText);
            fail("It shouldn't get here");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("Encryption error!"));
        }

        EncryptorService service = new EncryptorService(KEY_FILE);
        String encryptedString = service.encrypt(plainText);

        try {
            serviceBad.decrypt(encryptedString);
            fail("It shouldn't get here");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("Decryption error!"));
        }
    }

    @Test
    public void testWrongFile() throws IOException {

        File keyFile = new File("wrong"); // it doesn't exist!

        try {
            new EncryptorService(keyFile);
            fail("It shouldn't get here");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("Invalid encryption key file!"));
        }
    }

    public static File getKeyFile() {
        try {
            return new File(
                    EncryptorServiceTest.class.getResource("/encryption.key").toURI().getPath());
        } catch (Exception e) {
            throw new IllegalStateException("Error reading encryption.key file!", e);
        }
    }

    public static String generatePlainText() {
        return new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").format(new Date());
    }

}
