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

import java.io.File;
import java.nio.file.Files;

import com.vmware.xenon.common.LocalizableValidationException;

/**
 * Simple encryption utility class that provides methods to encrypt and decrypt strings based on the
 * {@link EncryptorService} and the encryption key file provided via a system property. If no
 * encryption key is configured then the encryption/decryption methods do nothing.
 */
public class EncryptionUtils {

    public static final String ENCRYPTION_PREFIX = "s2enc~";

    private static EncryptorService encryptionService;

    static {
        initEncryptionService();
    }

    /**
     * Initializes (or re-initializes) the {@link EncryptorService} by reading the configured
     * encryption key file.
     */
    public static void initEncryptionService() {
        File encryptionKey = getEncryptionFile();
        if (encryptionKey == null) {
            encryptionService = null;
        } else {
            encryptionService = new EncryptorService(encryptionKey);
            try {
                encryptionService.encrypt(ENCRYPTION_PREFIX);
            } catch (Exception e) {
                throw new LocalizableValidationException(e, "Error validating the encryption key!", "common.encryption.file.validation");
            }
        }
    }

    public static final String ENCRYPTION_KEY = "encryption.key.file";
    public static final String INIT_KEY_IF_MISSING = "init.encryption.key.file";

    private static File getEncryptionFile() {

        String param = System.getProperty(ENCRYPTION_KEY);
        if (param == null) {
            return null;
        }

        File encryptionKeyFile = new File(param);
        if (!encryptionKeyFile.exists()) {
            if (Boolean.getBoolean(INIT_KEY_IF_MISSING)) {
                try {
                    Files.write(encryptionKeyFile.toPath(), EncryptorService.generateKey());
                } catch (Exception e) {
                    throw new LocalizableValidationException(e,
                            "Error initializing the encryption key file '" + param + "'!",
                            "common.encryption.file.init", param);
                }
            } else {
                throw new LocalizableValidationException("File '" + param + "' does not exist!",
                        "common.encryption.file.missing", param);
            }
        }

        return encryptionKeyFile;
    }

    /**
     * Encrypts the provided string.
     *
     * @param input
     *            String (UTF-8 encoded) to be encrypted
     * @return The encrypted version of the input string, or directly the input string if no
     *         encryption key is configured.
     */
    public static String encrypt(String input) {
        if (encryptionService == null || input == null || input.length() == 0) {
            return input;
        }
        return ENCRYPTION_PREFIX + encryptionService.encrypt(input);
    }

    /**
     * Decrypts the provided string.
     *
     * @param input
     *            String (UTF-8 encoded) to be decrypted
     * @return The decrypted version of the input string, or directly the input string if no
     *         encryption key is configured.
     */
    public static String decrypt(String input) {
        if (encryptionService == null || input == null || !input.startsWith(ENCRYPTION_PREFIX)) {
            return input;
        }
        return encryptionService.decrypt(input.replaceFirst(ENCRYPTION_PREFIX, ""));
    }

}
