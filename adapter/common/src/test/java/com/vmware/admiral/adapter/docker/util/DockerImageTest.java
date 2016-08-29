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

package com.vmware.admiral.adapter.docker.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test for DockerImage parsing methods
 */
@RunWith(Parameterized.class)
public class DockerImageTest {
    private final String description;
    private final String fullImageName;
    private final String expectedHost;
    private final String expectedNamespace;
    private final String expectedRepo;
    private final String expectedTag;

    @Parameters
    public static List<String[]> data() {
        List<String[]> data = new ArrayList<>();
        data.add(new String[] { "all sections", "myhost:300/namespace/repo:tag", "myhost:300",
                "namespace", "repo",
                "tag" });

        data.add(new String[] { "repo and tag", "repo:tag", null, null, "repo", "tag" });

        data.add(new String[] { "repo without tag", "repo", null, null, "repo", "latest" });

        data.add(new String[] { "namespace and repo", "namespace/repo", null, "namespace", "repo",
                "latest" });

        data.add(new String[] { "host with dot and repo", "host.name/repo", "host.name", null,
                "repo", "latest" });

        data.add(new String[] { "host with colon and repo", "host:3000/repo", "host:3000", null,
                "repo", "latest" });

        data.add(new String[] { "host with colon, repo and tag", "host:3000/repo:tag", "host:3000",
                null, "repo", "tag" });

        data.add(new String[] { "official repo with default namespace",
                "registry.hub.docker.com/library/repo:tag", null, null, "repo", "tag" });

        data.add(new String[] { "official repo with custom namespace",
                "registry.hub.docker.com/user/repo:tag", null, "user", "repo", "tag" });

        data.add(new String[] { "official repo with default namespace",
                "docker.io/library/repo:tag", null, null, "repo", "tag" });

        data.add(new String[] { "official repo with custom namespace",
                "docker.io/user/repo:tag", null, "user", "repo", "tag" });

        return data;
    }

    /**
     * @param expectedHost
     * @param expectedNamespace
     * @param expectedRepo
     */
    public DockerImageTest(String description, String fullImageName, String expectedHost,
            String expectedNamespace,
            String expectedRepo,
            String expectedTag) {

        this.description = description;
        this.fullImageName = fullImageName;
        this.expectedHost = expectedHost;
        this.expectedNamespace = expectedNamespace;
        this.expectedRepo = expectedRepo;
        this.expectedTag = expectedTag;
    }

    @Test
    public void testDockerImageParsing() {

        DockerImage dockerImage = DockerImage.fromImageName(fullImageName);
        assertEquals(description + ": host", expectedHost, dockerImage.getHost());
        assertEquals(description + ": namespace", expectedNamespace, dockerImage.getNamespace());
        assertEquals(description + ": repository", expectedRepo, dockerImage.getRepository());
        assertEquals(description + ": tag", expectedTag, dockerImage.getTag());
    }
}
