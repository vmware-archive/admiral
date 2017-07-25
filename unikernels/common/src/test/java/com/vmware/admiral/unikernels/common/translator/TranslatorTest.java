/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.unikernels.common.translator;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TranslatorTest {

    private CapstanFileReference cfr;
    private DockerFileReference dfr;
    private Translator translator;
    Parser parserFromCapstan;
    Parser parserFromDocker;
    private static final String capstanFileName = "CapstanTest";
    private static final String dockerFileName = "DockerTest";

    public TranslatorTest() throws Exception {
        parserFromCapstan = new Parser(
                getClass().getClassLoader().getResourceAsStream(capstanFileName));
        parserFromDocker = new Parser(
                getClass().getClassLoader().getResourceAsStream(dockerFileName));

        cfr = parserFromCapstan.parseCapstan();
        dfr = parserFromDocker.parseDocker();
    }

    // The test files used are with bases, which are not inside the DB
    @Test
    public void translateDockerToCapstanTest() {
        translator = new Translator(dfr);
        DescriptiveFileReference translated = translator.translate(Platform.OSv);
        assertEquals(translated.getBase(), dfr.getBase());
        assertEquals(translated.getGivenName(), "/app.jar"); // since the name in the test docker is
                                                             // not specified this is a default
                                                             // OSv requires '/' before all names
        assertEquals(translated.getWorkDir(), "/"); // no working dir specified
        assertEquals(
                translated.getCMD().contains("cmdline") && translated.getCMD().contains("app.jar"),
                true);

        for (String info : translated.getDocument()) {
            assertEquals(info.equals("base: cloudrouter/osv-builder") ||
                    info.equals("cmdline: /java.so -jar /app.jar") ||
                    info.equals("files:") ||
                    info.equals("  /app.jar: OSv-service.jar"), true);
        }

    }

    @Test
    public void translateCapstanToDockerTest() {
        translator = new Translator(cfr);
        DescriptiveFileReference translated = translator.translate(Platform.Docker);
        assertEquals(translated.getBase(), "openjdk:7");
        assertEquals(translated.getWorkDir(), "/target");

        for (String info : translated.getDocument()) {
            assertEquals(info.equals("FROM openjdk:7") ||
                    info.equals("COPY . /target") ||
                    info.equals("WORKDIR /target") ||
                    info.equals("CMD java -jar my-app-1.0-SNAPSHOT.jar"), true);
        }
    }

}
