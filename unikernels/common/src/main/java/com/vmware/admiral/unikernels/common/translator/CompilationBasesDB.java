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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CompilationBasesDB {

    public List<List<String>> compilationBasesDB = new ArrayList<>();

    public CompilationBasesDB() {
        fill();
    }

    public List<List<String>> getDB() {
        return compilationBasesDB;
    }

    private void fill() {

        List<String> OSvBases = Arrays.asList("cloudius/osv-openjdk", "cloudius/osv-openjdk8",
                "cloudius/osv-node");
        List<String> DockerBases = Arrays.asList("openjdk:7", "openjdk:8", "node:boron");

        compilationBasesDB.add(Platform.Docker.ordinal(), DockerBases);
        compilationBasesDB.add(Platform.OSv.ordinal(), OSvBases);
    }

}
