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

package com.vmware.photon.controller.model.data;

import java.util.Map;
import java.util.Objects;

import com.google.gson.annotations.SerializedName;

/**
 * Describe a data schema field
 */
public class SchemaField {
    /**
     * constant for the {@literal string} dataType
     * <p>
     * instance value is of type {@link String} Reference:
     * <a href="https://tools.ietf.org/html/rfc7159#section-7">RFC 7159 Strings</a>
     */
    public static final String DATATYPE_STRING = "string";
    /**
     * constant for the {@literal integer} dataType
     * <p>
     * instance value is of type {@link Integer} Reference:
     * <a href="https://tools.ietf.org/html/rfc7159#section-6">RFC 7159 Numbers</a>
     */
    public static final String DATATYPE_INTEGER = "integer";
    /**
     * constant for the {@literal decimal} dataType
     * <p>
     * instance value is of type {@link Double} Reference:
     * <a href="https://tools.ietf.org/html/rfc7159#section-6">RFC 7159 Numbers</a>
     */
    public static final String DATATYPE_DECIMAL = "decimal";
    /**
     * constant for the {@literal boolean} dataType
     * <p>
     * instance value is of type {@link Boolean} Possible values are either {@literal true} or
     * {@literal false}
     */
    public static final String DATATYPE_BOOLEAN = "boolean";
    /**
     * constant for the {@literal dateTime} dataType
     * <p>
     * Reference: <a href="https://tools.ietf.org/html/rfc3339">Date and Time on the
     * Internet:Timestamps</a>
     * <ul>
     * Example:
     * <li>1985-04-12T23:20:50.52Z</li>
     * <li>1996-12-19T16:39:57-08:00</li>
     * </ul>
     */
    public static final String DATATYPE_DATETIME = "dateTime";

    /**
     * possible field types.
     */
    public enum Type {
        /**
         * This is the default one, specifies the field has single value of {@link #dataType}
         */
        @SerializedName("value")
        VALUE,
        /**
         * Specifies the field is a list of values of {@link #dataType}
         */
        @SerializedName("list")
        LIST,
        /**
         * Specified the field is map of entries which key is of type {@literal string} and the
         * value is of type specified in the {@link #dataType} property
         */
        @SerializedName("map")
        MAP
    }

    public enum Constraint {
        /**
         * read only flag.
         * <p>
         * expect boolean value
         */
        readOnly,
        /**
         * mandatory flag
         * <p>
         * expect boolean value
         */
        mandatory,
        /**
         * list of permissible values
         * <p>
         * list values shall be of same type as for the {@link #dataType} property
         */
        permissibleValues
    }

    /**
     * A short, user-friendly name used for presenting this field to end-users.
     * <p/>
     * Optional.
     */
    public String label;

    /**
     * The description of this field.
     * <p/>
     * Optional.
     */
    public String description;

    /**
     * The type of data supported and expected by this field.
     * <p/>
     * <b>Optional</b>. If {@code dataType} not specified, the field is considered of data type
     * {@literal string}
     */
    public String dataType;

    /**
     * Describes the schema of complex data type for the field.
     * <p>
     * <b>Optional</b>. Value of the property is discarded If {@link #dataType} is specified
     */
    public Schema schema;
    /**
     * Optional property specifying the instance type of the current field.
     */
    public Type type;

    /**
     * Field constraints.
     */
    public Map<Constraint, Object> constraints;

    @Override
    public String toString() {
        return String.format("%s[label=%s, "
                + "dataType=%s, "
                + "type=%s, "
                + "description=%s, "
                + "schema=%s, "
                + "constraints=%s]",
                getClass().getSimpleName(),
                this.label,
                this.dataType,
                this.type,
                this.description,
                this.schema,
                this.constraints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.label, this.dataType, this.type, this.schema, this.constraints);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof SchemaField)) {
            return false;
        }
        SchemaField other = (SchemaField) obj;

        return Objects.equals(this.label, other.label)
                && Objects.equals(this.dataType, other.dataType)
                && Objects.equals(this.type, other.type)
                && Objects.equals(this.schema, other.schema)
                && Objects.equals(this.constraints, other.constraints);
    }
}
