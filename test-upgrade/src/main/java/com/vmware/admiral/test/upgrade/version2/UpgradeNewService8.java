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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

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
 * Represents the base service {@link UpgradeOldService4} with removed field.
 */
public class UpgradeNewService8 extends StatefulService {

    public static final String FACTORY_LINK = UpgradeUtil.UPGRADE_SERVICE8_FACTORY_LINK;

    public static class UpgradeNewService8State extends ServiceDocument {

        public static final String KIND = UpgradeUtil.UPGRADE_SERVICE8_STATE_KIND;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field1;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field2;
    }

    // ---- Example based on com.vmware.xenon.common.TestGsonConfiguration ----

    static {
        Utils.registerCustomJsonMapper(UpgradeNewService8State.class,
                new JsonMapper((b) -> b.registerTypeAdapter(
                        UpgradeNewService8State.class,
                        UpdateNewService8StateConverter.INSTANCE)));
    }

    private enum UpdateNewService8StateConverter
            implements JsonDeserializer<UpgradeNewService8State> {

        INSTANCE;

        @Override
        public UpgradeNewService8State deserialize(JsonElement json, Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();

            // ---- custom transformation logic here ---

            if (jsonObject.has("field3")) {

                // "field3" removed, it will be ignored

                UpgradeUtil.trackStateUpgraded(jsonObject);
            }

            return JSON_MAPPER.fromJson(json, UpgradeNewService8State.class);
        }
    }

    // ---- ----

    public UpgradeNewService8() {
        super(UpgradeNewService8State.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
        UpgradeNewService8State body = post.getBody(UpgradeNewService8State.class);
        AssertUtil.assertNotNull(body, "body");

        // upgrade the old entities accordingly...
        handleStateUpgrade(body);

        // validate based on annotations
        Utils.validateState(getStateDescription(), body);
        super.handleStart(post);
    }

    private void handleStateUpgrade(UpgradeNewService8State state) {
        // update Lucene index if needed
        if (UpgradeUtil.untrackStateUpgraded(state)) {
            UpgradeUtil.forceLuceneIndexUpdate(getHost(), state);
        }
    }

}
