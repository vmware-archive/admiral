/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.xenon.services.rdbms;

import static com.vmware.xenon.services.rdbms.PostgresQueryConverter.SQL_TRUE;
import static com.vmware.xenon.services.rdbms.PostgresQueryConverter.isSqlFalse;
import static com.vmware.xenon.services.rdbms.PostgresQueryConverter.isSqlTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.vmware.xenon.common.NodeSelectorService.SelectOwnerResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.config.XenonConfiguration;
import com.vmware.xenon.common.serialization.GsonSerializers;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.rdbms.PostgresQueryPageService.PostgresQueryPage;
import com.vmware.xenon.services.rdbms.PostgresSchemaManager.TableDescription;

final class PostgresServiceDocumentDao implements PostgresServiceDocumentDaoMXBean {
    private static final Logger logger = Logger
            .getLogger(PostgresServiceDocumentDao.class.getName());

    private static final String RESOURCE_PER_DOCUMENT_TABLE_TEMPLATE = "sql/per_document_table_template.sql";
    private static final String SQL_TEMPLATE_VAR_TABLE_NAME = "\\$tableName";

    private boolean isDetailedLoggingEnabled = XenonConfiguration.bool(
            PostgresServiceDocumentDao.class,
            "isDetailedLoggingEnabled",
            false);

    static final int FETCH_SIZE = XenonConfiguration.integer(
            PostgresServiceDocumentDao.class,
            "fetchSize",
            100);

    // TODO: Expire old deleted documents without expiration
    // Old documented got purged during migration if not migrating deleted documents
    private final boolean isSoftDeleteEnabled = XenonConfiguration.bool(
            PostgresServiceDocumentDao.class,
            "isSoftDeleteEnabled",
            true);

    private static final String DUMP_QUERY_DIRECTORY = XenonConfiguration.string(
            PostgresServiceDocumentDao.class,
            "dumpQueryDirectory",
            null);

    private long logSlowQueryThresholdMicros = TimeUnit.SECONDS.toMicros(XenonConfiguration.integer(
            PostgresServiceDocumentDao.class,
            "logSlowQueriesThresholdSeconds",
            0));

    private static final String DOCUMENTS_WITHOUT_RESULTS = "DocumentsWithoutResults";

    private static final String SQL_UPSERT;
    private static final String SQL_UPSERT_FORCE_UPDATE;

    static {
        List<String> insertFields = Arrays.asList("data", "documentselflink", "documentversion",
                "documentkind", "documentexpirationtimemicros",
                "documentupdatetimemicros", "documenttransactionid",
                "documentauthprincipallink", "documentupdateaction");

        List<String> updateFields = insertFields.stream()
                .filter(field -> !field.equals("documentselflink")
                        && !field.equals("documentkind"))
                .collect(Collectors.toList());

        List<String> excludedFields = updateFields.stream()
                .map(field -> "excluded." + field)
                .collect(Collectors.toList());

        SQL_UPSERT_FORCE_UPDATE = String.format(
                "INSERT INTO %%s as doc (%s) VALUES (?::jsonb,?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT (documentselflink) DO " +
                        "UPDATE SET (%s) = (%s)",
                String.join(",", insertFields),
                String.join(",", updateFields),
                String.join(",", excludedFields));

        SQL_UPSERT = SQL_UPSERT_FORCE_UPDATE
                + " WHERE doc.documentversion < EXCLUDED.documentversion";
    }

    private final ServiceHost host;
    private final Service service;
    private final DataSource ds;
    private final PostgresSchemaManager schemaManager;

    // Used as a filename prefix when saving query information to filesystem. The time-based prefix
    // is used to avoid overriding files on node restart.
    private final long startTimeMillis = System.currentTimeMillis();

    public PostgresServiceDocumentDao(ServiceHost host, Service service, DataSource ds) {
        this.host = host;
        this.service = service;
        this.ds = ds;
        this.schemaManager = new PostgresSchemaManager(host);
        registerMBeans();
    }

    public PostgresSchemaManager getPostgresSchemaManager() {
        return this.schemaManager;
    }

    /**
     * Performs SQL initialization for the given document type, e.g. creating a dedicated table for
     * it, etc.
     *
     * Expected to be called once per document type upon host startup.
     */
    public void initForDocument(String factoryLink, Class<? extends ServiceDocument> documentType,
            ServiceDocumentDescription sdd) {
        this.schemaManager.addFactory(factoryLink, documentType, sdd);

        TableDescription desc = this.schemaManager.getTableDescriptionForFactoryLink(factoryLink);
        if (desc != null) {
            ensureTableExists(desc);
        }
    }

    private void ensureTableExists(TableDescription desc) {
        // load the create table SQL statement template
        String sqlCreateTableTemplate;
        try {
            sqlCreateTableTemplate = PostgresHostUtils.getResourceAsString(
                    PostgresServiceDocumentDao.class.getClassLoader(),
                    RESOURCE_PER_DOCUMENT_TABLE_TEMPLATE);
        } catch (IOException e) {
            logger.severe(() -> String.format("Error reading SQL resource: %s", e));
            throw new AssertionError(e);
        }

        // replace the table name into the template
        String tableName = desc.getTableName();

        final String createTableStatement = sqlCreateTableTemplate.replaceAll(
                SQL_TEMPLATE_VAR_TABLE_NAME, tableName);

        // execute the create table statement
        try (Connection conn = this.ds.getConnection(); Statement stmt = conn.createStatement()) {
            int response = stmt.executeUpdate(createTableStatement);

            logger.info(() -> String.format("Created table %s, response code: %s",
                    tableName, response));
        } catch (SQLException e) {
            if (e.getMessage().contains("already exists") || e.getMessage()
                    .contains("pg_type_typname_nsp_index")) {
                logger.info(() -> String.format("Table %s seems to already exist: %s", tableName,
                        e));
            } else {
                logger.severe(() -> String.format("Cannot create table %s: %s",
                        tableName, e.getMessage()));
                throw new IllegalStateException("Cannot create table " + tableName, e);
            }
        }
    }

    /**
     * Find the document given a self link .
     *
     * This function is used for two purposes; find given version to... 1) load state if the service
     * state is not yet cached 2) filter query results to only include the given version
     *
     * In case (1), authorization is applied in the service host (either against the cached state or
     * freshly loaded state). In case (2), authorization should NOT be applied because the original
     * query already included the resource group query per the authorization context. Query results
     * will be filtered given the REAL latest version, not the latest version subject to the
     * resource group query. This means older versions of a document will NOT appear in the query
     * result if the user is not authorized to see the newer version.
     */
    public ServiceDocument loadDocument(String selfLink) throws SQLException {
        TableDescription tableDescription = this.schemaManager
                .getTableDescriptionForDocumentSelfLink(selfLink);
        if (tableDescription == null) {
            logger.severe(String.format("Cannot determine SQL table name for document %s",
                    selfLink));
            return null;
        }

        String sql = String
                .format("SELECT data FROM %s WHERE documentselflink = ? AND (documentexpirationtimemicros = 0 OR documentexpirationtimemicros > ?)",
                        tableDescription.getTableName());

        PostgresDocumentStoredFieldVisitor visitor;
        try (Connection conn = this.ds.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, selfLink);
            stmt.setLong(2, Utils.getSystemNowMicrosUtc());

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    if (isDetailedLoggingEnabled) {
                        logger.info(String.format("SQL loadDocument NOT FOUND: %s", selfLink));
                    }
                    return null;
                }

                visitor = new PostgresDocumentStoredFieldVisitor();
                loadDoc(visitor, rs);

                if (isDetailedLoggingEnabled) {
                    logger.info(String.format("SQL loadDocument: %s : ver=%d documentOwner=%s",
                            selfLink, visitor.documentVersion, visitor.documentOwner));
                }
            }
        } catch (SQLException e) {
            logger.severe(String.format("SQL loadDocument failed: %s : %s", selfLink, e));
            throw e;
        }

        Long expiration = visitor.documentExpirationTimeMicros;
        boolean hasExpired = expiration != null
                && expiration != 0
                && expiration <= Utils.getSystemNowMicrosUtc();

        if (hasExpired) {
            return null;
        }

        return getStateFromPostgresDocument(tableDescription, visitor, selfLink);
    }

    public void saveDocument(ServiceDocument sd, ServiceDocumentDescription sdd,
            boolean forceIndexUpdate) throws SQLException {
        String tableName = this.schemaManager.getTableNameForDocumentSelfLink(sd.documentSelfLink);
        if (tableName == null) {
            throw new IllegalArgumentException("Cannot determine SQL table name for document: "
                    + sd.documentSelfLink);
        }

        // Delete document if action is DELETE and soft delete is disabled
        // TODO: Need to check behavior when upset is after DELETE and not using soft delete
        boolean delete = Action.DELETE.name().equals(sd.documentUpdateAction)
                && !this.isSoftDeleteEnabled;

        if (delete) {
            // SQL DELETE
            if (isDetailedLoggingEnabled) {
                logger.info(() -> String.format("SQL delete: %s : ver=%s documentOwner=%s",
                        sd.documentSelfLink, sd.documentVersion, sd.documentOwner));
            }
            String sql = String.format("DELETE FROM %s WHERE documentselflink = ?", tableName);
            try (Connection conn = this.ds.getConnection();
                    PreparedStatement delStmt = conn.prepareStatement(sql)) {
                delStmt.setString(1, sd.documentSelfLink);
                delStmt.executeUpdate();
                // TODO: Need to call service delete
            } catch (Exception e) {
                logger.severe(String
                        .format("Failed SQL delete: %s : ver=%s documentOwner=%s : %s",
                                sd.documentSelfLink, sd.documentVersion, sd.documentOwner,
                                Utils.toString(e)));
                throw e;
            }
            return;
        }

        // SQL UPSERT
        // Make sure we are not updating a version that is newer than current for
        // non-forced index update operations
        String sql = String.format(forceIndexUpdate ? SQL_UPSERT_FORCE_UPDATE : SQL_UPSERT,
                tableName);
        String json = Utils.toJson(sd);

        // Calling Utils.getBuilder() to make sure to trim large buffers from staying in the
        // indexing service thread pool
        // TODO: find alternative to make sure buffer is cleared from thread
        if (json.length() > 10 * 1024) {
            Utils.getBuilder();
        }

        try (Connection conn = this.ds.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, json);
            stmt.setString(2, sd.documentSelfLink);
            stmt.setLong(3, sd.documentVersion);
            stmt.setString(4, sd.documentKind);
            stmt.setLong(5, sd.documentExpirationTimeMicros);
            stmt.setLong(6, sd.documentUpdateTimeMicros);
            stmt.setString(7, sd.documentTransactionId);
            stmt.setString(8, sd.documentAuthPrincipalLink);
            stmt.setString(9, sd.documentUpdateAction);

            if (stmt.executeUpdate() == 0) {
                // TODO: Fail operation?
                logger.log(Level.WARNING, String.format(
                        "Ignored SQL upsert: %s : ver=%s documentOwner=%s : most probably a newer version is already stored",
                        sd.documentSelfLink, sd.documentVersion, sd.documentOwner));
            } else if (isDetailedLoggingEnabled) {
                logger.info(String.format(
                        "SQL upsert: %s : ver=%s documentOwner=%s documentExpirationTimeMicros=%s table=%s",
                        sd.documentSelfLink, sd.documentVersion, sd.documentOwner,
                        sd.documentExpirationTimeMicros, tableName));
            }
        } catch (Exception e) {
            logger.severe(String.format("Failed SQL upsert: %s : ver=%s documentOwner=%s : %s",
                    sd.documentSelfLink, sd.documentVersion, sd.documentOwner, Utils.toString(e)));
            throw e;
        }
    }

    public void deleteDocument(Connection conn, String tableName, String documentSelfLink)
            throws SQLException {
        String sql = String.format("DELETE FROM %s WHERE documentselflink = ?", tableName);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, documentSelfLink);
            int rows = stmt.executeUpdate();
            if (isDetailedLoggingEnabled) {
                logger.info(() -> String.format("SQL delete: %s from %s, rows=%d",
                        documentSelfLink, tableName, rows));
            }
        } catch (SQLException e) {
            logger.severe(String.format("Failed SQL delete: %s from %s : %s", documentSelfLink,
                    tableName, e));
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public ServiceDocumentQueryResult queryDocuments(Operation op, QueryTask task)
            throws Exception {
        QuerySpecification qs = task.querySpec;
        if (qs.context.kindScope == null) {
            // This will return one or more kinds
            qs.context.kindScope = getKindScope(qs);
        }

        Set<TableDescription> tables = (Set<TableDescription>) qs.context.nativeSearcher;
        if (tables == null) {
            tables = kindScopeToTableDescriptions(qs.context.kindScope);
            qs.context.nativeSearcher = tables;
        }

        if (tables.isEmpty()) {
            return null;
        }

        List<String> sortFields = (List<String>) qs.context.nativeSort;
        PostgresQueryPage postgresPage = (PostgresQueryPage) qs.context.nativePage;

        if (sortFields == null && task.querySpec.options != null
                && task.querySpec.options.contains(QuerySpecification.QueryOption.SORT)) {
            // If having multiple scopes, we are getting first description which should be good
            // enough
            // since we are using a union, which assume the documents have some common fields
            TableDescription td = tables.iterator().next();

            sortFields = PostgresQueryConverter.convertToPostgresSort(task.querySpec, false, td,
                    true);
            task.querySpec.context.nativeSort = sortFields;
        }

        return queryIndex(op, tables, null, postgresPage, task.querySpec.options,
                task.querySpec, task.querySpec.resultLimit, task.documentExpirationTimeMicros,
                task.nodeSelectorLink, task.indexLink);
    }

    public ServiceDocumentQueryResult queryBySelfLinkPrefix(Operation op, String selfLinkPrefix,
            EnumSet<QueryOption> options) throws Exception {
        TableDescription td = this.schemaManager
                .getTableDescriptionForFactoryLink(UriUtils.normalizeUriPath(selfLinkPrefix));
        if (td == null) {
            // this might be a non-persisted service, return null so that in-memory services are
            // queried instead
            logger.fine(() -> String.format(
                    "Cannot determine document kind for factory %s", selfLinkPrefix));
            return null;
        }

        final int resultLimit = Integer.MAX_VALUE;
        final String tq = SQL_TRUE;

        QuerySpecification qs = new QuerySpecification();
        qs.context.kindScope = Collections.singleton(td.getDocumentKind());
        qs.context.nativeQuery = tq;

        Set<TableDescription> tables = Collections.singleton(td);

        return queryIndex(op, tables, tq, null, options,
                qs, resultLimit, 0, null, null);
    }

    @SuppressWarnings("unchecked")
    private ServiceDocumentQueryResult queryGroupBy(Operation op,
            Set<TableDescription> tables,
            String tq, PostgresQueryPage page, EnumSet<QueryOption> options,
            QuerySpecification qs, int count, long expirationTimeMicros, String nodeSelectorLink,
            String indexLink, long queryStartTimeMicros) throws SQLException {
        ServiceDocumentQueryResult rsp = new ServiceDocumentQueryResult();
        rsp.nextPageLinksPerGroup = new TreeMap<>();

        TableDescription firstTable = tables.iterator().next();

        String groupBy = PostgresQueryConverter.convertToPostgresGroupField(qs.groupByTerm,
                firstTable);
        if (groupBy == null) {
            return rsp;
        }

        String orderBy;
        String fields;
        String sql;
        List<String> groupSortFields = null;
        if (qs.groupSortTerm != null) {
            groupSortFields = PostgresQueryConverter
                    .convertToPostgresSort(qs, true, firstTable, false);

            orderBy = groupSortFields.stream()
                    .collect(Collectors.joining(","));

            String additionalFields = groupSortFields.stream()
                    .map(s -> s.substring(0, s.lastIndexOf(' ')))
                    .filter(s -> !groupBy.equals(s))
                    .collect(Collectors.joining(","));
            if (!additionalFields.isEmpty()) {
                fields = groupBy + ',' + additionalFields;
            } else {
                fields = groupBy;
            }
        } else {
            orderBy = groupBy + " ASC";
            fields = groupBy;
        }

        // TODO: Use keyset pagination instead of OFFSET?
        int groupOffset = page != null ? page.groupOffset : 0;
        int groupLimit = qs.groupResultLimit != null ? qs.groupResultLimit
                : PostgresDocumentIndexService.queryResultLimit;

        if (tables.size() == 1) {
            TableDescription td = tables.iterator().next();
            String where = tq != null ? tq
                    : PostgresQueryConverter.convert(qs.query, qs.context, td);
            where = updateQuery(op, td, where, startTimeMillis, qs, true);
            if (where == null) {
                return rsp;
            }

            sql = String.format("SELECT %s FROM %s WHERE %s GROUP BY %s ORDER BY %s LIMIT %s",
                    fields, tables.iterator().next().getTableName(), where, groupBy, orderBy,
                    groupLimit + 1);
            if (groupOffset > 0) {
                sql += String.format(" OFFSET %d", groupOffset);
            }
        } else {
            Collection<String> tableSelects = tables.stream()
                    .map(td -> {
                        String where = tq != null ? tq
                                : PostgresQueryConverter.convert(qs.query, qs.context, td);
                        where = updateQuery(op, td, where, startTimeMillis, qs, true);
                        if (where == null) {
                            return null;
                        }

                        return String.format("SELECT %s FROM %s WHERE %s",
                                fields, td.getTableName(), where);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (tableSelects.isEmpty()) {
                return rsp;
            }
            String unionQuery = String.join(" UNION ALL ", tableSelects);
            sql = String.format("SELECT %s FROM (%s) AS docs GROUP BY %s ORDER BY %s LIMIT %s",
                    fields, unionQuery, groupBy, orderBy, groupLimit + 1);
            if (groupOffset > 0) {
                sql += String.format(" OFFSET %d", groupOffset);
            }
        }

        if (isDetailedLoggingEnabled) {
            logger.fine(() -> String.format("Xenon query specification: %s", Utils.toJsonHtml(qs)));
            logger.info(String.format(
                    "SQL query: \n>>>>>>>>>> SQL BEGIN >>>>>>>>>>\n%s\n<<<<<<<<<< SQL END <<<<<<<<<<\n",
                    prettySqlStatement(sql)));
        }

        long queryTime;
        try (Connection conn = this.ds.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                // Turn use of the cursor on.
                st.setFetchSize(FETCH_SIZE);
                long startMillis = System.currentTimeMillis();
                try (ResultSet rs = st.executeQuery(sql)) {
                    queryTime = System.currentTimeMillis() - startMillis;
                    if (isDetailedLoggingEnabled) {
                        logger.info(String.format("SQL query execution time: %d ms", queryTime));
                    }

                    while (rs.next()) {
                        if (rsp.nextPageLinksPerGroup.size() >= groupLimit) {
                            // check if we need to generate a next page for the next set of group
                            // results
                            rsp.nextPageLink = createNextPage(op, qs, tq, groupSortFields,
                                    null, 0, groupLimit + groupOffset,
                                    expirationTimeMicros, indexLink, nodeSelectorLink,
                                    page != null);
                            break;
                        }

                        // groupValue can be ANY OF ( GROUPS, null )
                        // The "null" group signifies documents that do not have the property.
                        String groupValue = rs.getString(1);

                        // we need to modify the query to include a top level clause that restricts
                        // scope
                        // to documents with the groupBy field and value
                        QueryTask.Query clause = new QueryTask.Query()
                                .setTermPropertyName(qs.groupByTerm.propertyName)
                                .setTermMatchType(QueryTask.QueryTerm.MatchType.TERM);
                        clause.occurance = QueryTask.Query.Occurance.MUST_OCCUR;

                        String origGroupValue = groupValue;
                        if (groupValue == null) {
                            groupValue = DOCUMENTS_WITHOUT_RESULTS;
                        }

                        if (qs.groupByTerm.propertyType == ServiceDocumentDescription.TypeName.LONG
                                && origGroupValue != null) {
                            clause.setNumericRange(QueryTask.NumericRange
                                    .createEqualRange(Long.parseLong(groupValue)));
                        } else if (qs.groupByTerm.propertyType == ServiceDocumentDescription.TypeName.DOUBLE
                                && origGroupValue != null) {
                            clause.setNumericRange(QueryTask.NumericRange
                                    .createEqualRange(Double.parseDouble(groupValue)));
                        } else {
                            clause.setTermMatchValue(groupValue);
                        }

                        QuerySpecification qsPerGroup = new QuerySpecification();
                        qs.copyTo(qsPerGroup);
                        qsPerGroup.query = new Query();
                        qsPerGroup.query.booleanClauses = new ArrayList<>(2);
                        qsPerGroup.query.addBooleanClause(qs.query);
                        qsPerGroup.query.addBooleanClause(clause);

                        // for each group generate a query page link
                        String pageLink = createNextPage(op, qsPerGroup, null, null,
                                null, 0, null,
                                expirationTimeMicros, indexLink, nodeSelectorLink, false);

                        rsp.nextPageLinksPerGroup.put(groupValue, pageLink);
                    }
                }
            }

            try {
                conn.rollback();
                conn.setAutoCommit(true);
            } catch (Exception ignore) {
                // Ignore
            }
        } catch (Exception e) {
            logger.severe(String.format("Error while querying: %s\nException: %s",
                    sql, Utils.toString(e)));
            throw e;
        }

        rsp.queryTimeMicros = Utils.getNowMicrosUtc() - queryStartTimeMicros;
        rsp.documentCount = 0L;

        if (isDetailedLoggingEnabled) {
            logger.info(
                    String.format("Group query result: %s", rsp.nextPageLinksPerGroup.keySet()));
        }

        logQuery(op, rsp, qs, sql, queryTime);

        return rsp;
    }

    private void logQuery(Operation op, ServiceDocumentQueryResult rsp, QuerySpecification qs,
            String sql, long queryTime) {
        if (isDebugQuery()) {
            appendDebugInfo(op,
                    "ExecuteQuery time ms: " + queryTime, null,
                    "ExecuteQuery count: " + rsp.documentCount, null,
                    "Total query time ms: " + TimeUnit.MICROSECONDS.toMillis(rsp.queryTimeMicros),
                    null,
                    "QuerySpecification", Utils.toJsonHtml(qs),
                    "SQL", prettySqlStatement(sql));
        }

        // Log slow queries
        if (this.logSlowQueryThresholdMicros > 0
                && rsp.queryTimeMicros > this.logSlowQueryThresholdMicros) {
            long totalQueryTimeMillis = TimeUnit.MICROSECONDS.toMillis(rsp.queryTimeMicros);

            Map<String, Object> map = new HashMap<>();
            map.put("totalQueryTimeMillis", totalQueryTimeMillis);
            map.put("executeQueryTimeMillis", queryTime);
            map.put("documentCount", rsp.documentCount);
            map.put("sql", sql);
            map.put("querySpecification", qs);

            logger.warning(String.format("Slow SQL Query, %d ms: %s", totalQueryTimeMillis,
                    Utils.toJson(map)));
        }
    }

    private ServiceDocumentQueryResult queryIndex(Operation op, Set<TableDescription> tables,
            String tq, PostgresQueryPage page, EnumSet<QueryOption> options,
            QuerySpecification qs, int resultLimit, long expirationTimeMicros,
            String nodeSelectorLink, String indexLink) throws Exception {
        if (options == null) {
            options = EnumSet.noneOf(QueryOption.class);
        }

        long queryStartTimeMicros = Utils.getNowMicrosUtc();
        // if (qs != null && qs.query != null && hasOption(ServiceOption.INSTRUMENTATION)) {
        // String queryStat = getQueryStatName(qs.query);
        // adjustStat(queryStat, 1);
        // }

        ServiceDocumentQueryResult result;
        if (options.contains(QueryOption.GROUP_BY)) {
            result = queryGroupBy(op, tables, tq, page, options, qs,
                    resultLimit, expirationTimeMicros, nodeSelectorLink, indexLink,
                    queryStartTimeMicros);
        } else if (options.contains(QueryOption.COUNT)) {
            result = queryIndexCount(op, qs, tables, tq, queryStartTimeMicros);
        } else {
            result = queryIndexPaginated(op, tables, tq, page, options, qs,
                    resultLimit, expirationTimeMicros, nodeSelectorLink, indexLink,
                    queryStartTimeMicros);
        }

        if (result.documentCount == null) {
            result.documentCount = 0L;
        }
        if (result.queryTimeMicros == null) {
            result.queryTimeMicros = Utils.getNowMicrosUtc() - queryStartTimeMicros;
        }
        if (result.documentOwner == null) {
            result.documentOwner = this.host.getId();
        }

        return result;
    }

    public void loadDoc(PostgresDocumentStoredFieldVisitor visitor, ResultSet rs)
            throws SQLException {
        String data = rs.getString(1);
        visitor.jsonSerializedState = data;

        JsonObject jsonObject = visitor.getAsJsonObject();
        jsonObject.entrySet().forEach(e -> {
            if (e.getValue().isJsonPrimitive()) {
                JsonPrimitive jsonPrimitive = e.getValue().getAsJsonPrimitive();
                if (jsonPrimitive.isString()) {
                    visitor.stringField(e.getKey(), jsonPrimitive.getAsString());
                } else if (jsonPrimitive.isNumber()) {
                    visitor.longField(e.getKey(), jsonPrimitive.getAsLong());
                }
            }
        });

        if (isDetailedLoggingEnabled) {
            logger.fine(
                    () -> String.format("Load document %s: %s", visitor.documentSelfLink, data));
        }
    }

    ServiceDocument getStateFromPostgresDocument(TableDescription tableDescription,
            PostgresDocumentStoredFieldVisitor visitor, String link) {
        JsonObject jsonObject = visitor.getAsJsonObject();
        if (jsonObject == null) {
            // This should not happen
            return null;
        }

        Class<? extends ServiceDocument> stateType;
        if (tableDescription != null) {
            stateType = tableDescription.getStateType();
        } else {
            stateType = this.schemaManager.getStateTypeForDocumentKind(visitor.documentKind);
            if (stateType == null) {
                // TODO: Remove after using registerPostgresSchema() in host
                stateType = getTypeFromKind(visitor.documentKind);
                if (stateType == null) {
                    // Return null if documentKind is not known, we should be able to return JSON
                    // after the content in the DB
                    return null;
                }
            }
        }
        // Check if kind was registered
        ServiceDocument state = Utils.fromJson(jsonObject, stateType);

        if (state.documentSelfLink == null) {
            state.documentSelfLink = link;
        }
        if (state.documentKind == null) {
            state.documentKind = Utils.buildKind(stateType);
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends ServiceDocument> getTypeFromKind(String documentKind) {
        // TODO: how to find class?
        // Check if kind was registered
        Class<?> clazz = Utils.getTypeFromKind(documentKind);
        if (clazz == null) {
            String className = documentKind.replace(':', '.');
            while (true) {
                try {
                    clazz = Class.forName(className);
                    break;
                } catch (ClassNotFoundException e) {
                    int i = className.lastIndexOf('.');
                    if (i == -1) {
                        logger.warning("State type not found for " + documentKind);
                        return null;
                    }

                    // Check if inner class, replace last '.' with '$'
                    StringBuilder sb = new StringBuilder(className);
                    sb.setCharAt(i, '$');
                    className = sb.toString();
                }
            }
            // Register kind
            Utils.registerKind(clazz, documentKind);
        }
        return (Class<? extends ServiceDocument>) clazz;
    }

    /**
     * This routine modifies a user-specified query to include clauses which apply the resource
     * group query specified by the operation's authorization context and which exclude expired
     * documents.
     *
     * If the operation was executed by the system user, no resource group query is applied.
     *
     * If no query needs to be executed return null
     *
     * @return Augmented query.
     */
    private String updateQuery(Operation op, TableDescription td, String tq, long now,
            QuerySpecification qs, boolean forceIncludeDeleted) {
        if (isSqlFalse(tq)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        if (!forceIncludeDeleted && !qs.options.contains(QueryOption.INCLUDE_DELETED)
                && !qs.options.contains(QueryOption.INCLUDE_ALL_VERSIONS)) {
            sb.append("documentupdateaction in ('POST','PATCH','PUT')");
            sb.append(String.format(
                    " AND (documentexpirationtimemicros = 0 OR documentexpirationtimemicros > %d)",
                    now));
        }

        if (qs.options.contains(QueryOption.TIME_SNAPSHOT)
                && qs.timeSnapshotBoundaryMicros != null
                && qs.timeSnapshotBoundaryMicros > 0) {
            if (sb.length() > 0) {
                sb.append(" AND ");
            }
            sb.append(String.format("documentupdatetimemicros <= %d",
                    qs.timeSnapshotBoundaryMicros));
        }

        if (this.host.isAuthorizationEnabled()) {
            AuthorizationContext ctx = op.getAuthorizationContext();
            if (ctx == null) {
                // Don't allow operation if no authorization context and auth is enabled
                return null;
            }

            // Allow unconditionally if this is the system user
            if (!ctx.isSystemUser()) {
                // If the resource query in the authorization context is unspecified,
                // use a Lucene query that doesn't return any documents so that every
                // result will be empty.
                QueryTask.Query resourceQuery = ctx.getResourceQuery(Action.GET);
                if (resourceQuery == null) {
                    return null;
                }

                // Use first table for any needed property descriptions
                String rq = PostgresQueryConverter.convert(resourceQuery, null, td);
                if (isSqlFalse(rq)) {
                    return null;
                }
                if (!isSqlTrue(rq)) {
                    if (sb.length() > 0) {
                        sb.append(" AND ");
                    }
                    sb.append(rq);

                    if (isDebugQuery()) {
                        appendDebugInfo(op,
                                "Auth Query", Utils.toJsonHtml(resourceQuery),
                                "Auth SQL", prettySqlStatement(sb.toString()));
                    }
                }
            }
        }
        if (sb.length() == 0) {
            return tq;
        }
        if (isSqlTrue(tq)) {
            return sb.toString();
        }
        return sb.toString() + " AND " + tq;
    }

    private ServiceDocumentQueryResult queryIndexCount(Operation op, QuerySpecification qs,
            Set<TableDescription> tables, String tq, long queryStartTimeMicros)
            throws SQLException {
        ServiceDocumentQueryResult response = new ServiceDocumentQueryResult();
        String sql;
        if (tables.size() > 1) {
            String countClauses = String.join(" + ", tables.stream()
                    .map(td -> {
                        String where = tq != null ? tq
                                : PostgresQueryConverter.convert(qs.query, qs.context, td);
                        where = updateQuery(op, td, where, queryStartTimeMicros, qs, false);
                        if (where == null) {
                            return null;
                        }
                        return String.format("(SELECT COUNT(*) FROM %s WHERE %s)",
                                td.getTableName(), where);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            if (countClauses.isEmpty()) {
                return response;
            }
            sql = String.format("SELECT %s", countClauses);
        } else {
            TableDescription td = tables.iterator().next();
            String where = tq != null ? tq
                    : PostgresQueryConverter.convert(qs.query, qs.context, td);
            where = updateQuery(op, td, where, queryStartTimeMicros, qs, false);
            if (where == null) {
                return response;
            }
            sql = String.format("SELECT COUNT(*) FROM %s WHERE %s",
                    td.getTableName(), where);
        }

        if (isDetailedLoggingEnabled) {
            logger.fine(() -> String.format("Xenon query specification: %s", Utils.toJsonHtml(qs)));
            logger.info(String.format(
                    "SQL query: \n>>>>>>>>>> SQL BEGIN >>>>>>>>>>\n%s\n<<<<<<<<<< SQL END <<<<<<<<<<\n",
                    prettySqlStatement(sql)));
        }

        try (Connection conn = this.ds.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            response.documentCount = rs.getLong(1);
        } catch (SQLException e) {
            logger.severe(() -> String.format("Failed SQL count: %s : %s",
                    prettySqlStatement(sql), Utils.toString(e)));
            throw e;
        }

        response.queryTimeMicros = Utils.getNowMicrosUtc() - queryStartTimeMicros;

        logQuery(op, response, qs, sql, response.queryTimeMicros);

        if (isDetailedLoggingEnabled) {
            logger.info(() -> String.format("SQL count: %s : %s", response.documentCount,
                    prettySqlStatement(sql)));
        }

        return response;
    }

    @SuppressWarnings("unchecked")
    private ServiceDocumentQueryResult queryIndexPaginated(Operation op,
            Set<TableDescription> tables,
            String tq, PostgresQueryPage page, EnumSet<QueryOption> options,
            QuerySpecification qs, int count, long expirationTimeMicros, String nodeSelectorLink,
            String indexLink, long queryStartTimeMicros) throws Exception {
        if (options == null) {
            options = EnumSet.noneOf(QueryOption.class);
        }

        String after = null;
        boolean useDirectSearch = options.contains(QueryOption.TOP_RESULTS);
        boolean hasExplicitLimit = count != Integer.MAX_VALUE;
        boolean isPaginatedQuery = hasExplicitLimit && !useDirectSearch;
        boolean hasPage = page != null;
        boolean shouldProcessResults = true;
        int hitCount;
        int resultLimit = count;

        ServiceDocumentQueryResult rsp = new ServiceDocumentQueryResult();
        if (options.contains(QueryOption.EXPAND_CONTENT)
                || options.contains(QueryOption.EXPAND_BINARY_CONTENT)
                || options.contains(QueryOption.EXPAND_SELECTED_FIELDS)) {
            rsp.documents = new HashMap<>();
        }

        if (isPaginatedQuery) {
            if (hasPage) {
                hitCount = resultLimit + 1;
            } else {
                // QueryTask.resultLimit was set, but we don't have a page param yet, which means
                // this
                // is the initial POST to create the queryTask. Since the initial query results will
                // be
                // discarded in this case, just set the limit to 1 and do not process results.
                resultLimit = 1;
                hitCount = 1;
                shouldProcessResults = false;
            }
        } else if (!hasExplicitLimit) {
            // The query does not have an explicit result limit set. We still specify an implicit
            // limit in order to avoid out of memory conditions, since Lucene will use the limit in
            // order to allocate a results array; however, if the number of hits returned by Lucene
            // is higher than the default limit, we will fail the query later.
            hitCount = PostgresDocumentIndexService.queryResultLimit;
        } else {
            // The query has an explicit result limit set, but the value is specified in terms of
            // the number of desired results in the QueryTask.
            // Assume twice as much data fill be fetched to account for the discrepancy.
            // The do/while loop below will correct this estimate at every iteration
            hitCount = resultLimit;
        }

        if (hasPage) {
            // For example, via GET of QueryTask.nextPageLink
            after = page.after;
            if (!qs.options.contains(QueryOption.FORWARD_ONLY)) {
                rsp.prevPageLink = page.previousPageLink;
            }
        }

        List<String> sortFields = null;
        if (qs != null && qs.sortTerm != null) {
            // see if query is part of a task and already has a cached sort
            if (qs.context != null) {
                sortFields = (List<String>) qs.context.nativeSort;
            }
            if (sortFields == null) {
                sortFields = PostgresQueryConverter.convertToPostgresSort(qs, false,
                        tables.iterator().next(), true);
            }
        }

        if (sortFields == null && (hasExplicitLimit || (qs != null && qs.options
                .contains(QueryOption.OWNER_SELECTION)))) {
            sortFields = Collections.singletonList("documentselflink ASC");
        }

        boolean hasOffset = qs != null && qs.offset != null;
        int offset = !hasOffset ? 0 : qs.offset;

        String sql;
        String orderBy;
        String fields;

        if (sortFields != null) {
            orderBy = "ORDER BY " + sortFields.stream()
                    .collect(Collectors.joining(","));
            fields = "data," + sortFields.stream()
                    .map(s -> s.substring(0, s.lastIndexOf(' ')))
                    .collect(Collectors.joining(","));
        } else {
            fields = "data";
            orderBy = "";
        }

        if (tables.size() == 1) {
            TableDescription td = tables.iterator().next();
            String where = tq != null ? tq
                    : PostgresQueryConverter.convert(qs.query, qs.context, td);
            where = updateQuery(op, td, where, queryStartTimeMicros, qs, false);
            if (where == null) {
                return rsp;
            }

            if (after != null) {
                where += " AND " + after;
            }

            if (!shouldProcessResults) {
                sql = String.format("SELECT EXISTS (SELECT 1 FROM %s WHERE %s)",
                        td.getTableName(), where);
            } else {
                sql = String.format("SELECT %s FROM %s WHERE %s %s LIMIT %s",
                        fields, td.getTableName(), where, orderBy, hitCount);
                if (offset > 0) {
                    sql += String.format(" OFFSET %d", offset);
                }
            }
        } else {
            Collection<String> tableSelects = tables.stream()
                    .map(td -> {
                        String where = tq != null ? tq
                                : PostgresQueryConverter.convert(qs.query, qs.context, td);
                        where = updateQuery(op, td, where, queryStartTimeMicros, qs, false);
                        if (where == null) {
                            return null;
                        }

                        return String.format("SELECT %s FROM %s WHERE %s",
                                fields, td.getTableName(), where);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (tableSelects.isEmpty()) {
                return rsp;
            }
            String unionQuery = String.join(" UNION ALL ", tableSelects);
            if (!shouldProcessResults) {
                sql = String.format("SELECT EXISTS (%s)", unionQuery);
            } else {
                String where = after != null ? " WHERE " + after : "";
                sql = String.format("SELECT %s FROM (%s) AS docs %s %s LIMIT %s",
                        fields, unionQuery, where, orderBy, hitCount);
                if (offset > 0) {
                    sql += String.format(" OFFSET %d", offset);
                }
            }
        }

        if (isDetailedLoggingEnabled) {
            logger.fine(() -> String.format("Xenon query specification: %s", Utils.toJsonHtml(qs)));
            logger.info(String.format(
                    "SQL query: \n>>>>>>>>>> SQL BEGIN >>>>>>>>>>\n%s\n<<<<<<<<<< SQL END <<<<<<<<<<\n",
                    prettySqlStatement(sql)));
        }

        long queryTime;
        try (Connection conn = this.ds.getConnection()) {
            if (shouldProcessResults) {
                conn.setAutoCommit(false);
            }
            try (Statement st = conn.createStatement()) {
                if (shouldProcessResults) {
                    // Turn use of the cursor on.
                    st.setFetchSize(FETCH_SIZE);
                }
                long startMillis = System.currentTimeMillis();
                try (ResultSet rs = st.executeQuery(sql)) {
                    queryTime = System.currentTimeMillis() - startMillis;
                    if (isDetailedLoggingEnabled) {
                        logger.info(String.format("SQL query execution time: %d ms", queryTime));
                    }

                    long totalHits = -1;
                    /*
                     * if (queryCount == 0 && useCountColumn) { totalHits = rs.getLong("_count"); if
                     * (!hasExplicitLimit && !hasPage && !isPaginatedQuery && totalHits > hitCount)
                     * { throw new IllegalStateException(
                     * "Query returned large number of results, please specify a resultLimit. Results:"
                     * + totalHits + ", QuerySpec: " + Utils.toJson(qs)); } }
                     */

                    JsonObject bottom = null;
                    if (shouldProcessResults) {
                        bottom = processQueryResults(qs, options, count, rsp, rs,
                                queryStartTimeMicros, nodeSelectorLink, true);
                        if (hasOffset) {
                            offset += count;
                        }
                    }

                    boolean checkNextPage = true;
                    if (!hasExplicitLimit || useDirectSearch ||
                            (totalHits != -1 && totalHits == 0)) {
                        // single pass
                        checkNextPage = false;
                    } else if (totalHits != -1 && totalHits == 0) {
                        // not hits
                        checkNextPage = false;
                    }

                    if (isPaginatedQuery && checkNextPage) {
                        boolean createNextPageLink;
                        if (hasPage) {
                            // Checks next page exists or not
                            createNextPageLink = rs.next();
                        } else {
                            // get exists column
                            rs.next();
                            createNextPageLink = rs.getBoolean(1);
                        }

                        if (createNextPageLink) {
                            if (bottom != null) {
                                after = PostgresQueryConverter
                                        .buildPaginationClause(tables.iterator().next(), sortFields,
                                                bottom);
                            }
                            rsp.nextPageLink = createNextPage(op, qs,
                                    null, sortFields, !hasOffset ? after : null,
                                    hasOffset ? offset : null, null,
                                    expirationTimeMicros, indexLink, nodeSelectorLink, hasPage);
                        }
                    }
                }
            }
            if (shouldProcessResults) {
                try {
                    conn.rollback();
                    conn.setAutoCommit(true);
                } catch (Exception ignore) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            logger.severe(String.format("Error while querying: %s\nException: %s",
                    sql, Utils.toString(e)));
            throw e;
        }

        rsp.queryTimeMicros = Utils.getNowMicrosUtc() - queryStartTimeMicros;
        rsp.documentCount = (long) rsp.documentLinks.size();

        logQuery(op, rsp, qs, sql, queryTime);

        return rsp;
    }

    /**
     * Starts a {@code QueryPageService} to track a partial search result set, associated with a
     * index searcher and search pointers. The page can be used for both grouped queries or document
     * queries
     */
    private String createNextPage(Operation op, QuerySpecification qs,
            String tq,
            List<String> sortFields,
            String after,
            Integer offset,
            Integer groupOffset,
            long expiration,
            String indexLink,
            String nodeSelectorLink,
            boolean hasPage) {

        String nextPageId = Utils.getNowMicrosUtc() + "";
        URI u = UriUtils.buildUri(this.host, UriUtils.buildUriPath(ServiceUriPaths.CORE_QUERY_PAGE,
                nextPageId));

        // the page link must point to this node, since the index searcher and results have been
        // computed locally. Transform the link to a query page forwarder link, which will
        // transparently forward requests to the current node.

        URI forwarderUri = UriUtils.buildForwardToQueryPageUri(u, this.host.getId());
        String nextLink = forwarderUri.getPath() + UriUtils.URI_QUERY_CHAR
                + forwarderUri.getQuery();

        // Compute previous page link. When FORWARD_ONLY option is specified, do not create previous
        // page link.
        String prevLinkForNewPage = null;
        boolean isForwardOnly = qs.options.contains(QueryOption.FORWARD_ONLY);
        if (!isForwardOnly) {
            URI forwarderUriOfPrevLinkForNewPage = UriUtils
                    .buildForwardToQueryPageUri(op.getReferer(),
                            this.host.getId());
            prevLinkForNewPage = forwarderUriOfPrevLinkForNewPage.getPath()
                    + UriUtils.URI_QUERY_CHAR + forwarderUriOfPrevLinkForNewPage.getQuery();
        }

        // Requests to core/query-page are forwarded to document-index (this service) and
        // referrer of that forwarded request is set to original query-page request.
        // This method is called when query-page wants to create new page for a paginated query.
        // If a new page is going to be created then it is safe to use query-page link
        // from referrer as previous page link of this new page being created.
        PostgresQueryPage page;
        if (after != null || groupOffset == null) {
            // page for documents
            page = new PostgresQueryPage(hasPage ? prevLinkForNewPage : null, after);
        } else {
            // page for group results
            page = new PostgresQueryPage(hasPage ? prevLinkForNewPage : null, groupOffset);
        }

        QuerySpecification spec = new QuerySpecification();
        qs.copyTo(spec);

        if (groupOffset == null) {
            spec.options.remove(QueryOption.GROUP_BY);
        }

        spec.offset = offset;
        spec.context.nativeQuery = tq;
        spec.context.nativePage = page;
        spec.context.nativeSearcher = qs.context.nativeSearcher;
        spec.context.nativeSort = sortFields;

        ServiceDocument body = new ServiceDocument();
        body.documentSelfLink = u.getPath();
        body.documentExpirationTimeMicros = expiration;

        AuthorizationContext ctx = op.getAuthorizationContext();
        if (ctx != null) {
            body.documentAuthPrincipalLink = ctx.getClaims().getSubject();
        }

        Operation startPost = Operation
                .createPost(u)
                .setBodyNoCloning(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logger.warning(() -> String.format("Unable to start next page "
                                + "service: %s", e));
                    }
                });

        if (ctx != null) {
            this.service.setAuthorizationContext(startPost, ctx);
        }

        this.host.startService(startPost,
                new PostgresQueryPageService(spec, indexLink, nodeSelectorLink));

        return nextLink;
    }

    private JsonObject processQueryResults(QuerySpecification qs, EnumSet<QueryOption> options,
            int resultLimit, ServiceDocumentQueryResult rsp, ResultSet rs,
            long queryStartTimeMicros,
            String nodeSelectorPath,
            boolean populateResponse) throws Exception {

        JsonObject lastDocVisited = null;
        final boolean hasCountOption = options.contains(QueryOption.COUNT);

        rsp.documentLinks.clear();

        PostgresDocumentStoredFieldVisitor visitor = new PostgresDocumentStoredFieldVisitor();
        int resultCount = 0;
        while (true) {
            if (!hasCountOption && rsp.documentLinks.size() >= resultLimit) {
                break;
            }

            if (++resultCount > resultLimit) {
                break;
            }

            if (!rs.next()) {
                break;
            }

            visitor.reset();
            loadDoc(visitor, rs);
            final String link = visitor.documentSelfLink;
            final String json = visitor.jsonSerializedState;

            lastDocVisited = visitor.getAsJsonObject();

            if (hasCountOption || !populateResponse) {
                // count unique instances of this link
                rsp.documentLinks.add(link);
                continue;
            }

            ServiceDocument state = null;

            if (options.contains(QueryOption.EXPAND_CONTENT)
                    || options.contains(QueryOption.OWNER_SELECTION)
                    || options.contains(QueryOption.EXPAND_SELECTED_FIELDS)) {
                state = getStateFromPostgresDocument(null, visitor, link);
            }

            if (options.contains(QueryOption.OWNER_SELECTION)) {
                if (!processQueryResultsForOwnerSelection(json, state, nodeSelectorPath)) {
                    continue;
                }
            }

            rsp.documentLinks.add(link);

            if (options.contains(QueryOption.EXPAND_CONTENT)) {
                Object o;
                if (options.contains(QueryOption.EXPAND_BUILTIN_CONTENT_ONLY)) {
                    if (state == null) {
                        o = visitor.getServiceDocumentBuiltInContentOnly();
                    } else {
                        ServiceDocument stateClone = new ServiceDocument();
                        state.copyTo(stateClone);
                        o = stateClone;
                    }
                } else if (state == null) {
                    o = visitor.getAsJsonObject();
                } else {
                    // More efficient to return same JSON object given to visitor, but there can be
                    // rare cases that db will have more fields than ServiceDocument state
                    // TODO: Consider using raw json from db
                    o = toJsonObject(state);
                }
                rsp.documents.put(link, o);
            } else if (options.contains(QueryOption.EXPAND_SELECTED_FIELDS)) {
                // filter out only the selected fields
                Set<String> selectFields = new TreeSet<>();
                if (qs != null) {
                    qs.selectTerms.forEach(qt -> selectFields.add(qt.propertyName));
                }

                // Create a new json object with selected fields
                JsonObject fromJsonObj = visitor.getAsJsonObject();
                JsonObject jo = new JsonObject();
                for (String field : selectFields) {
                    JsonElement je = fromJsonObj.get(field);
                    if (je != null && !je.isJsonNull()) {
                        jo.add(field, je);
                    }
                }
                rsp.documents.put(link, jo);
            }

            if (options.contains(QueryOption.SELECT_LINKS)) {
                processQueryResultsForSelectLinks(qs, rsp, visitor, rs, link, state);
            }
        }

        rsp.documentCount = (long) rsp.documentLinks.size();

        if (isDetailedLoggingEnabled) {
            logger.info(() -> String.format("Processed %s documents", rsp.documentCount));
        }
        return lastDocVisited;
    }

    private JsonObject toJsonObject(ServiceDocument state) {
        return (JsonObject) GsonSerializers.getJsonMapperFor(state.getClass()).toJsonElement(state);
    }

    private ServiceDocument processQueryResultsForSelectLinks(
            QuerySpecification qs, ServiceDocumentQueryResult rsp,
            PostgresDocumentStoredFieldVisitor d, ResultSet rs,
            String link, ServiceDocument state) throws Exception {
        if (rsp.selectedLinksPerDocument == null) {
            rsp.selectedLinksPerDocument = new HashMap<>();
            rsp.selectedLinks = new HashSet<>();
        }
        Map<String, String> linksPerDocument = rsp.selectedLinksPerDocument
                .computeIfAbsent(link, k -> new HashMap<>());

        for (QueryTask.QueryTerm qt : qs.linkTerms) {
            String linkValue = d.getLink(qt.propertyName);
            if (linkValue != null) {
                linksPerDocument.put(qt.propertyName, linkValue);
                rsp.selectedLinks.add(linkValue);
                continue;
            }

            // if there is no stored field with the link term property name, it might be
            // a field with a collection of links. We do not store those in lucene, they are
            // part of the binary serialized state.
            if (state == null) {
                PostgresDocumentStoredFieldVisitor visitor = new PostgresDocumentStoredFieldVisitor();
                loadDoc(visitor, rs);
                state = getStateFromPostgresDocument(null, visitor, link);
                if (state == null) {
                    logger.warning(() -> String.format("Skipping link term %s for %s, can "
                            + "not find serialized state", qt.propertyName, link));
                    continue;
                }
            }

            Field linkCollectionField = ReflectionUtils
                    .getField(state.getClass(), qt.propertyName);
            if (linkCollectionField == null) {
                continue;
            }
            Object fieldValue = linkCollectionField.get(state);
            if (fieldValue == null) {
                continue;
            }
            if (!(fieldValue instanceof Collection<?>)) {
                logger.warning(() -> String.format("Skipping link term %s for %s, field "
                        + "is not a collection", qt.propertyName, link));
                continue;
            }
            @SuppressWarnings("unchecked")
            Collection<String> linkCollection = (Collection<String>) fieldValue;
            int index = 0;
            for (String item : linkCollection) {
                if (item != null) {
                    linksPerDocument.put(
                            QuerySpecification
                                    .buildLinkCollectionItemName(qt.propertyName, index++),
                            item);
                    rsp.selectedLinks.add(item);
                }
            }
        }
        return state;
    }

    private boolean processQueryResultsForOwnerSelection(String json, ServiceDocument state,
            String nodeSelectorPath) {
        String documentSelfLink;
        if (state == null) {
            documentSelfLink = Utils.fromJson(json, ServiceDocument.class).documentSelfLink;
        } else {
            documentSelfLink = state.documentSelfLink;
        }
        // when node-selector is not specified via query, use the one for index-service which may be
        // null
        if (nodeSelectorPath == null) {
            nodeSelectorPath = this.service.getPeerNodeSelectorPath();
        }
        SelectOwnerResponse ownerResponse = this.host.findOwnerNode(nodeSelectorPath,
                documentSelfLink);

        // omit the result if the documentOwner is not the same as the local owner
        return ownerResponse != null && ownerResponse.isLocalHostOwner;
    }

    private Set<String> getKindScope(QuerySpecification qs) {
        Set<String> kindScope = new HashSet<>(4);

        findKindScopeFromQuery(qs.query, kindScope);
        if (kindScope.isEmpty()) {
            findFactoryScopeFromQuery(qs.query, kindScope);
        }

        if (kindScope.isEmpty()) {
            logger.fine(() -> String.format("Missing kind scope in query: %s",
                    Utils.toJsonHtml(qs)));

            // Returning all scopes to allow do a UNION of all tables
            // Adding to existing kindScope mutable set to avoid Kryo cloning issues
            kindScope.addAll(this.schemaManager.getServiceDocuments());
        }

        if (kindScope.isEmpty()) {
            throw new IllegalArgumentException("Cannot create SQL query without a kind scope");
        }

        return kindScope;
    }

    /**
     * Traverses the given query and find documentSelfLink constraints from which the factory query
     * scope is deducted.
     *
     * Takes all links into account no matter if they MUST_NOT_OCCUR - no need for such optimization
     * for now as our support for multiple kind scope will cope with that.
     */
    private void findFactoryScopeFromQuery(Query query, Set<String> result) {
        if (query.booleanClauses != null) {
            for (Query innerQuery : query.booleanClauses) {
                findFactoryScopeFromQuery(innerQuery, result);
            }
            return;
        }

        if (query.term != null &&
                ServiceDocument.FIELD_NAME_SELF_LINK.equals(query.term.propertyName) &&
                !UriUtils.URI_WILDCARD_CHAR.equals(query.term.matchValue)) {
            String factoryLink = UriUtils.getParentPath(query.term.matchValue);
            if (factoryLink != null && !factoryLink.isEmpty() &&
                    !UriUtils.URI_PATH_CHAR.equals(factoryLink) &&
                    !factoryLink.contains(UriUtils.URI_WILDCARD_CHAR)) {
                String documentKind = this.schemaManager.getDocumentKindForFactoryLink(factoryLink);
                if (documentKind != null) {
                    result.add(documentKind);
                }
            }
        }
    }

    /**
     * Traverses the given query and find documentSelfLink constraints from which the factory query
     * scope is deducted.
     *
     * Takes all links into account no matter if they MUST_NOT_OCCUR - no need for such optimization
     * for now as our support for multiple kind scope will cope with that.
     */
    private void findKindScopeFromQuery(Query query, Set<String> result) {
        if (query.booleanClauses != null) {
            for (Query innerQuery : query.booleanClauses) {
                findKindScopeFromQuery(innerQuery, result);
            }
            return;
        }

        if (query.term != null &&
                ServiceDocument.FIELD_NAME_KIND.equals(query.term.propertyName) &&
                query.occurance != QueryTask.Query.Occurance.MUST_NOT_OCCUR) {
            result.add(query.term.matchValue);
        }
    }

    private Set<TableDescription> kindScopeToTableDescriptions(Set<String> kindScope) {
        return kindScope.stream()
                .map(documentKind -> {
                    TableDescription td = this.schemaManager
                            .getTableDescriptionForDocumentKind(documentKind);
                    if (td == null) {
                        logger.warning("Cannot determine SQL table name for document kind: "
                                + documentKind);
                    }
                    return td;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    static boolean isTableColumn(String name) {
        switch (name) {
        case ServiceDocument.FIELD_NAME_KIND:
        case ServiceDocument.FIELD_NAME_EXPIRATION_TIME_MICROS:
        case ServiceDocument.FIELD_NAME_VERSION:
        case ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS:
        case ServiceDocument.FIELD_NAME_SELF_LINK:
        case ServiceDocument.FIELD_NAME_AUTH_PRINCIPAL_LINK:
        case ServiceDocument.FIELD_NAME_TRANSACTION_ID:
        case ServiceDocument.FIELD_NAME_UPDATE_ACTION:
            return true;
        case ServiceDocument.FIELD_NAME_OWNER:
        default:
            return false;
        }
    }

    static String prettySqlStatement(String stmt) {
        stmt = stmt.replace(" WHERE ", "\nWHERE ")
                .replace(" FROM (", "\nFROM (\n")
                .replace(" FROM ", "\nFROM ")
                .replace(") AS docs ", "\n) AS docs ")
                .replace(" ORDER BY ", "\nORDER BY ")
                .replace(" GROUP BY ", "\nGROUP BY ")
                .replace(" UNION ALL ", "\nUNION ALL\n")
                .replace(" AND ", "\nAND ")
                .replace(" OR ", "\nOR ")
                .replace(" + ", "\n+ ");
        return "\n" + stmt + "\n";
    }

    private void appendDebugInfo(Operation op, String... titleContent) {
        if (!isDebugQuery()) {
            return;
        }
        try {
            Path path = Paths
                    .get(DUMP_QUERY_DIRECTORY, this.startTimeMillis + '-' + op.getId() + ".txt");
            try (OutputStream out = Files
                    .newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (int i = 0; i < titleContent.length; i += 2) {
                    String title = titleContent[i];
                    String content = titleContent[i + 1];

                    out.write(("*** " + title + "\n\n").getBytes(Utils.CHARSET));

                    if (content != null && !content.isEmpty()) {
                        out.write(content.getBytes(Utils.CHARSET));
                        out.write(("\n\n").getBytes(Utils.CHARSET));
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to dump query: " + e);
        }
    }

    private boolean isDebugQuery() {
        return DUMP_QUERY_DIRECTORY != null && !DUMP_QUERY_DIRECTORY.isEmpty();
    }

    private void registerMBeans() {
        if (!PostgresHostUtils.REGISTER_MBEANS) {
            return;
        }

        // Register management beans
        try {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName beanConfigName = new ObjectName("com.vmware.xenon.services.rdbms:type=DAO");
            if (!mBeanServer.isRegistered(beanConfigName)) {
                mBeanServer.registerMBean(this, beanConfigName);
            }
        } catch (Exception e) {
            logger.warning("Failed to register management beans: " + e);
        }
    }

    public boolean isDetailedLoggingEnabled() {
        return this.isDetailedLoggingEnabled;
    }

    public void setDetailedLoggingEnabled(boolean enabled) {
        this.isDetailedLoggingEnabled = enabled;
    }

    public long getLogSlowQueryThresholdSeconds() {
        return TimeUnit.MICROSECONDS.toSeconds(this.logSlowQueryThresholdMicros);
    }

    public void setLogSlowQueryThresholdSeconds(long seconds) {
        this.logSlowQueryThresholdMicros = TimeUnit.SECONDS.toMicros(seconds);
    }

}
