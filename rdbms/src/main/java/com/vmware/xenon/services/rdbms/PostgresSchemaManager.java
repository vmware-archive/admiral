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

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.rdbms.annotations.StateNameOverride;

public class PostgresSchemaManager {
    private static final Logger logger = Logger.getLogger(PostgresSchemaManager.class.getName());
    private static final String TASK_STATE_DOCUMENT_KIND = Utils.toDocumentKind(TaskState.class);
    private final Set<String> tableNames = ConcurrentHashMap.newKeySet();
    private final Map<String, TableDescription> tableDescPerFactoryLink = new ConcurrentHashMap<>();
    private final Map<String, TableDescription> tableDescPerDocumentKind = new
            ConcurrentHashMap<>();

    private final ServiceHost host;

    static class ColumnDescription {
        private TableDescription table;
        private ColumnDescription parent;
        private Set<ColumnDescription> children;
        private String propertyName;
        private String columnName;
        private String columnNameAsText;
        private String columnType;
        private PropertyDescription propertyDescription;
        private String indexType;
        private int dataColumnLevel;

        public TableDescription getTableDescription() {
            return this.table;
        }

        public ColumnDescription getParent() {
            return parent;
        }

        public boolean isTextType() {
            return "text".equals(this.columnType);
        }

        public boolean isJsonType() {
            return this.columnType != null && this.columnType.startsWith("json");
        }

        public String getIndexType() {
            return this.indexType;
        }

        public String getColumnName() {
            return this.columnName;
        }

        public String getColumnNameAsText() {
            return this.columnNameAsText;
        }

        public String getPropertyName() {
            return this.propertyName;
        }

        public String getColumnType() {
            return this.columnType;
        }

        public PropertyDescription getPropertyDescription() {
            return this.propertyDescription;
        }

        public void addChild(ColumnDescription cd) {
            if (this.children == null) {
                this.children = new HashSet<>();
            }
            this.children.add(cd);
        }

        private Collection<ColumnDescription> getAllChildren() {
            if (this.children == null || this.children.isEmpty()) {
                return Collections.emptySet();
            }
            Collection<ColumnDescription> children = new HashSet<>(this.children);
            this.children.forEach(c -> {
                children.addAll(c.getAllChildren());
            });
            return children;
        }

        public int getDataColumnLevel() {
            return this.dataColumnLevel;
        }

        boolean isNativeColumn() {
            return this.dataColumnLevel == -1;
        }
    }

    public static class TableDescription {
        private ServiceHost host;
        private String factoryLink;
        private String factoryLinkWithTrailingSlash;
        private String documentKind;
        private Class<? extends ServiceDocument> stateType;
        private ServiceDocumentDescription sdd;
        private String tableName;
        private Map<String, ColumnDescription> columnByPropertyName = new HashMap<>();

        private TableDescription(ServiceHost host) {
            this.host = host;
        }

        private void buildColumns() {
            columnByPropertyName.clear();
            this.sdd.propertyDescriptions.forEach((propertyName, propertyDescription) ->
                    handleProperty(null, null, propertyName, propertyDescription));

            PropertyDescription documentUpdateAction = new PropertyDescription();
            documentUpdateAction.typeName = ServiceDocumentDescription.TypeName.STRING;

            handleProperty(null, null, ServiceDocument.FIELD_NAME_UPDATE_ACTION,
                    documentUpdateAction);
        }

        private void handleProperty(ColumnDescription parent, String parentPath, String propertyName,
                PropertyDescription pd) {
            String absolutePropertyName = QuerySpecification.buildCompositeFieldName(parentPath,
                    propertyName);

            ColumnDescription cd = new ColumnDescription();
            cd.propertyName = absolutePropertyName;
            cd.table = this;
            cd.propertyDescription = pd;
            cd.columnType = getColumnType(pd);
            cd.parent = parent;
            if (parent != null) {
                parent.addChild(cd);
            }

            boolean isJsonType = cd.getParent() != null ? cd.getParent().isJsonType() : cd.isJsonType();
            boolean isTextType = cd.getParent() != null ? cd.getParent().isTextType() : cd.isTextType();

            if (PostgresServiceDocumentDao.isTableColumn(absolutePropertyName)) {
                cd.dataColumnLevel = -1;
                cd.columnName = cd.columnNameAsText = propertyName.toLowerCase();

                switch (propertyName) {
                case ServiceDocument.FIELD_NAME_EXPIRATION_TIME_MICROS:
                case ServiceDocument.FIELD_NAME_UPDATE_ACTION:
                case ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS:
                case ServiceDocument.FIELD_NAME_SELF_LINK:
                    cd.indexType = "btree";
                    break;
                default:
                }
            } else {
                String propertyPath = absolutePropertyName.replace(
                        QuerySpecification.FIELD_NAME_CHARACTER
                                + QuerySpecification.COLLECTION_FIELD_SUFFIX, "")
                        .replace(QuerySpecification.FIELD_NAME_CHARACTER, ",");

                cd.dataColumnLevel = 0;
                for (int i = 0; i < propertyPath.length(); i++) {
                    if (propertyPath.charAt(i) == ',') {
                        cd.dataColumnLevel++;
                    }
                }

                if (cd.dataColumnLevel == 0) {
                    cd.columnName = String.format("data -> '%s'", propertyPath);
                } else {
                    cd.columnName = String.format("data #> '{%s}'", propertyPath);
                }

                cd.columnNameAsText = cd.columnName.replace(">", ">>");

                if (isTextType) {
                    cd.columnName = cd.columnNameAsText;
                } else if (!isJsonType) {
                    cd.columnName = String.format("(%s)::%s", cd.columnNameAsText, cd.columnType);
                }

                EnumSet<PropertyIndexingOption> indexingOptions = cd.propertyDescription.indexingOptions;
                EnumSet<PropertyUsageOption> usageOptions = cd.propertyDescription.usageOptions;

                if (!ServiceDocument.isBuiltInDocumentField(propertyName)
                        && cd.parent == null
                        && !indexingOptions.contains(PropertyIndexingOption.STORE_ONLY)) {
                    switch (cd.propertyDescription.typeName) {
                    case InternetAddressV4:
                    case InternetAddressV6:
                    case URI:
                    case ENUM:
                    case DOUBLE:
                    case BOOLEAN:
                    case STRING:
                    case LONG:
                        cd.indexType = "btree";
                        break;
                    case PODO:
                    case COLLECTION:
                    case MAP:
                        if (indexingOptions.contains(PropertyIndexingOption.EXPAND)
                                || usageOptions.contains(PropertyUsageOption.LINKS)
                                || (cd.propertyDescription.typeName == ServiceDocumentDescription.TypeName.PODO
                                && TASK_STATE_DOCUMENT_KIND.equals(cd.propertyDescription.kind))) {
                            cd.indexType = "gin";
                        }
                        break;
                    case DATE:
                        // No indexing
                        // TDOO: Need to index as microseconds since UNIX epoch
                        break;
                    case BYTES:
                    default:
                        // No indexing
                    }
                }
            }

            this.columnByPropertyName.put(absolutePropertyName, cd);

            switch (pd.typeName) {
            case PODO:
                pd.fieldDescriptions
                        .forEach((key, value) -> handleProperty(cd, absolutePropertyName, key, value));
                break;
            case COLLECTION:
                handleProperty(cd, absolutePropertyName, QuerySpecification.COLLECTION_FIELD_SUFFIX,
                        pd.elementDescription);
                break;
            default:
            }
        }

        public TableDescription setIndexType(String propertyName, String indexType) {
            ColumnDescription cd = getColumnDescription(propertyName);
            if (cd == null) {
                throw new IllegalArgumentException("Property " + propertyName + " not found");
            }
            cd.indexType = indexType;
            return this;
        }

        public TableDescription setTableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public TableDescription setFactoryLink(String factoryLink) {
            this.factoryLink = factoryLink;
            this.factoryLinkWithTrailingSlash = factoryLink + '/';
            return this;
        }

        public TableDescription setStateType(Class<? extends ServiceDocument> stateType) {
            this.stateType = stateType;
            this.documentKind = Utils.buildKind(stateType);
            return this;
        }

        public TableDescription setServiceDocumentDescription(ServiceDocumentDescription sdd) {
            this.sdd = sdd;
            buildColumns();
            return this;
        }

        public TableDescription useStatefulService(StatefulService service) {
            if (service.getHost() == null) {
                service.setHost(this.host);
            }

            if (getFactoryLink() == null) {
                Class<? extends StatefulService> serviceType = service.getClass();
                String factoryLink = getFactoryLinkFieldValue(serviceType);
                if (factoryLink == null) {
                    throw new IllegalArgumentException(
                            "Failed to add factory, missing FACTORY_LINK field");
                }
                setFactoryLink(factoryLink);
            }

            setStateType(service.getStateType());
            setServiceDocumentDescription(service.getDocumentTemplate().documentDescription);
            return this;
        }

        public TableDescription useStatefulService(Class<? extends StatefulService> serviceType) {
            try {
                StatefulService service = serviceType.newInstance();
                useStatefulService(service);
                return this;
            } catch (InstantiationException | IllegalAccessException e) {
                throw new IllegalArgumentException("Failed to create a new instance: " + e);
            }
        }

        public TableDescription useFactoryService(FactoryService factoryService) {
            if (factoryService.getHost() == null) {
                factoryService.setHost(this.host);
            }
            try {
                if (getFactoryLink() == null) {
                    setFactoryLink(factoryService.getSelfLink());
                }

                StatefulService childService = (StatefulService) factoryService.createServiceInstance();
                useStatefulService(childService);
                return this;
            } catch (Throwable e) {
                throw new IllegalArgumentException(String.format(
                        "Failed to add factory %s", factoryService.getClass().getCanonicalName()), e);
            }
        }

        public TableDescription useFactoryService(Class<? extends FactoryService> factoryType) {
            if (getFactoryLink() == null) {
                String factoryLink = getSelfLinkFieldValue(factoryType);
                if (factoryLink == null) {
                    throw new IllegalArgumentException(String.format(
                            "Failed to add factory %s, missing SELF_LINK field",
                            factoryType.getCanonicalName()));
                }
                setFactoryLink(factoryLink);
            }

            try {
                FactoryService factoryService = factoryType.newInstance();
                useFactoryService(factoryService);
                return this;
            } catch (Throwable e) {
                throw new IllegalArgumentException(String.format(
                        "Failed to add factory %s", factoryType.getCanonicalName()), e);
            }
        }

        public String getFactoryLink() {
            return this.factoryLink;
        }

        public String getDocumentKind() {
            return this.documentKind;
        }

        public Class<? extends ServiceDocument> getStateType() {
            return this.stateType;
        }

        public ServiceDocumentDescription getServiceDocumentDescription() {
            return this.sdd;
        }

        public String getTableName() {
            return this.tableName;
        }

        Collection<ColumnDescription> getColumns() {
            return this.columnByPropertyName.values();
        }

        Collection<String> getColumnNames() {
            return this.columnByPropertyName.keySet();
        }

        ColumnDescription getColumnDescription(String propertyName) {
            return this.columnByPropertyName.get(propertyName);
        }

        String getFactoryLinkWithTrailingSlash() {
            return this.factoryLinkWithTrailingSlash;
        }

        @Override
        public String toString() {
            return "TableDescription{" +
                    "factoryLink='" + this.factoryLink + '\'' +
                    ", documentKind='" + this.documentKind + '\'' +
                    ", tableName='" + this.tableName + '\'' +
                    '}';
        }
    }

    public PostgresSchemaManager(ServiceHost host) {
        this.host = host;
    }

    public void addFactory(String factoryLink, Class<? extends StatefulService> serviceType) {
        addTable(td -> {
            td.setFactoryLink(factoryLink);
            td.useStatefulService(serviceType);
        });
    }

    public void addFactory(String factoryLink, StatefulService childService) {
        addTable(td -> {
            td.setFactoryLink(factoryLink);
            td.useStatefulService(childService);
        });
    }

    public void addCustomFactory(Class<? extends FactoryService> factoryType) {
        addTable(td -> {
            td.useFactoryService(factoryType);
        });
    }

    public void addFactory(StatefulService service) {
        addTable(td -> {
            td.useStatefulService(service);
        });
    }

    public void addFactory(Class<? extends StatefulService> serviceType) {
        addTable(td -> {
            td.useStatefulService(serviceType);
        });
    }

    void addFactory(String factoryLink,
            Class<? extends ServiceDocument> stateType,
            ServiceDocumentDescription sdd) {
        addTable(td -> {
            td.setFactoryLink(factoryLink);
            td.setStateType(stateType);
            td.setServiceDocumentDescription(sdd);
        });
    }

    public void addTable(Consumer<TableDescription> handler) {
        TableDescription td = new TableDescription(this.host);
        handler.accept(td);
        addTable(td);
    }

    private void addTable(TableDescription td) {
        Objects.requireNonNull(td.host, "Missing host");
        Objects.requireNonNull(td.factoryLink, "Missing factoryLink");
        Objects.requireNonNull(td.documentKind, "Missing documentKind");
        Objects.requireNonNull(td.stateType, "Missing stateType");
        Objects.requireNonNull(td.sdd, "Missing sdd (ServiceDocumentDescription)");

        if (td.tableName == null || td.tableName.isEmpty()) {
            td.tableName = toTableName(td.stateType, td.factoryLink);
            Objects.requireNonNull(td.tableName, "Missing tableName");
        }

        if (this.tableNames.contains(td.tableName)) {
            logger.severe(String.format("Factory already registered for table %s", td.tableName));
        }

        // Skip registering document kinds that is ServiceDocument, some bootstrap services use
        // it as state type
        if (!ServiceDocument.class.equals(td.stateType)) {
            if (this.tableDescPerDocumentKind.containsKey(td.documentKind)) {
                logger.warning(String.format("Factory already registered for document kind %s: %s",
                        td.documentKind,
                        tableDescPerDocumentKind.get(td.documentKind).factoryLink));
            }
            this.tableDescPerDocumentKind.put(td.documentKind, td);
        }
        this.tableNames.add(td.tableName);
        this.tableDescPerFactoryLink.put(td.factoryLink, td);
        Utils.registerKind(td.stateType, td.documentKind);
    }

    Collection<TableDescription> getTableDescriptions() {
        return this.tableDescPerFactoryLink.values();
    }

    Set<String> getServiceDocuments() {
        return this.tableDescPerDocumentKind.keySet();
    }

    Set<String> getTableNames() {
        return this.tableNames;
    }

    String getDocumentKindForFactoryLink(String factoryLink) {
        TableDescription td;
        if ((td = this.tableDescPerFactoryLink.get(UriUtils.normalizeUriPath(factoryLink))) != null) {
            return td.getDocumentKind();
        }

        return null;
    }

    Class<? extends ServiceDocument> getStateTypeForDocumentKind(String documentKind) {
        TableDescription td;
        if ((td = this.tableDescPerDocumentKind.get(documentKind)) != null) {
            return td.getStateType();
        }

        return null;
    }

    String getTableNameForDocumentSelfLink(String documentSelfLink) {
        return getTableNameForFactoryLink(UriUtils.getParentPath(documentSelfLink));
    }

    TableDescription getTableDescriptionForDocumentSelfLink(String documentSelfLink) {
        return getTableDescriptionForFactoryLink(UriUtils.getParentPath(documentSelfLink));
    }

    String getTableNameForFactoryLink(String factoryLink) {
        TableDescription td;
        if ((td = this.tableDescPerFactoryLink.get(factoryLink)) != null) {
            return td.getTableName();
        }

        return null;
    }

    TableDescription getTableDescriptionForFactoryLink(String factoryLink) {
        return this.tableDescPerFactoryLink.get(factoryLink);
    }

    String getTableNameForDocumentKind(String documentKind) {
        TableDescription td;
        if ((td = this.tableDescPerDocumentKind.get(documentKind)) != null) {
            return td.getTableName();
        }

        return null;
    }

    TableDescription getTableDescriptionForDocumentKind(String documentKind) {
        return this.tableDescPerDocumentKind.get(documentKind);
    }

    public PostgresLiquibaseSnapshot getSnapshot() {
        PostgresLiquibaseSnapshot snapshot = new PostgresLiquibaseSnapshot();
        snapshot.addTableDescriptions(getTableDescriptions());
        return snapshot;
    }

    private static String getFactoryLinkFieldValue(Class<? extends StatefulService> type) {
        try {
            Field f = type.getField(UriUtils.FIELD_NAME_FACTORY_LINK);
            return (String) f.get(null);
        } catch (Exception e) {
            logger.severe(String.format("%s field not found in class %s: %s",
                    UriUtils.FIELD_NAME_FACTORY_LINK,
                    type.getSimpleName(), Utils.toString(e)));
        }
        return null;
    }

    private static String getSelfLinkFieldValue(Class<? extends FactoryService> type) {
        try {
            Field f = type.getField(UriUtils.FIELD_NAME_SELF_LINK);
            return (String) f.get(null);
        } catch (Exception e) {
            logger.severe(String.format("%s field not found in class %s: %s",
                    UriUtils.FIELD_NAME_SELF_LINK,
                    type.getSimpleName(), Utils.toString(e)));
        }
        return null;
    }

    private static String toTableName(Class<? extends ServiceDocument> documentType, String
            factoryLink) {
        String documentTypeName = documentType.getSimpleName().toLowerCase();
        if (documentType.isAnnotationPresent(StateNameOverride.class)) {
            StateNameOverride stateNameOverride = documentType.getAnnotation(StateNameOverride.class);
            documentTypeName = stateNameOverride.value().toLowerCase();
        } else if (documentType.equals(ServiceDocument.class)) {
            String lastPath = UriUtils.getLastPathSegment(UriUtils.normalizeUriPath(factoryLink));
            documentTypeName = lastPath.replace('-', '_').toLowerCase();
        }

        if (documentTypeName.equals("state")) {
            Class<?> declaringClass = documentType.getDeclaringClass();
            if (declaringClass != null) {
                documentTypeName = declaringClass.getSimpleName().toLowerCase();
            }
        }

        final String prefix = factoryLink.startsWith(ServiceUriPaths.CORE) ? "docs_core_" : "docs_";
        return prefix + documentTypeName;
    }

    static String getColumnType(PropertyDescription pd) {
        switch (pd.typeName) {
        case LONG:
            return "bigint";
        case DOUBLE:
            return "numeric";
        case BOOLEAN:
            // TODO: use boolean if on the root?
            return "text";
        case BYTES:
            return "blob";
        case COLLECTION:
            return "jsonb";
        case PODO:
        case MAP:
            return "jsonb";
        case DATE:
            return "bigint";
        case STRING:
        case ENUM:
        case URI:
        case InternetAddressV4:
        case InternetAddressV6:
        default:
            return "text";
        }
    }

}
