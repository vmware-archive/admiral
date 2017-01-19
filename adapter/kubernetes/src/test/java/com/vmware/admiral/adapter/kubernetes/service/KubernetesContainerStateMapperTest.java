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

package com.vmware.admiral.adapter.kubernetes.service;

import org.junit.Assert;
import org.junit.Test;

public class KubernetesContainerStateMapperTest {
    @Test
    public void TestCorrectContainerIdExtract() {
        String realId = "some-test-id-83f80ae29bc734";
        String id = "docker://" + realId;
        String extracted = KubernetesContainerStateMapper.getId(id);
        Assert.assertEquals(realId, extracted);
    }
}
