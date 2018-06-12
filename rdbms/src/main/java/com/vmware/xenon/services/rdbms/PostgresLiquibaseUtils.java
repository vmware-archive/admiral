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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.sql.DataSource;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.change.core.CreateIndexChange;
import liquibase.changelog.ChangeLogHistoryServiceFactory;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.OfflineConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.diff.DiffGeneratorFactory;
import liquibase.diff.DiffResult;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.DiffToChangeLog;
import liquibase.exception.LiquibaseException;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import liquibase.serializer.LiquibaseSerializable;
import liquibase.serializer.SnapshotSerializerFactory;
import liquibase.serializer.core.xml.XMLChangeLogSerializer;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.core.Catalog;
import liquibase.structure.core.Schema;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.config.XenonConfiguration;
import com.vmware.xenon.services.rdbms.PostgresSchemaManager.ColumnDescription;

public class PostgresLiquibaseUtils {

    private static final Logger logger = Logger.getLogger(PostgresLiquibaseUtils.class.getName());

    private static final long LOCK_RELEASE_TIMEOUT_MINUTES = XenonConfiguration.number(
            PostgresLiquibaseUtils.class, "lockReleaseTimeoutMinutes", 0);

    public static DiffResult compareSnapshots(String latestSnapshotJson,
            String currentSnapshotJson) throws Exception {
        // Resource accessor to read content from strings
        ResourceAccessor resourceAccessor = new ResourceAccessor() {
            @Override
            public Set<InputStream> getResourcesAsStream(String path) throws IOException {
                String content;
                switch (path) {
                case "latest.json":
                    content = latestSnapshotJson;
                    break;
                case "current.json":
                    content = currentSnapshotJson;
                    break;
                default:
                    return null;
                }
                return Collections
                        .singleton(new ByteArrayInputStream(content.getBytes(Utils.CHARSET)));
            }

            @Override
            public Set<String> list(String relativeTo, String path, boolean includeFiles,
                    boolean includeDirectories, boolean recursive) {
                return null;
            }

            @Override
            public ClassLoader toClassLoader() {
                return null;
            }
        };

        // loading current snapshot
        OfflineConnection currentOfflineConnection = new OfflineConnection(
                "offline:postgresql?snapshot=current.json", resourceAccessor);
        Database currentDatabase = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(currentOfflineConnection);

        // Loading latest snapshot
        OfflineConnection latestOfflineConnection = new OfflineConnection(
                "offline:postgresql?snapshot=latest.json", resourceAccessor);
        Database latestDatabase = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(latestOfflineConnection);

        // Compare
        return DiffGeneratorFactory.getInstance().compare(currentDatabase, latestDatabase,
                new CompareControl()
                        .addSuppressedField(Catalog.class, "name")
                        .addSuppressedField(Schema.class, "name"));
    }

    public static String getLatestSnapshotAsJson(String snapshotResourcePath) {
        try {
            // TODO: Potential issue with wrong class loader for getting resources
            String json = PostgresHostUtils
                    .getResourceAsString(Thread.currentThread().getContextClassLoader(),
                            snapshotResourcePath);
            if (json.isEmpty()) {
                // Throw an Exception for empty content to treat it as empty database
                throw new IOException("Empty content");
            }
            return json;
        } catch (IOException e) {
            // Use empty snapshot if latest snapshot is missing, this is used for
            // creating initial changelog
            logger.warning(String.format(
                    "Missing %s resource, using empty snapshot for Liquibase change log creation: %s",
                    snapshotResourcePath, e));
            return PostgresLiquibaseSnapshot.emptySnapshotAsJson();
        }
    }

    private static class MyXMLChangeLogSerializer extends XMLChangeLogSerializer {
        private final PostgresLiquibaseSnapshot currentSnapshot;

        MyXMLChangeLogSerializer(PostgresLiquibaseSnapshot currentSnapshot) {
            this.currentSnapshot = currentSnapshot;
        }

        @Override
        public Element createNode(LiquibaseSerializable object) {
            Element element = super.createNode(object);

            if (object instanceof ChangeSet) {
                ChangeSet changeSet = (ChangeSet) object;
                changeSet.getChanges().forEach(change -> {
                    if (change instanceof CreateIndexChange) {
                        CreateIndexChange cic = (CreateIndexChange) change;
                        if (cic.getColumns().size() != 1) {
                            return;
                        }

                        ColumnDescription cd = this.currentSnapshot
                                .getColumnDescriptionByIndexId(cic.getIndexName());
                        String indexType = cd != null ? cd.getIndexType() : null;

                        if (indexType != null) {
                            Document doc = element.getOwnerDocument();
                            Element modifySqlElement = doc.createElement("modifySql");
                            Element replaceElement = doc.createElement("regExpReplace");
                            replaceElement.setAttribute("replace", "\\(.+\\)");
                            replaceElement.setAttribute("with",
                                    String.format(" USING %s $0", indexType));
                            modifySqlElement.appendChild(replaceElement);
                            element.appendChild(modifySqlElement);
                        }
                    }
                });
            }
            return element;
        }
    }

    public static String diffToChangeLog(DiffResult diffResult,
            PostgresLiquibaseSnapshot currentSnapshot) throws Exception {
        DiffToChangeLog changeLogWriter = new DiffToChangeLog(diffResult, new DiffOutputControl()
                .setIncludeSchema(false)
                .setIncludeCatalog(false));
        changeLogWriter.setDiffResult(diffResult);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        changeLogWriter.print(new PrintStream(os), new MyXMLChangeLogSerializer(currentSnapshot));
        return os.toString();
    }

    public static boolean validateSnapshot(PostgresLiquibaseSnapshot currentSnapshot,
            String latestSnapshotResourcePath, boolean createFilesOnDifference) throws Exception {
        String currentSnapshotJson = currentSnapshot.getSnapshotAsJson();

        // Get latest snapshot
        String latestSnapshotJson = getLatestSnapshotAsJson(latestSnapshotResourcePath);

        // Compare current snapshot with latest-snapshot.json
        DiffResult diffResult = compareSnapshots(latestSnapshotJson, currentSnapshotJson);
        if (diffResult.areEqual()) {
            return true;
        }

        // Snapshots are different
        logger.severe(String.format("Need to update '%s' to current Liquibase snapshot",
                latestSnapshotResourcePath));

        if (createFilesOnDifference) {
            Path latestSnapshotPath = Paths.get("current-snapshot.json").toAbsolutePath();
            logger.severe(String.format("*** Current Liquibase snapshot: %s\n%s\n",
                    latestSnapshotPath, currentSnapshotJson));
            Files.write(latestSnapshotPath,
                    currentSnapshotJson.getBytes(Utils.CHARSET),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            // change log
            String changeLogXml = diffToChangeLog(diffResult, currentSnapshot);
            Path latestChangeLogPath = Paths.get("current-changelog.xml").toAbsolutePath();
            logger.severe(String.format("*** Current Liquibase changelog: %s\n%s\n",
                    latestChangeLogPath, changeLogXml));

            Files.write(latestChangeLogPath,
                    changeLogXml.getBytes(Utils.CHARSET),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }

        return false;
    }

    private static void checkLiquibaseTables(DataSource ds) throws Exception {
        // TODO: workaround to resolve locking issue with postgres
        // https://liquibase.jira.com/browse/CORE-2846
        int retries = 5;
        for (int retry = 0; retry < retries; retry++) {
            try (Connection conn = ds.getConnection()) {
                Liquibase liquibase = new Liquibase(null, null, new JdbcConnection(conn));

                try {
                    liquibase.checkLiquibaseTables(false, null, new Contexts(),
                            new LabelExpression());
                    break;
                } catch (Exception e) {
                    logger.warning(String.format(
                            "checkLiquibaseTables failed, can be ignored (retry %s/%s): %s",
                            retry + 1, retries, e));

                    TimeUnit.SECONDS.sleep(2);
                }
            }
        }
    }

    public static void update(DataSource ds, String changelogResourceName) throws Exception {
        update(ds, changelogResourceName, new ClassLoaderResourceAccessor());
    }

    public static void updateFromMemory(DataSource ds, String changelogXml) throws Exception {
        // Resource accessor to read content from strings
        ResourceAccessor resourceAccessor = new ResourceAccessor() {
            @Override
            public Set<InputStream> getResourcesAsStream(String path) throws IOException {
                switch (path) {
                case "changelog.xml":
                    return Collections.singleton(
                            new ByteArrayInputStream(changelogXml.getBytes(Utils.CHARSET)));
                default:
                    return null;
                }
            }

            @Override
            public Set<String> list(String relativeTo, String path, boolean includeFiles,
                    boolean includeDirectories, boolean recursive) {
                return null;
            }

            @Override
            public ClassLoader toClassLoader() {
                return null;
            }
        };

        update(ds, "changelog.xml", resourceAccessor);
    }

    private static void update(DataSource ds, String changeLogFile,
            ResourceAccessor resourceAccessor) throws Exception {
        // TODO: workaround to resolve locking issue with postgres
        checkLiquibaseTables(ds);

        try (Connection conn = ds.getConnection()) {
            Contexts contexts = new Contexts("");
            LabelExpression labels = new LabelExpression();
            Liquibase liquibase = new Liquibase(changeLogFile, resourceAccessor,
                    new JdbcConnection(conn));

            // Check if update is needed without locking liquibase tables
            List<ChangeSet> changes = liquibase.listUnrunChangeSets(contexts, labels);
            if (changes.isEmpty()) {
                return;
            }

            // Release locks on timeout
            releaseLockOnTimeout(liquibase);

            // Reset liquibase as it has cached the run change sets during the
            // listUnrunChangeSets() call which happens before acquiring the update lock and may
            // miss latest entries
            ChangeLogHistoryServiceFactory.getInstance().resetAll();

            // Update
            liquibase.update(contexts, labels);
        }
    }

    private static void releaseLockOnTimeout(Liquibase liquibase)
            throws LiquibaseException, UnsupportedEncodingException {
        if (LOCK_RELEASE_TIMEOUT_MINUTES <= 0) {
            return;
        }

        for (DatabaseChangeLogLock lock : liquibase.listLocks()) {
            long elapsedMinutes = TimeUnit.MILLISECONDS
                    .toMinutes(System.currentTimeMillis() - lock.getLockGranted().getTime());
            if (elapsedMinutes >= LOCK_RELEASE_TIMEOUT_MINUTES) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                liquibase.reportLocks(new PrintStream(os));
                logger.warning(String.format("Releasing liquibase locks after %d minutes: %s",
                        elapsedMinutes, os.toString(Utils.CHARSET)));

                liquibase.forceReleaseLocks();
                break;
            }
        }
    }

    public static String createDatabaseSnapshotAsString(DataSource ds) throws LiquibaseException,
            SQLException {
        try (Connection conn = ds.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));

            DatabaseSnapshot currentSnapshot = SnapshotGeneratorFactory
                    .getInstance().createSnapshot(database.getDefaultSchema(), database,
                            new SnapshotControl(database));

            return SnapshotSerializerFactory.getInstance().getSerializer("json")
                    .serialize(currentSnapshot, true);
        }
    }
}
