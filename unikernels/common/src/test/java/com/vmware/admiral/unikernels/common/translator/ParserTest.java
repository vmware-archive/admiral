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

import static org.junit.Assert.*;

import java.io.FileNotFoundException;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.unikernels.common.exceptions.CapstanFileFormatException;
import com.vmware.admiral.unikernels.common.exceptions.DockerFileFormatException;

public class ParserTest {

    private Parser parserFromCapstan;
    private Parser parserFromDocker;
    private static final String capstanFileName = "CapstanTest";
    private static final String dockerFileName = "DockerTest";

    @Before
    public void reconstructParser() throws FileNotFoundException {
        parserFromCapstan = new Parser(
                getClass().getClassLoader().getResourceAsStream(capstanFileName));
        parserFromDocker = new Parser(
                getClass().getClassLoader().getResourceAsStream(dockerFileName));
    }

    @Test
    public void parseInformationLengthTest() {
        String[] reading = parserFromCapstan.getReading();
        int numMessages = 0;

        // skip empty lines
        for (String s : reading) {
            if (!s.equals(""))
                numMessages++;
        }
        assertEquals(numMessages == 4, true);
    }

    @Test
    public void parsedTaggedMessagesQualityCapstanTest() {
        assertEquals(parserFromCapstan.getTagArgs("base").equals("base: cloudius/osv-openjdk"),
                true);
    }

    @Test
    public void parsedTaggedMessagesQualityDockerTest() {
        assertEquals(parserFromDocker.getTagArgs("FROM").equals("FROM cloudrouter/osv-builder"),
                true);
    }

    @Test
    public void parseCapstanFromCapstanTest() throws CapstanFileFormatException {
        DescriptiveFileReference dfr = parserFromCapstan.parseCapstan();

        assertEquals(dfr.getBase(), "cloudius/osv-openjdk");
        assertEquals(dfr.getCMD(), "cmdline: /java.so -jar /app.jar");
    }

    @Test(expected = DockerFileFormatException.class)
    public void parseDockerFromCapstan() throws DockerFileFormatException {
        @SuppressWarnings("unused")
        DescriptiveFileReference dfr = parserFromCapstan.parseDocker();
    }

    @Test(expected = CapstanFileFormatException.class)
    public void parseCapstanFromDockerTest() throws CapstanFileFormatException {
        @SuppressWarnings("unused")
        DescriptiveFileReference dfr = parserFromDocker.parseCapstan();
    }

    @Test
    public void parseDockerFromDocker() throws DockerFileFormatException {
        DescriptiveFileReference dfr = parserFromDocker.parseDocker();

        assertEquals(dfr.getBase(), "cloudrouter/osv-builder");
        assertEquals(dfr.getCMD(), "CMD java -jar OSv-service.jar");
    }

}
