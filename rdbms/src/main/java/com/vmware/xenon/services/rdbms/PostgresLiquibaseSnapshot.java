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

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.rdbms.PostgresSchemaManager.ColumnDescription;
import com.vmware.xenon.services.rdbms.PostgresSchemaManager.TableDescription;

class PostgresLiquibaseSnapshot {

    private static String LIQUIBASE_STRUCTURE_CORE = "liquibase.structure.core.";
    private static String LIQUIBASE_STRUCTURE_CORE_CATALOG = LIQUIBASE_STRUCTURE_CORE + "Catalog";
    private static String LIQUIBASE_STRUCTURE_CORE_COLUMN = LIQUIBASE_STRUCTURE_CORE + "Column";
    private static String LIQUIBASE_STRUCTURE_CORE_TABLE = LIQUIBASE_STRUCTURE_CORE + "Table";
    private static String LIQUIBASE_STRUCTURE_CORE_PRIMARY_KEY =
            LIQUIBASE_STRUCTURE_CORE + "PrimaryKey";
    private static String LIQUIBASE_STRUCTURE_CORE_INDEX = LIQUIBASE_STRUCTURE_CORE + "Index";
    private static String LIQUIBASE_STRUCTURE_CORE_SCHEMA = LIQUIBASE_STRUCTURE_CORE + "Schema";

    private final JsonObject snapshotWrapper = new JsonObject();
    private final JsonArray columnArray = new JsonArray();
    private final JsonArray tableArray = new JsonArray();
    private final JsonArray primaryKeyArray = new JsonArray();
    private final JsonArray indexArray = new JsonArray();
    private final JsonArray includedType = new JsonArray();
    private final JsonObject objects = new JsonObject();
    private final String schemaId;
    private final Map<String, ColumnDescription> columnDescriptionPerIndexId = new HashMap<>();

    static String emptySnapshotAsJson() {
        return new PostgresLiquibaseSnapshot().getSnapshotAsJson();
    }

    PostgresLiquibaseSnapshot() {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("created", Instant.now().toString());
        snapshotWrapper.add("snapshot", snapshot);

        JsonObject database = new JsonObject();
        database.addProperty("majorVersion", 9);
        database.addProperty("minorVersion", 6);
        database.addProperty("productVersion", "9.6.6");
        database.addProperty("productName", "PostgreSQL");
        database.addProperty("shortName", "postgresql");
        database.addProperty("user", "xenon");
        snapshot.add("database", database);

        // objects
        snapshot.add("objects", objects);

        // catalog
        String catalogName = "xenon";
        JsonObject catalog = new JsonObject();
        catalog.addProperty("default", true);
        catalog.addProperty("name", catalogName);
        String catalogId = addSnapshotId(catalog, LIQUIBASE_STRUCTURE_CORE_CATALOG, catalogName);


        JsonObject catalogArrayWrapper = new JsonObject();
        catalogArrayWrapper.add("catalog", catalog);
        JsonArray catalogArray = new JsonArray();
        catalogArray.add(catalogArrayWrapper);
        addObject(LIQUIBASE_STRUCTURE_CORE_CATALOG, catalogArray);

        // schema
        String schemaName = "public";
        JsonObject schema = new JsonObject();
        schema.addProperty("catalog", catalogId);
        schema.addProperty("default", true);
        schema.addProperty("name", schemaName);
        this.schemaId = addSnapshotId(schema, LIQUIBASE_STRUCTURE_CORE_SCHEMA, schemaName);

        JsonObject schemaArrayWrapper = new JsonObject();
        schemaArrayWrapper.add("schema", schema);
        JsonArray schemaArray = new JsonArray();
        schemaArray.add(schemaArrayWrapper);
        addObject(LIQUIBASE_STRUCTURE_CORE_SCHEMA, schemaArray);

        // other objects
        addObject(LIQUIBASE_STRUCTURE_CORE_COLUMN, columnArray);
        addObject(LIQUIBASE_STRUCTURE_CORE_TABLE, tableArray);
        addObject(LIQUIBASE_STRUCTURE_CORE_PRIMARY_KEY, primaryKeyArray);
        addObject(LIQUIBASE_STRUCTURE_CORE_INDEX, indexArray);

        // snapshotControl
        JsonObject snapshotControl = new JsonObject();
        snapshotControl.add("includedType", includedType);
        JsonObject snapshotControlWrapper = new JsonObject();
        snapshotControlWrapper.add("snapshotControl", snapshotControl);
        snapshot.add("snapshotControl", snapshotControlWrapper);
    }

    private String addSnapshotId(JsonObject obj, String type, String... values) {
        StringJoiner s = new StringJoiner(",");
        s.add(type);
        Stream.of(values).forEach(s::add);
        String snapshotId = Utils.computeHash(s.toString());
        obj.addProperty("snapshotId", snapshotId);

        // return id as type#snapshotId
        return type + "#" + snapshotId;
    }

    private void addObject(String name, JsonArray array) {
        this.objects.add(name, array);
        this.includedType.add(name);
    }

    private String addColumn(String tableId, String name, boolean nullable, String typeName,
            boolean computed) {
        JsonObject type = new JsonObject();
        type.addProperty("typeName", typeName);

        String sizeUnit;
        switch (typeName) {
        case "text":
            sizeUnit = "CHAR";
            type.addProperty("characterOctetLength", "2147483647!{java.lang.Integer}");
            break;
        default:
            sizeUnit = "BYTE";
        }

        type.addProperty("columnSizeUnit",
                sizeUnit + "!{liquibase.structure.core.DataType$ColumnSizeUnit}");

        type.addProperty("radix", "10!{java.lang.Integer}");

        JsonObject column = new JsonObject();
        column.addProperty("name", name);
        column.addProperty("relation", tableId);
        column.addProperty("nullable", nullable);
        column.addProperty("computed", computed);
        //column.addProperty("order", order + "!{java.lang.Integer}");
        column.add("type", type);
        String columnId = addSnapshotId(column, LIQUIBASE_STRUCTURE_CORE_COLUMN, tableId, name);

        JsonObject columnArrayWrapper = new JsonObject();
        columnArrayWrapper.add("column", column);
        columnArray.add(columnArrayWrapper);

        return columnId;
    }

    private String addPrimaryKey(String tableId, String name, String primaryKeyIndexId,
            List<String> columnIds) {
        JsonArray columns = new JsonArray();
        columnIds.forEach(columns::add);

        JsonObject primaryKey = new JsonObject();
        // TODO: Avoid setting name, causing issues
        //primaryKey.addProperty("name", name);
        primaryKey.addProperty("table", tableId);
        primaryKey.addProperty("backingIndex", primaryKeyIndexId);
        primaryKey.add("columns", columns);
        String primaryKeyId = addSnapshotId(primaryKey, LIQUIBASE_STRUCTURE_CORE_PRIMARY_KEY, tableId);

        JsonObject primaryKeyArrayWrapper = new JsonObject();
        primaryKeyArrayWrapper.add("primaryKey", primaryKey);
        primaryKeyArray.add(primaryKeyArrayWrapper);

        return primaryKeyId;
    }

    private String addIndex(String tableId, String name, boolean unique, List<String> columnIds) {
        JsonArray columns = new JsonArray();
        columnIds.forEach(columns::add);

        JsonObject index = new JsonObject();
        index.addProperty("name", name);
        index.addProperty("table", tableId);
        index.addProperty("unique", unique);
        index.add("columns", columns);
        String indexId = addSnapshotId(index, LIQUIBASE_STRUCTURE_CORE_INDEX, tableId, name);

        JsonObject indexArrayWrapper = new JsonObject();
        indexArrayWrapper.add("index", index);
        indexArray.add(indexArrayWrapper);

        return indexId;
    }

    private void addColumnIndex(TableDescription td, String tableId,
            ColumnDescription columnDescription,
            Map<String, String> columnIds, Map<String, String> indexIds,
            String documentSelfLinkColumnId) {
        String typeName = columnDescription.getColumnType();
        String columnName = columnDescription.getColumnName();
        boolean isNativeColumn = columnDescription.isNativeColumn();

        boolean nullable = true;
        if (isNativeColumn) {
            switch (columnDescription.getPropertyName()) {
            case ServiceDocument.FIELD_NAME_SELF_LINK:
            case ServiceDocument.FIELD_NAME_VERSION:
            case ServiceDocument.FIELD_NAME_KIND:
            case ServiceDocument.FIELD_NAME_UPDATE_ACTION:
            case ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS:
            case ServiceDocument.FIELD_NAME_OWNER:
                nullable = false;
                break;
            default:
            }
        }

        String indexType = columnDescription.getIndexType();
        boolean isIndex = indexType != null;
        if (!isNativeColumn && !isIndex) {
            return;
        }

        String indexColumnName = columnName;
        String indexNameSuffix = columnDescription.getPropertyName();
        PropertyDescription propertyDescription = columnDescription.getPropertyDescription();
        if (!columnDescription.isNativeColumn() && columnDescription.isTextType()
                && propertyDescription.indexingOptions.contains(PropertyIndexingOption.CASE_INSENSITIVE)) {
            // Make sure we are using a text value
            indexColumnName = String.format("lower(%s)", columnDescription.getColumnNameAsText());
            indexNameSuffix = "lower_" + indexNameSuffix;
        }

        if (!indexColumnName.equalsIgnoreCase(columnDescription.getPropertyName())) {
            indexColumnName = "(" + indexColumnName + ")";
        }

        String columnId = addColumn(tableId, indexColumnName, nullable, typeName, !isNativeColumn);
        if (isNativeColumn) {
            columnIds.put(columnName, columnId);
        }

        // create index
        if (isIndex) {
            addIndex(td, tableId, columnDescription, indexNameSuffix, indexIds, Collections.singletonList(columnId));

            // Add SORT indexing if index type is btree or hash
            if (propertyDescription.indexingOptions.contains(PropertyIndexingOption.SORT) &&
                    (indexType.equalsIgnoreCase("hash") || indexType.equalsIgnoreCase("btree"))) {
                // Add additional indexes for sorting
                addIndex(td, tableId, columnDescription, "sort_" + indexNameSuffix, indexIds, Arrays.asList(columnId,
                        documentSelfLinkColumnId));
            }
        }
    }

    private String toHashedName(String name, int maxLen) {
        name = name.toLowerCase();
        if (name.length() <= maxLen) {
            return name;
        }

        String hash = Utils.computeHash(name);
        return name.substring(0, maxLen - 1 - hash.length()) + '_' + hash;
    }

    private void addIndex(TableDescription td, String tableId, ColumnDescription columnDescription,
            String indexNameSuffix, Map<String, String> indexIds, List<String> columnIds) {
        // Postgres names are maxed at 63 bytes, as long as we use ascii we should have no more than
        // 63 characters
        String indexName = toHashedName(
                td.getTableName() + "_idx_" + indexNameSuffix, 63);
        String indexId = addIndex(tableId, indexName, false, columnIds);
        indexIds.put(indexName, indexId);
        this.columnDescriptionPerIndexId.put(indexName, columnDescription);
    }

    public ColumnDescription getColumnDescriptionByIndexId(String indexId) {
        return this.columnDescriptionPerIndexId.get(indexId);
    }

    private String addTable(TableDescription td) {
        JsonObject table = new JsonObject();
        table.addProperty("name", td.getTableName());
        String tableId = addSnapshotId(table, LIQUIBASE_STRUCTURE_CORE_TABLE, td.getTableName());

        Map<String, String> columnIds = new HashMap<>();
        Map<String, String> indexIds = new HashMap<>();

        String dataColumnId = addColumn(tableId, "data", false, "jsonb", false);
        columnIds.put("data", dataColumnId);

        td.getColumns().stream()
                .filter(ColumnDescription::isNativeColumn)
                .forEach(column -> addColumnIndex(td, tableId, column, columnIds, indexIds, null));

        String documentSelfLinkColumnId = columnIds
                .get(ServiceDocument.FIELD_NAME_SELF_LINK.toLowerCase());

        td.getColumns().stream()
                .filter(cd -> !cd.isNativeColumn() && cd.getDataColumnLevel() == 0)
                .forEach(column -> addColumnIndex(td, tableId, column, columnIds, indexIds, documentSelfLinkColumnId));

        // primary key index
        List<String> primaryKeyColumnIds = Collections.singletonList(documentSelfLinkColumnId);
        String primaryKeyIndexName = td.getTableName() + "_pkey";

        //String primaryKeyIndexId = null;
        String primaryKeyIndexId = addIndex(tableId, primaryKeyIndexName, true,
                primaryKeyColumnIds);
        indexIds.put(primaryKeyIndexName, primaryKeyIndexId);

        String primaryKey = addPrimaryKey(tableId, primaryKeyIndexName, primaryKeyIndexId,
                primaryKeyColumnIds);

        table.addProperty("primaryKey", primaryKey);
        table.addProperty("schema", this.schemaId);

        // Columns & Indexes
        JsonArray columns = new JsonArray();
        columnIds.values().forEach(columns::add);
        table.add("columns", columns);

        JsonArray indexes = new JsonArray();
        indexIds.values().forEach(indexes::add);
        table.add("indexes", indexes);

        JsonObject tableArrayWrapper = new JsonObject();
        tableArrayWrapper.add("table", table);
        tableArray.add(tableArrayWrapper);

        return tableId;
    }

    public void addTableDescriptions(Collection<TableDescription> descriptions) {
        descriptions.forEach(this::addTableDescription);
    }

    public void addTableDescription(TableDescription td) {
        addTable(td);
    }

    public String getSnapshotAsJson() {
        try {
            StringWriter stringWriter = new StringWriter();
            JsonWriter jsonWriter = new JsonWriter(stringWriter);
            jsonWriter.setLenient(true);
            jsonWriter.setSerializeNulls(true);
            jsonWriter.setIndent("  ");
            Streams.write(this.snapshotWrapper, jsonWriter);
            return stringWriter.toString();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

}
