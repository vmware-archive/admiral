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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * Base class for DCP documents
 */
@XmlTransient
public abstract class ServiceDocument {
    public static final String FIELD_NAME_SELF_LINK = "documentSelfLink";
    public static final String FIELD_NAME_KIND = "documentKind";

    /** The relative URI path of the service managing this document */
    @XmlAttribute
    public String documentSelfLink;

    /** Last time the document was update in microseconds since UNIX epoch */
    @XmlAttribute
    public String documentUpdateTimeMicros;

    /**
     * Expiration time in microseconds since UNIX epoch. If a document is found to be expired a
     * running service instance will be deleted and the document will be marked deleted in the index
     */
    @XmlAttribute
    public String documentExpirationTimeMicros;

    public String getExtractedId() {
        return extractId(documentSelfLink);
    }

    @Override
    public int hashCode() {
        if (documentSelfLink != null) {
            return new HashCodeBuilder(11, 31).append(documentSelfLink).append(this.getClass())
                    .toHashCode();
        } else {
            return super.hashCode();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this.documentSelfLink != null) {
            if (obj == null) {
                return false;
            } else if (obj == this) {
                return true;
            }

            if (getClass().equals(obj.getClass())) {
                return this.documentSelfLink.equals(((ServiceDocument) obj).documentSelfLink);
            } else {
                return false;
            }
        } else {
            return super.equals(obj);
        }
    }

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).toString();
    }

    public static String extractId(String documentSelfLink) {
        if (documentSelfLink == null) {
            return null;
        }
        String id = StringUtils.substringAfterLast(documentSelfLink, "/");
        if (id == null || id.isEmpty()) {
            return documentSelfLink;
        }
        return id;
    }
}
