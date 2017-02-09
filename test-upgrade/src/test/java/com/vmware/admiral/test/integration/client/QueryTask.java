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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vmware.admiral.test.integration.client.QueryTask.QueryTerm.MatchType;
import com.vmware.admiral.test.integration.client.ServiceDocumentDescription.ServiceOption;
import com.vmware.admiral.test.integration.client.ServiceDocumentDescription.TypeName;

/**
 * DCP related query task definition used to send as body to the query service.
 */
public class QueryTask extends ServiceDocument {

    public static class QuerySpecification {
        public static final String FIELD_NAME_CHARACTER = ".";

        public static enum QueryOption {
            EXPAND_CONTENT, INCLUDE_DELETED, TASK, COUNT, INCLUDE_ALL_VERSIONS
        }

        public static enum SortOrder {
            ASC, DESC
        }

        public Query query = new Query();
        public QueryTerm sortTerm;
        public Integer resultLimit;
        public Integer resultSkipCount;
        public EnumSet<QueryOption> options;
        public ServiceOption targetIndex;

        /**
         * Set by query task. Infrastructure use only, not serialized.
         */
        public transient Object nativeQuery;

        public static String buildCompositeFieldName(String... fieldNames) {
            StringBuilder sb = new StringBuilder();
            for (String s : fieldNames) {
                if (s == null) {
                    continue;
                }
                sb.append(s).append(FIELD_NAME_CHARACTER);
            }
            sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        }

        public static String buildCollectionItemName(String fieldName) {
            return fieldName + FIELD_NAME_CHARACTER + "item";
        }
    }

    public static class PostProcessingSpecification {
        public static enum GroupOperation {
            SUM, AVG, MIN
        }

        /**
         * Query term that picks the property that the group operation will run over its values
         */
        public QueryTerm selectionTerm;
    }

    public static class NumericRange {
        public TypeName type;
        public Double min;
        public Double max;
        public boolean isMinInclusive;
        public boolean isMaxInclusive;
        public int precisionStep = 4;

        public static NumericRange createLongRange(Long min, Long max,
                boolean isMinInclusive, boolean isMaxInclusive) {
            NumericRange nr = new NumericRange();
            nr.type = TypeName.LONG;
            nr.isMaxInclusive = isMaxInclusive;
            nr.isMinInclusive = isMinInclusive;
            nr.max = max == null ? null : (double) max;
            nr.min = min == null ? null : (double) min;
            return nr;
        }

        public static NumericRange createDoubleRange(Double min, Double max,
                boolean isMinInclusive, boolean isMaxInclusive) {
            NumericRange nr = new NumericRange();
            nr.type = TypeName.DOUBLE;
            nr.isMaxInclusive = isMaxInclusive;
            nr.isMinInclusive = isMinInclusive;
            nr.max = max;
            nr.min = min;
            return nr;
        }
    }

    public static class QueryTerm {
        public enum MatchType {
            WILDCARD, TERM, PHRASE
        }

        public String propertyName;
        public String matchValue;
        public MatchType matchType;
        public NumericRange range;
    }

    public static class Query {
        public static enum Occurance {
            MUST_OCCUR, MUST_NOT_OCCUR, SHOULD_OCCUR
        }

        public Occurance occurance = Occurance.MUST_OCCUR;

        /**
         * A single term definition. This property is exclusive to the booleanClauses list and must
         * be set to null if boolean clauses are supplied
         */
        public QueryTerm term;

        /**
         * A boolean query definition, composed out of 2 or more sub queries. The term property must
         * be null if this property is specified
         */
        public List<Query> booleanClauses;

        public Query setTermPropertyName(String name) {
            allocateTerm();
            this.term.propertyName = name;
            return this;
        }

        public Query setTermMatchValue(String matchValue) {
            allocateTerm();
            this.term.matchValue = matchValue;
            return this;
        }

        public Query setTermMatchType(MatchType matchType) {
            allocateTerm();
            this.term.matchType = matchType;
            return this;
        }

        public Query setNumericRange(NumericRange range) {
            allocateTerm();
            this.term.range = range;
            return this;
        }

        private void allocateTerm() {
            if (this.term != null) {
                return;
            }
            this.term = new QueryTask.QueryTerm();
        }

        public Query addBooleanClause(Query clause) {
            if (this.booleanClauses == null) {
                this.booleanClauses = new ArrayList<>();
                this.term = null;
            }
            this.booleanClauses.add(clause);
            return this;
        }

    }

    public TaskState taskInfo = new TaskState();

    /**
     * Describes the query
     */
    public QuerySpecification querySpec;

    /**
     * Describes any post processing on the query results (such summations, averages) The generation
     * of a time series is also a post processing
     */
    public PostProcessingSpecification postProcessingSpec;

    public ServiceDocumentQueryResult results;

    public static QueryTask create(QuerySpecification q) {
        QueryTask qt = new QueryTask();
        qt.querySpec = q;
        return qt;
    }

    public QueryTask setDirect(boolean enable) {
        this.taskInfo.isDirect = enable;
        return this;
    }

    public static class ServiceDocumentQueryResult extends ServiceDocument {
        public static final String FIELD_NAME_DOCUMENT_LINKS = "documentLinks";

        /**
         * Collection of self links associated with each document found. The self link acts as the
         * primary key for a document. the query
         */
        public Set<String> documentLinks;

        /**
         * If the query included an expand directory, this map contains the JSON serialized service
         * state document associated with each link
         */
        public Map<String, Object> documents;

        /**
         * Valid only Query.OPTION is specified. Set to the number of documents that satisfy the
         * query
         */
        public Long documentCount;
    }
}