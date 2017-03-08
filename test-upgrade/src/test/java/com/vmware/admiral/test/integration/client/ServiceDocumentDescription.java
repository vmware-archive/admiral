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

package com.vmware.admiral.test.integration.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;

/**
 * DCP related service definition used to send as part of the body to the query service.
 */
public class ServiceDocumentDescription {
    public static enum TypeName {
        LONG, STRING, BYTES, PODO, COLLECTION, MAP, BOOLEAN, DOUBLE, InternetAddressV4, InternetAddressV6, DATE, URI, ENUM, ARRAY
    }

    public enum ServiceOption {
        /**
         * Service runtime tracks statistics on operation completion and allows service instance and
         * external clients to track custom statistics, per instance. Statistics are available
         * through the /stats URI suffix, and served by an utility services associated with each
         * service instance. Statistics are not replicated but can be gathered across all instances
         * using broadcast GET requests.
         */
        INSTRUMENTATION,

        /**
         * Service runtime periodically invokes the handleMaintenance() handler making sure only one
         * maintenance operation is pending per service instance. If a maintenance operation is not
         * complete by the next maintenance interval a warning is logged.
         */
        PERIODIC_MAINTENANCE,

        /**
         * Service runtime forwards the update state to the local document index service state. The
         * document index independently tracks multiple versions of the service state and indexes
         * fields using indexing options specified in the service document template (see
         * {@code getDocumentTemplate}
         */
        PERSISTENCE,

        /**
         * Service state updates are replicated among peer instances on other nodes. The default
         * replication group is used if no group is specified. Updates are replicated into phases
         * and use the appropriate protocol depending on other options. Client does not see request
         * completion until majority of peer instances have accepted the updated. See
         * OWNER_SELECTION option on how it affects replication.
         *
         */
        REPLICATION,

        /**
         * Service runtime performs a node selection process, per service, and forwards all updates
         * to the service instance on the selected node. Ownership is tracked in the indexed state
         * versions and remains fixed as long as the current owner is healthy. To enable scale out
         * only the service instance on the owner node performs work. All instances see updates and
         * should validate and complete them.
         *
         * Requires: REPLICATION Not compatible with: CONCURRENT_UPDATE_HANDLING
         */
        OWNER_SELECTION,

        /**
         * Service state updates employ a replicated state machine protocol to guarantee update was
         * seen by majority of peers, before side-effects become visible to clients. This option
         * reduces the availability of a service since requests will be delayed or fail when the
         * group membership is changing or majority of peers is not available. It also potentially
         * reduces update throughput
         *
         * Requires: REPLICATION, OWNER_SELECTION Not compatible with: CONCURRENT_UPDATE_HANDLING
         */
        EAGER_CONSISTENCY,

        /**
         * Document update operations are conditional: the client must provide the expected
         * signature and/or version.
         *
         * If the service is durable and a signature is available in the current state, then the
         * request body must match the signature. The version is ignored.
         *
         * If there is no signature in the current state, then the version from the current state
         * must match the version in the request body.
         *
         * Requires: REPLICATION Not compatible with: CONCURRENT_UPDATE_HANDLING
         */
        STRICT_UPDATE_CHECKING,

        /**
         * Service runtime provides a HTML interactive UI through custom resource files associated
         * with the service class. The runtime serves the resource files from disk in response to
         * request to the /ui URI suffix
         */
        HTML_USER_INTERFACE,
        /**
         * Advanced option, not recommended.
         *
         * Service runtime disables local concurrency management and allows multiple update to be
         * processed concurrently. This should be used with great care since it does not compose
         * with most other options and can lead to inconsistent state updates. The default service
         * behavior serializes updates so only one update operation is logically pending. Service
         * handlers can issue asynchronous operation and exit immediately but the service runtime
         * still keeps other updates queued, until the operation is completed. GET operations are
         * allowed to execute concurrently with updates, using the latest committed version of the
         * service state
         *
         * Not compatible with: STRICT_UPDATE_CHECKING, PERSISTENCE, REPLICATION, EAGER_CONSISTENCY
         */
        CONCURRENT_UPDATE_HANDLING,

        /**
         * Service factory will convert a POST to a PUT if a child service is already present, and
         * forward it to the service. The service must handle PUT requests and should perform
         * validation on the request body. The child service can enable STRICT_UPDATE_CHECKING to
         * prevent POSTs from modifying state unless the version and signature match
         */
        IDEMPOTENT_POST,

        /**
         * Set by runtime. Service is associated with another service providing functionality for
         * one of the utility REST APIs.
         */
        UTILITY,

        /**
         * Set by runtime. Service creates new instances of services through POST and uses queries
         * to return the active child services, on GET.
         */
        FACTORY,

        /**
         * Set by runtime. Service was created through a factory
         */
        FACTORY_ITEM,

        /**
         * Set by runtime. Service is currently assigned ownership of the replicated document. Any
         * work initiated through an update should only happen on this instance
         */
        DOCUMENT_OWNER,

        NONE
    }

    public static enum PropertyIndexingOption {
        /**
         * Directs the indexing service to fully index a PODO. all fields will be indexed using the
         * field name as the prefix to each child field.
         *
         * If the field is a collection of PODOs each item will be fully indexed.
         */
        EXPAND,

        /**
         * Directs the indexing service to store but not index this field
         */
        STORE_ONLY,

        /**
         * Directs the indexing service to index the field contents as text
         */
        TEXT,

        /**
         * Directs the indexing service to exclude the field from the content signature calculation.
         */
        EXCLUDE_FROM_SIGNATURE
    }

    public static class PropertyDescription {
        public Boolean isCollectionItem;
        public String propertyName;
        public EnumSet<PropertyIndexingOption> indexingOptions;
        public ServiceDocumentDescription.TypeName typeName;
        public String propertyDocumentation;
        public ServiceDocumentDescription nestedDescription;
        transient Field typeField;
        public Object defaultValue;
    }

    public Map<String, ServiceDocumentDescription.PropertyDescription> propertyDescriptions;
    public EnumSet<ServiceOption> serviceCapabilities;
    transient ArrayList<Field> cachedFields;

}