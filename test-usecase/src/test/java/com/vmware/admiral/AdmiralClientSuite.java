/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.vmware.admiral.client.AdmiralClient;
import com.vmware.admiral.restmock.MockUtils;
import com.vmware.admiral.starter.AdmiralStarter;

@RunWith(Suite.class)
@SuiteClasses({RestMockServerTests.class, CredentialOperationTests.class, PksOperationTests.class})
public class AdmiralClientSuite {

    private static AdmiralStarter admiral;
    private static Path admiralJarFilePath, admiralConfigFilePath, localUsersFilePath;

    @ClassRule
    public static final ExternalResource resource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            setUpAdmiralProcess();
        }

        @Override
        protected void after() {
            tearDownAdmiralProcess();
        }
    };

    public static void setUpAdmiralProcess() throws Exception {

        // Prepare configuration
        readTestConfig();
        prepareAdmiralConfigFiles();

        // Select a random port
        int port = MockUtils.getAvailablePort();

        // Start an Admiral instance
        admiral = new AdmiralStarter(admiralJarFilePath.toString(),
                port, admiralConfigFilePath.toString(), localUsersFilePath.toString());
        admiral.start();

        // Make sure the process has started
        int attempts = 10;
        while(!admiral.isRunning() && attempts > 0) {
            Thread.sleep(1000);
            attempts--;
        }

        if(!admiral.isRunning() && attempts == 0) {
            throw new RuntimeException("Starting Admiral process failed");
        }

        // Wait a while to make sure the API is fully responding
        boolean succeeded = false;
        attempts = 10;
        while(!succeeded && attempts > 0) {

            try {
                createLocalhostClient("fritz@admiral.com", "Password1!").getPksEndpoints();
                succeeded = true;
                break;
            } catch (Exception e) {
                // Ignore
            }

            Thread.sleep(2000);
            attempts--;
        }

        if(!succeeded && attempts == 0) {
            throw new RuntimeException("Can't connect to Admiral API after start");
        }
    }

    public static void tearDownAdmiralProcess() {
        if(admiral.isRunning()) {
            admiral.stop();
        }

        try {
            cleanupConfigFiles();
        } catch (IOException e) {
            throw new RuntimeException("Config file cleanup failed", e);
        }
    }

    public static boolean isAdmiralRunning () {
        return admiral != null && admiral.isRunning();
    }

    public static AdmiralClient createLocalhostClient(String user, String password) {
        return new AdmiralClient("http://localhost:" + admiral.getPort(), user, password);
    }

    private static void readTestConfig() throws IOException {

        String path = System.getProperty("admiral.jar.file.path");
        if(path == null) {

            // This is useful when running the tests from an IDE and
            // you don't want to pass a property for each configuration
            Properties prop = new Properties();
            prop.load(AdmiralClientSuite.class.getResourceAsStream(
                    "/environment/test-config.properties"));
            path = prop.getProperty("admiral.jar.file.path");
        }

        if(path == null) {
            throw new RuntimeException("Failed to find test properties");
        }

        admiralJarFilePath = Paths.get(path);
    }

    private static void prepareAdmiralConfigFiles() throws Exception {

        String tempDirPath = System.getProperty("java.io.tmpdir");
        if(StringUtils.isBlank(tempDirPath)) {
            throw new RuntimeException("Failed to get the temp directory path");
        }

        admiralConfigFilePath = Paths.get(tempDirPath + "/admiral-config.properties");
        writeResourceToFile("/environment/admiral-config.properties", admiralConfigFilePath);

        localUsersFilePath = Paths.get(tempDirPath + "/local-users.json");
        writeResourceToFile("/environment/local-users.json", localUsersFilePath);
    }

    private static void cleanupConfigFiles() throws IOException {
        Files.deleteIfExists(admiralConfigFilePath);
        Files.deleteIfExists(localUsersFilePath);
    }

    private static void writeResourceToFile(String resourcePath, Path filePath) throws Exception {
        Files.write(filePath,
                MockUtils.resourceToString(resourcePath).getBytes(),
                StandardOpenOption.CREATE);
    }
}
