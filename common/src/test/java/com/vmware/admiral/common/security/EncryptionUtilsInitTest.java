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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vmware.xenon.common.LocalizableValidationException;

public class EncryptionUtilsInitTest {

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    private File keyFile;

    @Before
    public void init() throws IOException {
        System.clearProperty(EncryptionUtils.ENCRYPTION_KEY);
        System.clearProperty(EncryptionUtils.INIT_KEY_IF_MISSING);
        EncryptionUtils.initEncryptionService();
        keyFile = Paths.get(folder.newFolder().getPath(), "encryption.key").toFile();
    }

    @Test
    public void testEncryptionDisabled() {
        String plainText = EncryptorServiceTest.generatePlainText();
        assertEquals(plainText, EncryptionUtils.encrypt(plainText));
        assertEquals(plainText, EncryptionUtils.decrypt(plainText));
    }

    @Test
    public void testInvalidEncryptionKeyFile() {
        System.setProperty(EncryptionUtils.ENCRYPTION_KEY, keyFile.getPath());

        try {
            EncryptionUtils.initEncryptionService();
            fail("File actually does not exist!");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        }
    }

    @Test
    public void testInvalidEncryptionKeyFileContent() throws IOException {
        Files.write(keyFile.toPath(), EncryptionUtils.ENCRYPTION_PREFIX.getBytes(UTF_8));
        System.setProperty(EncryptionUtils.ENCRYPTION_KEY, keyFile.getPath());

        try {
            EncryptionUtils.initEncryptionService();
            fail("File actually does not contain a valid key!");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("validating the encryption key"));
        }
    }

    @Test
    public void testInitEncryptionKey() {
        System.setProperty(EncryptionUtils.ENCRYPTION_KEY, keyFile.getPath());
        System.setProperty(EncryptionUtils.INIT_KEY_IF_MISSING, "true");
        EncryptionUtils.initEncryptionService();

        String plainText = EncryptorServiceTest.generatePlainText();

        String encryptedString = EncryptionUtils.encrypt(plainText);
        assertNotNull(encryptedString);
        assertNotEquals(plainText, encryptedString);
        assertTrue(encryptedString.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));

        String decryptedString = EncryptionUtils.decrypt(encryptedString);
        assertNotNull(decryptedString);

        assertEquals(plainText, decryptedString);
    }

}
