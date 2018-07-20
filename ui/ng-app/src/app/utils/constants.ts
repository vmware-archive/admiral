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

export const Constants = {
    hosts: {
        state: {
            ON: "ON",
            OFF: "OFF",
            UNKNOWN: "UNKNOWN",
            SUSPEND: "SUSPEND"
        },
        type:{
            VCH: 'VCH'
        },
        customProperties: {
            publicAddress: '__publicAddress',
            deploymentPolicyLink: '__deploymentPolicyLink'
        }
    },
    clusters: {
        properties: {
            publicAddress: "publicAddress"
        },
        status: {
            ON: "ON",
            OFF: "OFF",
            DISABLED: "DISABLED",
            WARNING: "WARNING",
            PROVISIONING: "PROVISIONING",
            RESIZING: "RESIZING",
            REMOVING: "REMOVING"
        },
        pks: {
            lastActionState: {
                succeeded: "succeeded",
                inProgress: "in progress",
                failed: "failed"
            }
        },
        DEFAULT_VIEW_REFRESH_INTERVAL: 60000,
        DEFAULT_RESCAN_INTERVAL: 30000,
        DEFAULT_RESCAN_RETRIES_NUMBER: 3,
    },
    alert: {
        type: {
            DANGER: 'alert-danger',
            SUCCESS: 'alert-success',
            WARNING: 'alert-warning'
        }
    },
    recentActivities: {
        requests: {
            FAILED: "FAILED",
            CANCELLED: "CANCELLED",
            CREATED: "CREATED",
            STARTED: "STARTED"
        },
        eventLogs: {
            INFO: 'INFO',
            WARNING: 'WARNING',
            ERROR: 'ERROR'
        },
        MAX_ACTIVITIES_COUNT: 100,
        REFRESH_INTERVAL: 30000
    }
};
