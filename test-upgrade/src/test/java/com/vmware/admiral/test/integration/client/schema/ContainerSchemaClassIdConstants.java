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

package com.vmware.admiral.test.integration.client.schema;

public interface ContainerSchemaClassIdConstants {

    // Event Topic Class Ids:
    String TOPIC_PREFIX = "containers.event.topic";
    String REPLY = ".reply";

    String DATA_COLLECTION_PER_TYPE_TOPIC_CLASS_ID = TOPIC_PREFIX + ".data.collection.type";
    String DATA_COLLECTION_PER_TYPE_TOPIC_REPLY_CLASS_ID = DATA_COLLECTION_PER_TYPE_TOPIC_CLASS_ID
            + REPLY;

    String DATA_COLLECTION_PER_HOST_TOPIC_CLASS_ID = TOPIC_PREFIX + ".data.collection.host";
    String DATA_COLLECTION_PER_HOST_TOPIC_REPLY_CLASS_ID = DATA_COLLECTION_PER_HOST_TOPIC_CLASS_ID
            + REPLY;

    String DATA_COLLECTION_PER_CONTAINER_TOPIC_CLASS_ID = TOPIC_PREFIX
            + ".data.collection.container";
    String DATA_COLLECTION_PER_CONTAINER_TOPIC_REPLY_CLASS_ID = DATA_COLLECTION_PER_CONTAINER_TOPIC_CLASS_ID
            + REPLY;

    String CONTAINER_PROVISIONING_EVENT_TOPIC_CLASS_ID = TOPIC_PREFIX + ".operation.provision";
    String CONTAINER_PROVISIONING_EVENT_TOPIC_REPLY_CLASS_ID = CONTAINER_PROVISIONING_EVENT_TOPIC_CLASS_ID
            + REPLY;

    String CONTAINER_OPERATION_EVENT_TOPIC_CLASS_ID = TOPIC_PREFIX + ".operation.post-provision";
    String CONTAINER_OPERATION_EVENT_TOPIC_REPLY_CLASS_ID = CONTAINER_OPERATION_EVENT_TOPIC_CLASS_ID
            + REPLY;

    // Domain Class Ids:
    String DOMAIN_PREFIX = "containers.model";
    String CONTAINER_RESERVATION_CLASS_ID = DOMAIN_PREFIX + ".reservation";
}
