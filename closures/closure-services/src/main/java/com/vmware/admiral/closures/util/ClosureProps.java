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

package com.vmware.admiral.closures.util;

/**
 * Closure properties accessible via system properties.
 *
 * TODO: move this to the configuration service
 */
public class ClosureProps {

    // Timeout (if not touched) after which the image will be discarded
    public static final Long BUILD_IMAGE_EXPIRE_TIMEOUT_SECONDS =
            Long.getLong("closure.service.build.image.expire-timeout", 24 * 60 * 60L);

    // how long to keep failed images
    public static final Long KEEP_FAILED_BUILDS_TIMEOUT_SECONDS =
            Long.getLong("closure.service.build.image.keep-failed-timeout", 10 * 60L);

    // Interval at which the images will be checked
    public static final Long MAINTENANCE_TIMEOUT_SECONDS =
            Long.getLong("closure.service.build.image.maintenance-period", 60L);

    public static final int DEFAULT_WEB_HOOK_EXPIRATION_TIMEOUT =
            Integer.getInteger("closure.service.webhook-expire-timeout", 30);

    // should keep the execution resource on completion
    public static final boolean IS_KEEP_ON_COMPLETION_ON =
            Boolean.getBoolean("closure.service.keep_on_completion");

    public static final int DOCKER_IMAGE_REQUEST_TIMEOUT_SECONDS = Integer.getInteger(
            "adapter.docker.api.client.image_request_timeout_seconds", 60 * 10);

    public static final Integer MAX_EXEC_TIMEOUT_SECONDS_PROP =
            Integer.getInteger("closure.service.max-exec-timeout-seconds",
                    600);

    public static final int RETRIES_COUNT = Integer
            .getInteger("com.vmware.admiral.service.tasks.retries", 3);

    public static final int MAX_LOG_SIZE_BYTES = Integer
            .getInteger("com.vmware.admiral.closures.max.log.size.bytes",
                    90 * 1024);

    public static final int MAX_FILE_DESCRIPTORS = Integer
            .getInteger("com.vmware.admiral.closures.max.file.descriptors",
                    2048);

    public static final long DEFAULT_CLOSURE_EXPIRATION_DAYS = Integer
            .getInteger("com.vmware.admiral.closures.expiration.days", 10);

    public static final int CLOSURE_CONTAINER_DESCRIPTION_EXPIRATION_SECONDS = Integer
            .getInteger("com.vmware.admiral.closures.container.description.expiration.seconds",
                    120);

    /**
     * Publicly/Externally accessible URI of Admiral services. In case it exits the propery will be
     * used by closure runtime execution to callback Admiral services instead of xenon publicUri
     * property.
     */
    public static final String CLOSURE_SERVICE_CALLBACK_URI = "closure.service.callback.uri";

    /**
     * Trust certificates to use when talking to callback uri
     */
    public static final String CALLBACK_TRUST_CERT_FILE_PATH = "closure.service.callback.trust.certificate";

    /**
     * Sets runtime image registry. If set the runtime base image will be pulled from the
     * registry.
     */
    public static final String CLOSURE_RUNTIME_IMAGE_REGISTRY = "closure.image.runtime.registry.";

    public static final String ENV_PROP_TASK_URI = "TASK_URI";
    public static final String ENV_PROP_TOKEN = "TOKEN";
    public static final String ENV_TRUST_CERTS = "TRUST_CERTS";

    public static final String MAX_LOG_FILE_SIZE = (MAX_LOG_SIZE_BYTES / 1024) + "k";

    public static final Integer MIN_MEMORY_MB_RES_CONSTRAINT = 50;
    public static final Integer MAX_MEMORY_MB_RES_CONSTRAINT = 1536;

    public static final Integer MIN_EXEC_TIMEOUT_SECONDS = 1;
    public static final Integer MAX_EXEC_TIMEOUT_SECONDS = MAX_EXEC_TIMEOUT_SECONDS_PROP;

    public static final Integer MIN_CPU_SHARES = 50;
    public static final Integer DEFAULT_CPU_SHARES = 1024;

    public static final Integer DEFAULT_EXEC_TIMEOUT_SECONDS = 180;
    public static final Integer DEFAULT_MEMORY_MB_RES_CONSTRAINT = MIN_MEMORY_MB_RES_CONSTRAINT;

    private ClosureProps() {
    }

}
