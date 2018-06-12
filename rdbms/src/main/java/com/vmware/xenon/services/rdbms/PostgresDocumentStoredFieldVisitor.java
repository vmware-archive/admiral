/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.xenon.services.rdbms;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;

final class PostgresDocumentStoredFieldVisitor {
    public String documentUpdateAction;
    public String documentSelfLink;
    public String documentKind;
    public String documentOwner;
    public long documentVersion;
    public long documentUpdateTimeMicros;
    public Long documentExpirationTimeMicros;
    public String jsonSerializedState;
    private JsonObject jsonObject;
    private Map<String, String> links;

    public PostgresDocumentStoredFieldVisitor() {
    }

    public ServiceDocument getServiceDocumentBuiltInContentOnly() {
        return Utils.fromJson(jsonSerializedState, ServiceDocument.class);
    }

    public JsonObject getAsJsonObject() {
        if (jsonObject == null) {
            jsonObject = Utils.fromJson(jsonSerializedState, JsonObject.class);
        }
        return jsonObject;
    }

    public void stringField(String name, String stringValue) {
        switch (name) {
        case ServiceDocument.FIELD_NAME_SELF_LINK:
            this.documentSelfLink = stringValue;
            break;
        case ServiceDocument.FIELD_NAME_KIND:
            this.documentKind = stringValue;
            break;
        case ServiceDocument.FIELD_NAME_UPDATE_ACTION:
            this.documentUpdateAction = stringValue;
            break;
        case ServiceDocument.FIELD_NAME_OWNER:
            this.documentOwner = stringValue;
            break;
        default:
            if (this.links == null) {
                this.links = new HashMap<>();
            }
            this.links.put(name, stringValue);
        }
    }

    public void longField(String name, long value) {
        switch (name) {
        case ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS:
            this.documentUpdateTimeMicros = value;
            break;
        case ServiceDocument.FIELD_NAME_EXPIRATION_TIME_MICROS:
            this.documentExpirationTimeMicros = value;
            break;
        case ServiceDocument.FIELD_NAME_VERSION:
            this.documentVersion = value;
            break;
        default:
        }
    }

    public void reset() {
        this.documentKind = null;
        this.documentOwner = null;
        this.documentUpdateAction = null;
        this.documentUpdateTimeMicros = 0;
        this.documentExpirationTimeMicros = null;
        this.documentSelfLink = null;
        this.documentVersion = 0;
        this.jsonSerializedState = null;
        this.jsonObject = null;

        if (this.links != null) {
            this.links.clear();
        }
    }

    public String getLink(String linkName) {
        if (this.links == null) {
            return null;
        }

        return this.links.get(linkName);
    }

}