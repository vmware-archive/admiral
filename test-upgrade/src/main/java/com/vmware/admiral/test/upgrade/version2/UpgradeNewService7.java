/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.upgrade.version2;

import static com.vmware.admiral.test.upgrade.common.UpgradeUtil.JSON_MAPPER;

import java.lang.reflect.Type;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.test.upgrade.common.UpgradeUtil;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService4;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.serialization.JsonMapper;

/**
 * Represents the base service {@link UpgradeOldService4} with changed field name.
 */
public class UpgradeNewService7 extends StatefulService {

    public static final String FACTORY_LINK = UpgradeUtil.UPGRADE_SERVICE7_FACTORY_LINK;

    public static class UpgradeNewService7State extends ServiceDocument {

        public static final String KIND = UpgradeUtil.UPGRADE_SERVICE7_STATE_KIND;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field1;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field2;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String upgradedField3;
    }

    // ---- Example based on com.vmware.xenon.common.TestGsonConfiguration ----

    static {
        Utils.registerCustomJsonMapper(UpgradeNewService7State.class,
                new JsonMapper((b) -> b.registerTypeAdapter(
                        UpgradeNewService7State.class,
                        UpdateNewService7StateConverter.INSTANCE)));
    }

    private enum UpdateNewService7StateConverter
            implements JsonDeserializer<UpgradeNewService7State> {

        INSTANCE;

        @Override
        public UpgradeNewService7State deserialize(JsonElement json, Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();

            // ---- custom transformation logic here ---

            if (jsonObject.has("field3")) {

                // "field3" renamed to "upgradedField3"

                JsonElement field3 = jsonObject.remove("field3");
                String field3Value = field3.getAsString();
                jsonObject.addProperty("upgradedField3", field3Value);

                UpgradeUtil.trackStateUpgraded(jsonObject);
            }

            return JSON_MAPPER.fromJson(json, UpgradeNewService7State.class);
        }
    }

    // ---- ----

    public UpgradeNewService7() {
        super(UpgradeNewService7State.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
        UpgradeNewService7State body = post.getBody(UpgradeNewService7State.class);
        AssertUtil.assertNotNull(body, "body");

        // upgrade the old entities accordingly...
        handleStateUpgrade(body);

        // validate based on annotations
        Utils.validateState(getStateDescription(), body);
        super.handleStart(post);
    }

    private void handleStateUpgrade(UpgradeNewService7State state) {
        // update Lucene index if needed
        if (UpgradeUtil.untrackStateUpgraded(state)) {
            UpgradeUtil.forceLuceneIndexUpdate(getHost(), state);
        }
    }

}
