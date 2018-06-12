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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryRuntimeContext;
import com.vmware.xenon.services.rdbms.PostgresSchemaManager.ColumnDescription;
import com.vmware.xenon.services.rdbms.PostgresSchemaManager.TableDescription;

/**
 * Convert {@link QuerySpecification} to Postgres query.
 */
final class PostgresQueryConverter {
    private static final Logger logger = Logger.getLogger(PostgresQueryConverter.class.getName());

    private static class StringBuilderThreadLocal extends ThreadLocal<StringBuilder> {
        private static final int BUFFER_INITIAL_CAPACITY = 1024;

        protected StringBuilder initialValue() {
            return new StringBuilder(BUFFER_INITIAL_CAPACITY);
        }

        @Override
        public StringBuilder get() {
            StringBuilder result = super.get();
            if (result.length() > 10 * BUFFER_INITIAL_CAPACITY) {
                result = initialValue();
                set(result);
            } else {
                result.setLength(0);
            }

            return result;
        }
    }

    static final String SQL_TRUE = "TRUE";
    static final String SQL_FALSE = "FALSE";

    private static final StringBuilderThreadLocal builderPerThread = new StringBuilderThreadLocal();

    private static final QueryTask.QueryTerm QUERY_TERM_TRUE;
    private static final QueryTask.QueryTerm QUERY_TERM_FALSE;

    static {
        QUERY_TERM_TRUE = new QueryTask.QueryTerm();
        QUERY_TERM_TRUE.propertyName = ServiceDocument.FIELD_NAME_SELF_LINK;
        QUERY_TERM_TRUE.matchType = QueryTask.QueryTerm.MatchType.WILDCARD;
        QUERY_TERM_TRUE.matchValue = "*";

        QUERY_TERM_FALSE = new QueryTask.QueryTerm();
        QUERY_TERM_FALSE.propertyName = ServiceDocument.FIELD_NAME_SELF_LINK;
        QUERY_TERM_FALSE.matchType = QueryTask.QueryTerm.MatchType.TERM;
        QUERY_TERM_FALSE.matchValue = "";
    }

    private PostgresQueryConverter() {
    }

    static String convert(Query query, QueryRuntimeContext context, TableDescription td) {
        query = reduceQuery(query, td);

        try {
            if (query.occurance == null) {
                query.occurance = QueryTask.Query.Occurance.MUST_OCCUR;
            }

            StringBuilder condition = builderPerThread.get();

            convertToPostgresQuery(condition, true, query, context, td);

            String sql = condition.toString();
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("Convert: %s\n%s", sql, Utils.toJsonHtml(query)));
            }

            return sql;
        } catch (Exception e) {
            logger.severe(() -> String.format("Conversion failed: %s", Utils.toString(e)));
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void convertToPostgresQuery(StringBuilder sb, boolean first, Query query,
            QueryRuntimeContext context, TableDescription td) {
        if (query.occurance == null) {
            query.occurance = Query.Occurance.MUST_OCCUR;
        }

        if (query.booleanClauses != null) {
            if (query.term != null) {
                throw new IllegalArgumentException(
                        "term and booleanClauses are mutually exclusive");
            }

            convertToSqlBooleanQuery(sb, first, query, context, td);
            return;
        }

        if (query.term == null) {
            throw new IllegalArgumentException("One of term, booleanClauses must be provided");
        }

        QueryTask.QueryTerm term = query.term;
        validateTerm(term);
        if (term.matchType == null) {
            term.matchType = QueryTask.QueryTerm.MatchType.TERM;
        }

        if (context != null && query.occurance != QueryTask.Query.Occurance.MUST_NOT_OCCUR
                && ServiceDocument.FIELD_NAME_KIND.equals(term.propertyName)) {
            if (context.kindScope == null) {
                // assume most queries contain 1 or 2 document kinds. Initialize with size 4
                // to prevent resizing when the second kind is added. The default size of 16
                // has never been filled up.
                context.kindScope = new HashSet<>(4);
            }
            context.kindScope.add(term.matchValue);
        }

        ColumnDescription cd = getColumnDescription(td, query.term.propertyName);
        if (cd == null && context != null && context.nativeSearcher != null) {
            // Search all tables
            Set<TableDescription> tables = (Set<TableDescription>) context.nativeSearcher;
            for (TableDescription td2 : tables) {
                if ((cd = getColumnDescription(td2, query.term.propertyName)) != null) {
                    break;
                }
            }
        }

        String condition;
        if (term == QUERY_TERM_TRUE) {
            condition = SQL_TRUE;
        } else if (term == QUERY_TERM_FALSE) {
            condition = SQL_FALSE;
        } else if (term.range != null) {
            condition = convertToSqlNumericRangeQuery(query, cd);
        } else {
            if (term.matchType == QueryTask.QueryTerm.MatchType.WILDCARD) {
                condition = convertToSqlLikeQuery(query, cd);
            } else if (term.matchType == QueryTask.QueryTerm.MatchType.PHRASE) {
                condition = convertToSqlPhraseQuery(query, cd);
            } else if (term.matchType == QueryTask.QueryTerm.MatchType.PREFIX) {
                condition = convertToSqlPrefixQuery(query, cd);
            } else {
                condition = convertToSqlSingleTermQuery(query, cd);
            }
        }

        convertQueryCondition(sb, first, query, condition);
    }

    private static String wrapField(String propertyName, ColumnDescription cd,
            String expectedType) {
        String columnName;
        if (cd != null && cd.getPropertyName().equals(propertyName)) {
            if (isTextType(expectedType)) {
                return cd.getColumnNameAsText();
            }

            if (expectedType == null || cd.isNativeColumn()
                    || cd.getColumnType().equals(expectedType)) {
                return cd.getColumnName();
            }

            return String.format("(%s)::%s", cd.getColumnNameAsText(), expectedType);
        }

        boolean isExpectedJsonType = expectedType == null || isJsonbType(expectedType);
        String typeSuffix = !isExpectedJsonType ? ">" : "";

        propertyName = normalizePropertyName(propertyName);
        if (propertyName.contains(".")) {
            columnName = String.format("data #>%s '{%s}'", typeSuffix,
                    escapeSqlString(propertyName.replace('.', ',')));
        } else {
            columnName = String.format("data ->%s '%s'", typeSuffix, escapeSqlString(propertyName));
        }

        if (!isExpectedJsonType) {
            return String.format("(%s)::%s", columnName, expectedType);
        }

        return columnName;
    }

    private static String wrapStringField(String propertyName, ColumnDescription cd) {
        return wrapField(propertyName, cd, "text");
    }

    private static String wrapNativeField(String propertyName, ColumnDescription cd) {
        return wrapField(propertyName, cd, null);
    }

    static String escapeSqlLike(String p) {
        return p.replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("(", "\\(")
                .replace("_", "\\_")
                .replace("%", "\\%")
                .replace("'", "\\'");
    }

    private static void convertQueryCondition(StringBuilder sb, boolean first, Query query,
            String condition) {
        if (query.occurance == null) {
            query.occurance = Occurance.MUST_OCCUR;
        }

        // Skip adding AND TRUE
        if (!first) {
            if (query.occurance == Occurance.MUST_OCCUR && isSqlTrue(condition)) {
                return;
            }
            if (query.occurance == Occurance.SHOULD_OCCUR && isSqlFalse(condition)) {
                return;
            }
        }

        switch (query.occurance) {
        case MUST_NOT_OCCUR:
            if (!first) {
                sb.append(" AND ");
            }
            if (isSqlTrue(condition)) {
                sb.append(SQL_FALSE);
                return;
            }
            if (isSqlFalse(condition)) {
                sb.append(SQL_TRUE);
                return;
            }

            if (query.term != null
                    && query.term.propertyName.equals(ServiceDocument.FIELD_NAME_SELF_LINK)) {
                sb.append("NOT ").append(condition);
            } else {
                // "NOT (field = 'value')" evaluates to NULL if field is NULL, so here we make sure
                // to
                // use "NOT (field IS NOT NULL AND field = 'value')" instead
                sb.append("NOT COALESCE(").append(condition).append(", FALSE)");
            }
            break;
        case SHOULD_OCCUR:
            if (!first) {
                sb.append(" OR ");
            }
            sb.append(condition);
            break;
        case MUST_OCCUR:
        default:
            if (!first) {
                sb.append(" AND ");
            }
            sb.append(condition);
        }
    }

    private static String convertToSqlSingleTermQuery(Query query, ColumnDescription cd) {
        // support for "*" queries which does not specify WILDCARD search (by mistake)
        // TODO: Remove after fixing source
        if (query.term.matchValue.equals(UriUtils.URI_WILDCARD_CHAR)) {
            return String.format("%s IS NOT NULL", wrapStringField(query.term.propertyName, cd));
        }

        boolean isCaseInsensitive = isCaseInsensitive(cd);

        // TODO: CASE_INSENSITIVE will not work with collection
        if (isCollectionField(query)) {
            // TODO: Review
            // return String.format("%s @> '\"%s\"'", wrapNativeField(query.term.propertyName, cd),
            // escapeJsonString(query.term.matchValue));
            return String.format("%s ? '%s'", wrapNativeField(query.term.propertyName, cd),
                    escapeSqlString(query.term.matchValue));
        }

        String stringField = wrapStringField(query.term.propertyName, cd);
        String stringValue = query.term.matchValue;

        if (isCaseInsensitive) {
            stringField = String.format("LOWER(%s)", stringField);
            stringValue = stringValue.toLowerCase();
        }

        if (isTextIndexingOption(cd)) {
            // Is text field
            // TODO: use postgres text indexing?
            return String.format("%s LIKE '%%%s%%' ESCAPE '\\'", stringField,
                    escapeSqlLike(stringValue));
        }

        if (query.term.propertyName.contains(
                QuerySpecification.FIELD_NAME_CHARACTER + QuerySpecification.COLLECTION_FIELD_SUFFIX
                        + QuerySpecification.FIELD_NAME_CHARACTER)) {
            String[] s = query.term.propertyName.split(QuerySpecification.FIELD_NAME_REGEXP);
            String select = String.format("jsonb_array_elements(data->'%s') ->> '%s'",
                    escapeSqlString(s[0]), escapeSqlString(s[2]));

            if (isCaseInsensitive) {
                select = String.format("LOWER(%s)", select);
            }
            return String.format("'%s' IN (SELECT %s)", escapeSqlString(stringValue), select);
        }

        // TODO: Need to support case insensitive in MAP
        if (cd != null
                && cd.getPropertyDescription().typeName == ServiceDocumentDescription.TypeName.MAP
                && isCaseInsensitive) {
            isCaseInsensitive = false;
        }
        if (cd != null && !isCaseInsensitive && !cd.isNativeColumn()) {
            String condition = toJsonContainsCondition(query, cd);
            if (condition != null) {
                return condition;
            }
        }

        return String.format("%s = '%s'", stringField, escapeSqlString(stringValue));
    }

    private static String toJsonContainsCondition(Query query, ColumnDescription cd) {
        String propertyName = normalizePropertyName(query.term.propertyName);
        String[] fields = propertyName.split(QuerySpecification.FIELD_NAME_REGEXP);
        if (fields.length < 2) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int level = 0;
        for (String field : fields) {
            if (level > 0) {
                sb.append("{\"").append(escapeJsonString(field)).append("\":");
            }
            level++;
        }

        ServiceDocumentDescription.TypeName typeName = null;
        if (cd != null) {
            if (cd.getPropertyDescription().elementDescription != null) {
                typeName = cd.getPropertyDescription().elementDescription.typeName;
            } else {
                typeName = cd.getPropertyDescription().typeName;
            }
        }

        if (typeName == null) {
            typeName = ServiceDocumentDescription.TypeName.STRING;
        }

        try {
            switch (typeName) {
            case LONG:
                sb.append(Long.parseLong(query.term.matchValue));
                break;
            case BOOLEAN:
                sb.append(Boolean.parseBoolean(query.term.matchValue));
                break;
            case DOUBLE:
                sb.append(Double.parseDouble(query.term.matchValue));
                break;
            case STRING:
            case InternetAddressV4:
            case InternetAddressV6:
            case DATE:
            case URI:
            case ENUM:
            case BYTES:
            case PODO:
            case COLLECTION:
            case MAP:
            default:
                sb.append("\"").append(escapeJsonString(query.term.matchValue)).append("\"");
            }
        } catch (Throwable e) {
            logger.warning(String.format("Failed to convert %s: %s", typeName, e));
            return null;
        }
        while (level-- > 1) {
            sb.append('}');
        }

        return String.format("data -> '%s' @> '%s'", escapeSqlString(fields[0]),
                escapeSqlString(sb.toString()));
    }

    // For language agnostic, or advanced token parsing a Tokenizer from the LUCENE
    // analysis package should be used.
    // TODO consider compiling the regular expression.
    // Currently phrase queries are considered a rare, special case.
    private static String convertToSqlPhraseQuery(QueryTask.Query query, ColumnDescription cd) {
        String stringField = wrapStringField(query.term.propertyName, cd);
        String stringValue = query.term.matchValue;

        // Is case insensitive field
        if (isCaseInsensitive(cd)) {
            stringField = String.format("LOWER(%s)", stringField);
            stringValue = stringValue.toLowerCase();
        }

        // TODO: Use full text search
        // https://www.postgresql.org/docs/9.1/static/textsearch-controls.html
        String[] tokens = stringValue.split("\\W");
        StringJoiner joiner = new StringJoiner(" AND ");
        for (String token : tokens) {
            joiner.add(String.format("%s LIKE '%%%s%%' ESCAPE '\\'", stringField,
                    escapeSqlLike(token)));
        }
        return joiner.toString();
    }

    private static String convertToSqlPrefixQuery(QueryTask.Query query, ColumnDescription cd) {
        String stringField = wrapStringField(query.term.propertyName, cd);
        String stringValue = query.term.matchValue;

        // Is case insensitive field
        boolean isCaseInsensitive = isCaseInsensitive(cd);
        if (isCaseInsensitive) {
            stringField = String.format("LOWER(%s)", stringField);
            stringValue = stringValue.toLowerCase();
        }

        // when searching in collection we use the string representation of the collection's json
        String escapedPrefix = escapeSqlLike(stringValue);
        if (isCollectionField(query)) {
            if (escapedPrefix.isEmpty()) {
                return String.format(
                        "EXISTS(SELECT FROM jsonb_array_elements(%s) value WHERE value IS NOT NULL)",
                        wrapNativeField(query.term.propertyName, cd));
            }

            return String.format(
                    "EXISTS(SELECT FROM jsonb_array_elements_text(%s) value WHERE value %s '%s%%' ESCAPE '\\')",
                    wrapNativeField(query.term.propertyName, cd),
                    isCaseInsensitive ? "ILIKE" : "LIKE", escapedPrefix);

            // TODO: Review
            // return String.format("%s LIKE '%%\"%s%%' ESCAPE '\\'", stringField, escapedPrefix);
        }

        if (query.term.propertyName.contains(
                QuerySpecification.FIELD_NAME_CHARACTER + QuerySpecification.COLLECTION_FIELD_SUFFIX
                        + QuerySpecification.FIELD_NAME_CHARACTER)) {
            String[] s = query.term.propertyName.split(QuerySpecification.FIELD_NAME_REGEXP);

            return String.format(
                    "EXISTS(SELECT FROM jsonb_array_elements(data -> '%s') value WHERE value ->> '%s' %s '%s%%' ESCAPE '\\')",
                    escapeSqlString(s[0]), escapeSqlString(s[2]),
                    isCaseInsensitive ? "ILIKE" : "LIKE", escapedPrefix);
        }

        return String.format("%s LIKE '%s%%' ESCAPE '\\'", stringField, escapedPrefix);
    }

    private static String convertToSqlLikeQuery(QueryTask.Query query, ColumnDescription cd) {
        String stringValue = query.term.matchValue;

        // if the query is a wildcard, this is typically used to check the field is not null
        if (stringValue.equals(UriUtils.URI_WILDCARD_CHAR)) {
            if (isCollectionField(query)) {
                return String.format("jsonb_array_length(%s) > 0",
                        wrapNativeField(query.term.propertyName, cd));
            }
            return String.format("%s IS NOT NULL", wrapNativeField(query.term.propertyName, cd));
        }

        String stringField = wrapStringField(query.term.propertyName, cd);

        // Is case insensitive field
        if (isCaseInsensitive(cd)) {
            stringField = String.format("LOWER(%s)", stringField);
            stringValue = stringValue.toLowerCase();
        }

        // when searching in collection we use the string representation of the collection's json
        // TODO: Better way to search inside a collection/map
        String matchValue = escapeSqlLike(stringValue).replace('*', '%')
                .replace('?', '_');
        if (isCollectionField(query)) {
            return String.format("%s LIKE '%%\"%s\"%%' ESCAPE '\\'", stringField, matchValue);
        }

        String condition;
        // Convert to simple equals if it's not a like condition
        if (matchValue.equals(stringValue)) {
            condition = String.format("%s = '%s'", stringField, stringValue);
        } else {
            condition = String.format("%s LIKE '%s' ESCAPE '\\'", stringField, matchValue);
        }
        return condition;
    }

    private static String convertToSqlNumericRangeQuery(QueryTask.Query query,
            ColumnDescription cd) {
        QueryTask.QueryTerm term = query.term;

        term.range.validate();
        String condition;
        if (term.range.type == ServiceDocumentDescription.TypeName.LONG) {
            condition = createLongRangeQuery(term.propertyName, term.range, cd);
        } else if (term.range.type == ServiceDocumentDescription.TypeName.DOUBLE) {
            condition = createDoubleRangeQuery(term.propertyName, term.range, cd);
        } else if (term.range.type == ServiceDocumentDescription.TypeName.DATE) {
            // Date specifications must be in microseconds since epoch
            condition = createLongRangeQuery(term.propertyName, term.range, cd);
        } else {
            throw new IllegalArgumentException("Type is not supported:"
                    + term.range.type);
        }

        return condition;
    }

    private static ColumnDescription getColumnDescription(TableDescription td,
            String propertyName) {
        ColumnDescription cd = td.getColumnDescription(propertyName);
        if (cd != null) {
            return cd;
        }

        int i = propertyName.length() - 1;
        while ((i = propertyName.lastIndexOf('.', i)) != -1) {
            propertyName = propertyName.substring(0, i);
            cd = td.getColumnDescription(propertyName);
            if (cd != null) {
                return cd;
            }
            i--;
        }
        return null;
    }

    private static void convertToSqlBooleanQuery(StringBuilder sb, boolean first,
            QueryTask.Query query, QueryRuntimeContext context,
            TableDescription td) {
        // Recursively build the boolean query. We allow arbitrary nesting and grouping.
        if (query.booleanClauses.isEmpty()) {
            throw new IllegalArgumentException("Empty booleanClauses");
        }

        convertQueryOccurance(sb, first, query);

        boolean parentheses = query.booleanClauses.size() > 1;
        if (query.occurance == Occurance.MUST_NOT_OCCUR) {
            // "NOT (field = 'value')" evaluates to NULL if field is NULL, so here we make sure to
            // use "NOT (field IS NOT NULL AND field = 'value')" instead
            sb.append("COALESCE");
            parentheses = true;
        }

        if (parentheses) {
            sb.append('(');
        }
        int len = query.booleanClauses.size();
        for (int index = 0; index < len; index++) {
            Query q = query.booleanClauses.get(index);

            // Use a reducer to collapse matching property names
            if (q.booleanClauses == null
                    && q.term != null
                    && q.term.matchType == QueryTask.QueryTerm.MatchType.TERM
                    && q.term.propertyName != null
                    && !q.term.propertyName.equals(ServiceDocument.FIELD_NAME_KIND)
                    && !q.term.propertyName.equals(ServiceDocument.FIELD_NAME_SELF_LINK)
                    && q.term.matchValue != null
                    && index + 1 < len) {
                boolean skip = false;

                ColumnDescription cd = getColumnDescription(td, q.term.propertyName);
                boolean isCaseInsensitive = isCaseInsensitive(cd);
                if (cd == null || (isCaseInsensitive && isCollectionField(q))) {
                    skip = true;
                }

                if (!skip) {
                    StringBuilder values = null;
                    int valueCount = 0;
                    int initialIndex = index;

                    do {
                        // Check if next is the same as current query
                        Query next = query.booleanClauses.get(index + 1);
                        if (next.occurance != q.occurance
                                || next.booleanClauses != null
                                || next.term == null
                                || next.term.matchType != q.term.matchType
                                || !next.term.propertyName.equals(q.term.propertyName)
                                || next.term.matchValue == null) {
                            // No match, 'q' & 'next' still contains pending query to process
                            break;
                        }

                        if (values == null) {
                            values = new StringBuilder(q.term.matchValue.length()
                                    + next.term.matchValue.length() + 10);
                            values.append('\'')
                                    .append(escapeSqlString(
                                            isCaseInsensitive ? q.term.matchValue.toLowerCase()
                                                    : q.term.matchValue))
                                    .append('\'');
                            valueCount = 1;
                        }

                        values.append(',').append('\'')
                                .append(escapeSqlString(
                                        isCaseInsensitive ? next.term.matchValue.toLowerCase()
                                                : next.term.matchValue))
                                .append('\'');
                        valueCount++;
                        index++;
                    } while (index + 1 < len);

                    if (values != null) {
                        String condition;
                        if (isCollectionField(q)) {
                            if (cd.getParent() != null) {
                                cd = cd.getParent();
                            }
                            switch (q.occurance) {
                            case MUST_NOT_OCCUR:
                            case SHOULD_OCCUR:
                                condition = String.format("%s ?| ARRAY[%s]",
                                        wrapNativeField(q.term.propertyName, cd), values);
                                break;
                            case MUST_OCCUR:
                            default:
                                condition = String.format("%s ?& ARRAY[%s]",
                                        wrapNativeField(q.term.propertyName, cd), values);
                            }
                        } else {
                            String stringField = wrapStringField(q.term.propertyName, cd);
                            if (isCaseInsensitive) {
                                stringField = String.format("LOWER(%s)", stringField);
                            }
                            switch (q.occurance) {
                            case MUST_NOT_OCCUR:
                            case SHOULD_OCCUR:
                                condition = String.format("%s = ANY(ARRAY[%s])", stringField,
                                        values);
                                break;
                            case MUST_OCCUR:
                            default:
                                if (valueCount == 1) {
                                    if (cd.isNativeColumn()) {
                                        condition = String.format("%s = %s", stringField, values);
                                    } else {
                                        condition = String
                                                .format("%s @> ARRAY[%s]", stringField, values);
                                    }
                                } else {
                                    // This should not happen, value equals to different values
                                    condition = SQL_FALSE;
                                }
                            }
                        }

                        convertQueryCondition(sb, initialIndex == 0, q, condition);
                        continue;
                    }
                }
            }

            convertToPostgresQuery(sb, index == 0, q, context, td);
        }
        if (query.occurance == Occurance.MUST_NOT_OCCUR) {
            sb.append(", FALSE");
        }
        if (parentheses) {
            sb.append(')');
        }
    }

    private static void convertQueryOccurance(StringBuilder sb, boolean first, Query query) {
        if (query.occurance == null) {
            query.occurance = Occurance.MUST_OCCUR;
        }

        switch (query.occurance) {
        case MUST_NOT_OCCUR:
            if (!first) {
                sb.append(" AND ");
            }
            sb.append("NOT ");
            break;
        case SHOULD_OCCUR:
            if (!first) {
                sb.append(" OR ");
            }
            break;
        case MUST_OCCUR:
        default:
            if (!first) {
                sb.append(" AND ");
            }
        }
    }

    private static void validateTerm(QueryTask.QueryTerm term) {
        if (term.range == null && term.matchValue == null) {
            throw new IllegalArgumentException(
                    "One of term.matchValue, term.range is required");
        }

        if (term.range != null && term.matchValue != null) {
            throw new IllegalArgumentException(
                    "term.matchValue and term.range are exclusive of each other");
        }

        if (term.propertyName == null) {
            throw new IllegalArgumentException("term.propertyName is required");
        }
    }

    static String convertToPostgresGroupField(QueryTask.QueryTerm term, TableDescription td) {
        return convertToPostgresSortField(term, false, td);
    }

    static List<String> convertToPostgresSort(QueryTask.QuerySpecification querySpecification,
            boolean isGroupSort, TableDescription td, boolean ensureDocumentSelfLinkListed) {
        QueryTask.QueryTerm sortTerm = isGroupSort ? querySpecification.groupSortTerm
                : querySpecification.sortTerm;

        QueryTask.QuerySpecification.SortOrder sortOrder = isGroupSort
                ? querySpecification.groupSortOrder
                : querySpecification.sortOrder;

        if (querySpecification.options.contains(QueryOption.TOP_RESULTS)) {
            if (querySpecification.resultLimit <= 0
                    || querySpecification.resultLimit == Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "resultLimit should be a positive integer less than MAX_VALUE");
            }
        }

        if (sortOrder == null) {
            if (isGroupSort) {
                querySpecification.groupSortOrder = QueryTask.QuerySpecification.SortOrder.ASC;
            } else {
                querySpecification.sortOrder = QueryTask.QuerySpecification.SortOrder.ASC;
            }
        }

        sortTerm.sortOrder = sortOrder;

        List<QueryTask.QueryTerm> additionalSortTerms = isGroupSort
                ? querySpecification.additionalGroupSortTerms
                : querySpecification.additionalSortTerms;

        // Make sure return list have extra allocation for adding documentselflink if needed
        int allocationSize = additionalSortTerms == null ? 2 : additionalSortTerms.size() + 2;
        List<String> sortFields = new ArrayList<>(allocationSize);

        sortFields.add(convertToPostgresSortField(sortTerm, true, td));
        boolean addDocumentSelfLink = !sortTerm.propertyName
                .equals(ServiceDocument.FIELD_NAME_SELF_LINK);

        if (additionalSortTerms != null) {
            int len = additionalSortTerms.size() + 1;
            for (int index = 1; index < len; index++) {
                QueryTask.QueryTerm qt = additionalSortTerms.get(index - 1);
                sortFields.add(convertToPostgresSortField(qt, true, td));
                if (qt.propertyName.equals(ServiceDocument.FIELD_NAME_SELF_LINK)) {
                    addDocumentSelfLink = false;
                }
            }
        }

        if (ensureDocumentSelfLinkListed && addDocumentSelfLink) {
            sortFields.add("documentselflink "
                    + (sortOrder == QuerySpecification.SortOrder.ASC ? "ASC" : "DESC"));
        }
        return sortFields;
    }

    private static String convertToPostgresSortField(QueryTask.QueryTerm sortTerm,
            boolean includeSortOrder, TableDescription td) {
        validateSortTerm(sortTerm);

        ColumnDescription cd = td.getColumnDescription(sortTerm.propertyName);
        if (sortTerm.sortOrder == null) {
            sortTerm.sortOrder = QueryTask.QuerySpecification.SortOrder.ASC;
        }

        String sortField;
        switch (sortTerm.propertyType) {
        case BOOLEAN:
        case DOUBLE:
        case LONG:
            sortField = wrapNativeField(sortTerm.propertyName, cd);
            break;
        default:
            sortField = wrapStringField(sortTerm.propertyName, cd);
            if (isCaseInsensitive(cd)) {
                sortField = String.format("LOWER(%s)", sortField);
            }
        }

        if (includeSortOrder) {
            boolean ascending = sortTerm.sortOrder == QueryTask.QuerySpecification.SortOrder.ASC;
            sortField += ascending ? " ASC" : " DESC";
        }
        return sortField;
    }

    private static void validateSortTerm(QueryTask.QueryTerm term) {
        if (term.propertyType == null) {
            throw new IllegalArgumentException("term.propertyType is required");
        }

        if (term.propertyName == null) {
            throw new IllegalArgumentException("term.propertyName is required");
        }
    }

    private static String createLongRangeQuery(String propertyName, QueryTask.NumericRange<?> range,
            ColumnDescription cd) {
        // The range query constructed below is based-off
        // lucene documentation as per the link:
        // https://lucene.apache.org/core/6_0_0/core/org/apache/lucene/document/LongPoint.html
        long min = range.min == null ? Long.MIN_VALUE : range.min.longValue();
        long max = range.max == null ? Long.MAX_VALUE : range.max.longValue();
        if (!range.isMinInclusive) {
            min = Math.addExact(min, 1);
        }
        if (!range.isMaxInclusive) {
            max = Math.addExact(max, -1);
        }

        String intField = wrapField(propertyName, cd, "bigint");
        if (min == max) {
            return String.format("%s = %d", intField, max);
        }
        if (min > max) {
            // TODO: Why need to swap while using BETWEEN?
            long t = min;
            min = max;
            max = t;
        }
        return String.format("%s BETWEEN %d AND %d", intField, min, max);
    }

    private static String createDoubleRangeQuery(String propertyName,
            QueryTask.NumericRange<?> range,
            ColumnDescription cd) {
        if (range.min == null && range.max == null) {
            return SQL_TRUE;
        }
        // The range query constructed below is based-off
        // lucene documentation as per the link:
        // https://lucene.apache.org/core/6_0_0/core/org/apache/lucene/document/DoublePoint.html
        double min = range.min == null ? Double.NEGATIVE_INFINITY : range.min.doubleValue();
        double max = range.max == null ? Double.POSITIVE_INFINITY : range.max.doubleValue();
        if (!range.isMinInclusive && min != Double.NEGATIVE_INFINITY) {
            min = Math.nextUp(min);
        }
        if (!range.isMaxInclusive && max != Double.POSITIVE_INFINITY) {
            max = Math.nextDown(max);
        }
        String numericField = wrapField(propertyName, cd, "numeric");
        if (min == max) {
            return String.format("%s = %s", numericField, max);
        }
        if (min > max) {
            // TODO: Why need to swap while using BETWEEN?
            double t = min;
            min = max;
            max = t;
        }

        if (min == Double.NEGATIVE_INFINITY) {
            return String.format("%s <= %s", numericField, max);
        }
        if (max == Double.POSITIVE_INFINITY) {
            return String.format("%s >= %s", numericField, min);
        }
        return String.format("%s BETWEEN %s AND %s", numericField, min, max);
    }

    static String escapeSqlString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("'", "''");
    }

    private static boolean isCollectionField(Query q) {
        return isCollectionField(q.term.propertyName);
    }

    private static boolean isCollectionField(String propertyName) {
        return propertyName.endsWith(QuerySpecification.FIELD_NAME_CHARACTER
                + QuerySpecification.COLLECTION_FIELD_SUFFIX);
    }

    private static boolean isCaseInsensitive(ColumnDescription cd) {
        return cd != null && cd.getPropertyDescription().indexingOptions
                .contains(PropertyIndexingOption.CASE_INSENSITIVE);
    }

    private static boolean isTextIndexingOption(ColumnDescription cd) {
        return cd != null && cd.getPropertyDescription().indexingOptions
                .contains(PropertyIndexingOption.TEXT);
    }

    private static String escapeJsonString(String s) {
        return s.replace("'", "''")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static String normalizePropertyName(String s) {
        s = s.replace(QuerySpecification.FIELD_NAME_CHARACTER
                + QuerySpecification.COLLECTION_FIELD_SUFFIX
                + QuerySpecification.FIELD_NAME_CHARACTER,
                QuerySpecification.FIELD_NAME_CHARACTER);

        if (s.endsWith(QuerySpecification.FIELD_NAME_CHARACTER
                + QuerySpecification.COLLECTION_FIELD_SUFFIX)) {
            s = s.substring(0, s.length() - (QuerySpecification.FIELD_NAME_CHARACTER
                    + QuerySpecification.COLLECTION_FIELD_SUFFIX).length());
        }

        return s;
    }

    private static boolean isTextType(String type) {
        return "text".equals(type);
    }

    private static boolean isJsonbType(String type) {
        return "jsonb".equals(type);
    }

    private static Query reduceQuery(Query query, TableDescription td) {
        if (query.occurance == null) {
            query.occurance = Query.Occurance.MUST_OCCUR;
        }

        if (query.booleanClauses != null) {
            if (query.term != null) {
                throw new IllegalArgumentException(
                        "term and booleanClauses are mutually exclusive");
            }

            return reduceBooleanQuery(query, td);
        }

        QueryTask.QueryTerm term = query.term;
        if (term == null) {
            throw new IllegalArgumentException("One of term, booleanClauses must be provided");
        }

        validateTerm(term);

        if (term.matchType == null) {
            term.matchType = QueryTask.QueryTerm.MatchType.TERM;
        }

        Boolean result = null;
        if (term.matchType == QueryTask.QueryTerm.MatchType.WILDCARD) {
            // if the query is a wildcard on a self link that matches all documents, then
            // special case the query to a MatchAllDocsQuery to avoid looking through
            // the entire index as the number of terms is equal to the size of the index
            if (term.propertyName.equals(ServiceDocument.FIELD_NAME_SELF_LINK)
                    && term.matchValue.equals(UriUtils.URI_WILDCARD_CHAR)) {
                result = true;
            } else {
                int firstWildcardChar = term.matchValue.indexOf('*');
                int firstExactChar = term.matchValue.indexOf('?');

                if (firstWildcardChar == -1 && firstExactChar == -1) {
                    // Not wildcard search, use TERM match
                    term.matchType = QueryTask.QueryTerm.MatchType.TERM;
                } else if (firstWildcardChar == term.matchValue.length() - 1
                        && firstExactChar == -1) {
                    // Wildcard char in end of match, use PREFIX match
                    term.matchType = QueryTask.QueryTerm.MatchType.PREFIX;
                    term.matchValue = term.matchValue.substring(0, firstWildcardChar);
                }
            }
        }

        if (term.matchType == QueryTask.QueryTerm.MatchType.TERM) {
            if (term.range != null) {
                if (term.range.min == null && term.range.max == null) {
                    result = true;
                }
            } else {
                if (term.propertyName.equals(ServiceDocument.FIELD_NAME_SELF_LINK)
                        && !term.matchValue.startsWith(td.getFactoryLink())) {
                    result = false;
                } else if (term.propertyName.equals(ServiceDocument.FIELD_NAME_KIND)) {
                    result = term.matchValue.equals(td.getDocumentKind());
                }
            }
        } else if (term.matchType == QueryTask.QueryTerm.MatchType.PREFIX) {
            // if the query is a prefix on a self link that matches all documents, then
            // special case the query to a MatchAllDocsQuery to avoid looking through
            // the entire index as the number of terms is equal to the size of the index
            if (term.propertyName.equals(ServiceDocument.FIELD_NAME_SELF_LINK)) {
                if (term.matchValue.equals(UriUtils.URI_PATH_CHAR)) {
                    result = true;
                } else {
                    if (term.matchValue.length() <= td.getFactoryLinkWithTrailingSlash().length()) {
                        result = td.getFactoryLinkWithTrailingSlash().startsWith(term.matchValue);
                    } else if (!term.matchValue.startsWith(td.getFactoryLink())) {
                        result = false;
                    }
                }
            }
        }

        if (result == null) {
            ColumnDescription cd = getColumnDescription(td, term.propertyName);
            if (cd == null) {
                result = false;
            }
        }

        if (result != null) {
            return newQuery(query.occurance, result ? QUERY_TERM_TRUE : QUERY_TERM_FALSE, null);
        }

        return query;
    }

    private static Query reduceBooleanQuery(Query query, TableDescription td) {
        if (query.booleanClauses.isEmpty()) {
            throw new IllegalArgumentException("Empty booleanClauses");
        }

        List<Query> booleanClauses = null;
        Query last = null;
        boolean sameOccurance = true;
        boolean allFalse = true;
        boolean allShouldOccurClauses = true;
        for (Query q : query.booleanClauses) {
            q = reduceQuery(q, td);

            // Check if all queries have the same occurance
            if (last != null && sameOccurance) {
                sameOccurance = last.occurance == q.occurance;
            }
            if (q.occurance != Occurance.SHOULD_OCCUR) {
                allShouldOccurClauses = false;
            }
            if (q.term == QUERY_TERM_TRUE) {
                allFalse = false;
                // Avoid adding 'AND TRUE'
                if (q.occurance == Occurance.MUST_OCCUR) {
                    continue;
                }
                if (sameOccurance) {
                    // Return TRUE if 'OR TRUE' and all leading conditions are 'OR'
                    if (q.occurance == Occurance.SHOULD_OCCUR) {
                        return newQuery(query.occurance, QUERY_TERM_TRUE, null);
                    }
                    // return FALSE if 'AND NOT TRUE' and all leading conditions are 'AND NOT'
                    if (q.occurance == Occurance.MUST_NOT_OCCUR) {
                        return newQuery(query.occurance, QUERY_TERM_FALSE, null);
                    }
                }
            } else if (q.term == QUERY_TERM_FALSE) {
                // Avoid adding 'AND NOT FALSE' or 'OR FALSE'
                if (q.occurance != Occurance.MUST_OCCUR) {
                    continue;
                }
                if (sameOccurance) {
                    // return FALSE if 'AND FALSE' and all leading conditions are 'AND'
                    return newQuery(query.occurance, QUERY_TERM_FALSE, null);
                }
            }

            if (booleanClauses == null) {
                booleanClauses = new ArrayList<>(query.booleanClauses.size());
            }

            booleanClauses.add(q);
            last = q;
        }

        if (booleanClauses == null) {
            // return TRUE if 'AND ()' and no should occur clauses
            if (query.occurance == Occurance.MUST_OCCUR && !allShouldOccurClauses) {
                return newQuery(Occurance.MUST_OCCUR, QUERY_TERM_TRUE, null);
            }

            // return TRUE if 'OR ()' and not all FALSE conditions
            if (query.occurance == Occurance.SHOULD_OCCUR && !allFalse) {
                return newQuery(Occurance.SHOULD_OCCUR, QUERY_TERM_TRUE, null);
            }

            // return FALSE if 'OR ()' or 'MUST NOT ()'
            return newQuery(query.occurance, QUERY_TERM_FALSE, null);
        }

        if (booleanClauses.size() == 1) {
            // return x with query occurance if 'OR (x)' or 'AND (x)'
            if (last.occurance != Occurance.MUST_NOT_OCCUR) {
                return newQuery(query.occurance, last.term, last.booleanClauses);
            }

            // return x with AND occurance if term occurance same as query occurance
            // AND (AND x) -> AND x
            // OR (OR x) -> AND x
            // NOT (NOT x) -> AND x
            if (last.occurance == query.occurance) {
                return newQuery(Occurance.MUST_OCCUR, last.term, last.booleanClauses);
            }

            if (query.occurance == Occurance.MUST_OCCUR) {
                return newQuery(Occurance.MUST_NOT_OCCUR, last.term, last.booleanClauses);
            }
        }

        if (booleanClauses.size() > 1) {
            if (last.occurance != Occurance.MUST_NOT_OCCUR) {
                int count = countMatchingOccurance(last.occurance, booleanClauses);
                // Check if all clauses and children have same occurance, and get total count
                if (count > booleanClauses.size()) {
                    List<Query> newBooleanClauses = new ArrayList<>(count);
                    getAllClauses(booleanClauses, newBooleanClauses);
                    booleanClauses = newBooleanClauses;
                }
            }

            removeDuplicates(booleanClauses);
        }

        return newQuery(query.occurance, null, booleanClauses);
    }

    private static void removeDuplicates(List<Query> booleanClauses) {
        // Not the most efficient but might be good enough for now since we are using small lists
        // TODO: Review efficiency
        int len = booleanClauses.size();
        for (int i = 0; i < len - 1; i++) {
            Query first = booleanClauses.get(i);
            if (first.term != null) {
                for (int j = i + 1; j < len;) {
                    Query second = booleanClauses.get(j);
                    if (first.occurance != second.occurance) {
                        break;
                    }
                    if (second.term != null
                            && first.term.propertyName.equals(second.term.propertyName)
                            && first.term.range == null && second.term.range == null
                            && first.term.matchType.equals(second.term.matchType)
                            && first.term.matchValue.equals(second.term.matchValue)) {
                        booleanClauses.remove(j);
                        len--;
                    } else {
                        j++;
                    }
                }
            }
        }
    }

    private static Query newQuery(Occurance occurance, QueryTask.QueryTerm term,
            List<Query> booleanClauses) {
        Query query = new Query();
        query.occurance = occurance;
        query.term = term;
        query.booleanClauses = booleanClauses;
        return query;
    }

    private static int countMatchingOccurance(Occurance occurance, List<Query> booleanClauses) {
        int total = 0;
        for (Query q : booleanClauses) {
            if (q.occurance != occurance) {
                return 0;
            }
            if (q.booleanClauses != null) {
                int count = countMatchingOccurance(occurance, q.booleanClauses);
                if (count == 0) {
                    return 0;
                }
                total += count;
            } else {
                total++;
            }
        }
        return total;
    }

    private static void getAllClauses(List<Query> booleanClauses, List<Query> newBooleanClauses) {
        for (Query q : booleanClauses) {
            if (q.booleanClauses != null) {
                getAllClauses(q.booleanClauses, newBooleanClauses);
            } else {
                newBooleanClauses.add(q);
            }
        }
    }

    static String buildPaginationClause(TableDescription td, List<String> sortFields,
            JsonObject bottom) {
        StringBuilder names = new StringBuilder();
        StringBuilder values = new StringBuilder();
        boolean ascending = true;

        for (String field : sortFields) {
            field = field.trim();
            int i = field.lastIndexOf(' ');
            String columnName = field.substring(0, i);
            if (names.length() == 0) {
                String order = field.substring(i + 1);
                ascending = order.equals("ASC");
            }

            String propertyName;
            i = columnName.indexOf('\'');
            if (i > 0) {
                propertyName = columnName.substring(i + 1, field.lastIndexOf('\''));
            } else {
                propertyName = columnName;
            }

            JsonElement value = null;
            if (propertyName.startsWith("{") && propertyName.endsWith("}")) {
                String[] list = propertyName.substring(1, propertyName.length() - 1)
                        .split(",");
                value = bottom;
                for (String s : list) {
                    if (!value.isJsonObject()) {
                        value = null;
                        break;
                    }
                    value = value.getAsJsonObject().get(s);
                }
            } else if (propertyName.startsWith("document")) {
                for (Map.Entry<String, JsonElement> element : bottom.entrySet()) {
                    if (element.getKey().equalsIgnoreCase(propertyName)) {
                        value = element.getValue();
                        break;
                    }
                }
            } else {
                value = bottom.get(propertyName);
            }
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                // This should not happen
                continue;
            }
            if (names.length() > 0) {
                names.append(',');
                values.append(',');
            }

            names.append(columnName);

            if (!value.getAsJsonPrimitive().isNumber()) {
                String strValue = value.getAsString();
                ColumnDescription cd = PostgresQueryConverter.getColumnDescription(td,
                        propertyName);
                // Make sure to check against lowercase if property is case insensitive
                if (isCaseInsensitive(cd) && strValue != null) {
                    strValue = strValue.toLowerCase();
                }
                values.append('\'')
                        .append(PostgresQueryConverter.escapeSqlString(strValue))
                        .append('\'');
            } else {
                values.append(value.getAsString());
            }
        }

        // TODO: This will not work if additionalSortTerms is used with different sort order
        return String.format("(%s) %s (%s)", names, ascending ? ">" : "<", values);
    }

    static boolean isSqlFalse(String tq) {
        return tq.equals(SQL_FALSE) || tq.equals('(' + SQL_FALSE + ')');
    }

    static boolean isSqlTrue(String tq) {
        return tq.equals(SQL_TRUE) || tq.equals('(' + SQL_TRUE + ')');
    }

}
