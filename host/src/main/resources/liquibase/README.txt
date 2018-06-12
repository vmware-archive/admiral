Instructions on generating a new liquibase changelog
====================================================

1. Always make sure you have latest of both provisioning-service and photon-model, as they both
contribute to the schema.
2. Start the ManagementHost with the --withPostgres=true option. This will fail with invalid
snapshot but will create the new snapshot and changelog in the working directory. The generated
changelog is the delta between old and new snapshots.
3. Copy current-snapshot.json to host/src/main/resources/liquibase/latest-snapshot.json.
4. Copy current-changelog.xml to host/src/main/resources/liquibase/changelog/<index>-<description>
.xml. Use subsequent index and short meaningful description.
5. Update host/src/main/resources/liquibase/changelog.xml to include the new changelog file.
6. Start the ManagementHost again to verify the snapshot is now valid.
