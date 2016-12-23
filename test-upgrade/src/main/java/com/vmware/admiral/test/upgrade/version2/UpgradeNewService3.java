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

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.test.upgrade.common.UpgradeUtil;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService3;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.serialization.JsonMapper;

/**
 * Represents the base service {@link UpgradeOldService3} with new field types.
 */
public class UpgradeNewService3 extends StatefulService {

    public static final String FACTORY_LINK = UpgradeUtil.UPGRADE_SERVICE3_FACTORY_LINK;

    public static class UpgradeNewService3State extends ServiceDocument {

        public static final String KIND = UpgradeUtil.UPGRADE_SERVICE3_STATE_KIND;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field1;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field2;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Long field3;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Set<String> field4;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Map<String, String> field5;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Map<String, String> field6;
    }

    // ---- Example based on com.vmware.xenon.common.TestGsonConfiguration ----

    static {
        Utils.registerCustomJsonMapper(UpgradeNewService3State.class,
                new JsonMapper((b) -> {
                    b.registerTypeAdapter(Long.class,
                            UpdateNewService3StateLongConverter.INSTANCE);
                    b.registerTypeAdapter(Map.class,
                            UpdateNewService3StateMapConverter.INSTANCE);
                }));
    }

    private enum UpdateNewService3StateLongConverter implements JsonDeserializer<Long> {

        INSTANCE;

        @Override
        public Long deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            /*
             * The deserializer is applied to all the fields from UpgradeNewService3State of type
             * Long, not only "field3" (i.e. "documentVersion", etc.). Maybe there's a better way...
             */
            try {
                // it's already a Long
                return json.getAsLong();
            } catch (NumberFormatException e) {
                String asString = json.getAsString();
                // ---- custom transformation logic here ---
                if ("fortytwo".equals(asString)) {
                    return 42L;
                } else {
                    throw e;
                }
            }
        }
    }

    private enum UpdateNewService3StateMapConverter
            implements JsonDeserializer<Map<String, String>> {

        INSTANCE;

        @Override
        public Map<String, String> deserialize(JsonElement json, Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            /*
             * The deserializer is applied to all the fields from UpgradeNewService3State of type
             * Map, not only "field5" (i.e. "field6", etc.). Maybe there's a better way...
             */
            if (json.isJsonObject()) {
                // it's already a Map
                return Utils.fromJson(json, typeOfT);
            }
            // ---- custom transformation logic here ---
            // e.g. "a", "b", "c" -> "a=a", "b=b", "c=c"
            @SuppressWarnings("unchecked")
            List<String> asList = Utils.fromJson(json, List.class);
            Map<String, String> asMap = asList.stream()
                    .collect(Collectors.toMap(Function.identity(), Function.identity()));
            return asMap;
        }
    }

    // ---- ----

    public UpgradeNewService3() {
        super(UpgradeNewService3State.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
        UpgradeNewService3State body = post.getBody(UpgradeNewService3State.class);
        AssertUtil.assertNotNull(body, "body");

        // upgrade the old entities accordingly...
        handleStateUpgrade(body);

        // validate based on annotations
        Utils.validateState(getStateDescription(), body);
        super.handleCreate(post);
    }

    private void handleStateUpgrade(UpgradeNewService3State state) {
        // handle the case when a field becomes mandatory...
    }

}
