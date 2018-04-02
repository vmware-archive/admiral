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

package com.vmware.admiral.adapter.docker.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.vmware.admiral.common.util.DockerImage;

/**
 * Test the toString function of DockerImage
 */
@RunWith(Parameterized.class)
public class DockerImageToStringTest {
    private final String description;
    private final String fromImageName;
    private final String expectedToString;

    @Parameters
    public static List<String[]> data() {
        List<String[]> data = new ArrayList<>();

        data.add(new String[] { "normal three part image",
                "foo:5000/notlibrary/admiral:latest", "foo:5000/notlibrary/admiral:latest" });

        data.add(new String[] { "host and repo, default library should not be added",
                "foo:5000/officialrepo:latest", "foo:5000/officialrepo:latest" });

        data.add(new String[] { "namespace and repo",
                "notlibrary/repo:latest", "notlibrary/repo:latest" });

        data.add(new String[] { "default namespace (library) should be kept, without host",
                "library/admiral", "library/admiral:latest" });

        data.add(new String[] { "default repository (library) should be kept, with host",
                "foo:5000/library/admiral", "foo:5000/library/admiral:latest" });

        data.add(new String[] {
                "host should be set for image with multiple path segments in repo name",
                "foo:5000/namespace/category/bar", "foo:5000/namespace/category/bar:latest" });

        return data;
    }

    public DockerImageToStringTest(String description, String fromImageName, String expectedToString) {
        this.description = description;
        this.fromImageName = fromImageName;
        this.expectedToString = expectedToString;
    }

    @Test
    public void testCanonicalization() {
        DockerImage dockerImage = DockerImage.fromImageName(fromImageName);
        assertEquals(description + ": ", expectedToString, dockerImage.toString());
    }

}
