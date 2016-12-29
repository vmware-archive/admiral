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
import static com.vmware.admiral.test.upgrade.common.UpgradeUtil.JSON_PARSER;

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
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

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

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Long field3;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Set<String> field4;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Map<String, String> field5;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Map<String, String> field6;
    }

    // ---- Example based on com.vmware.xenon.common.TestGsonConfiguration ----

    static {
        Utils.registerCustomJsonMapper(UpgradeNewService3State.class,
                new JsonMapper((b) -> b.registerTypeAdapter(
                        UpgradeNewService3State.class,
                        UpdateNewService3StateConverter.INSTANCE)));
    }

    private enum UpdateNewService3StateConverter
            implements JsonDeserializer<UpgradeNewService3State> {

        INSTANCE;

        @SuppressWarnings("unchecked")
        @Override
        public UpgradeNewService3State deserialize(JsonElement json, Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            try {
                return JSON_MAPPER.fromJson(json, UpgradeNewService3State.class);
            } catch (JsonSyntaxException e) {
                JsonObject jsonObject = json.getAsJsonObject();

                // ---- custom transformation logic here ---

                // "field3" upgrade: String -> Long

                JsonElement field3 = jsonObject.remove("field3");
                String oldField3Value = field3.getAsString();
                Long newField3Value = ("fortytwo".equals(oldField3Value)) ? 42L : 0L;
                jsonObject.addProperty("field3", newField3Value);

                // "field5" upgrade: List ("a", "b", "c") -> Map ("a=a", "b=b", "c=c")

                JsonElement field5 = jsonObject.remove("field5");
                List<String> oldField5Value = Utils.fromJson(field5, List.class);
                Map<String, String> newField5Value = oldField5Value.stream()
                        .collect(Collectors.toMap(Function.identity(), Function.identity()));
                jsonObject.add("field5", JSON_PARSER.parse(Utils.toJson(newField5Value)));

                UpgradeUtil.trackStateUpgraded(jsonObject);

                return JSON_MAPPER.fromJson(json, UpgradeNewService3State.class);
            }
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
        super.handleStart(post);
    }

    private void handleStateUpgrade(UpgradeNewService3State state) {
        // update Lucene index if needed
        if (UpgradeUtil.untrackStateUpgraded(state)) {
            UpgradeUtil.forceLuceneIndexUpdate(getHost(), state);
        }
    }

}
