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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.serialization.ThreadLocalVersionHolder;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService3.UpgradeNewService3State;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.serialization.JsonMapper;

public enum UpgradeNewService3StateConverter implements
        JsonDeserializer<UpgradeNewService3State>, JsonSerializer<UpgradeNewService3State> {

    INSTANCE;

    public void init() {
        Utils.registerKind(UpgradeNewService3State.class, UpgradeNewService3State.KIND);
        Utils.registerCustomJsonMapper(UpgradeNewService3State.class,
                new JsonMapper((b) -> b.registerTypeAdapter(
                        UpgradeNewService3State.class,
                        UpgradeNewService3StateConverter.INSTANCE)));
    }

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

            // JsonElement field5 = jsonObject.remove("field5");
            // List<String> oldField5Value = Utils.fromJson(field5, List.class);
            // Map<String, String> newField5Value = oldField5Value.stream()
            // .collect(Collectors.toMap(Function.identity(), Function.identity()));
            // jsonObject.add("field5", JSON_PARSER.parse(Utils.toJson(newField5Value)));

            return JSON_MAPPER.fromJson(json, UpgradeNewService3State.class);
        }
    }

    @Override
    public JsonElement serialize(UpgradeNewService3State state, Type typeOfSrc,
            JsonSerializationContext context) {

        String version = ThreadLocalVersionHolder.getVersion();

        String json = JSON_MAPPER.toJson(state);
        JsonElement jsonElement = JSON_PARSER.parse(json);

        if (ReleaseConstants.API_VERSION_0_9_1.equals(version)) {

            /*
             * Handle the backward compatibility with the previous version
             */
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            // "field3" upgrade: Long -> String

            JsonElement field3 = jsonObject.remove("field3");
            Long oldField3Value = field3.getAsLong();
            String newField3Value = (oldField3Value == 42L) ? "fortytwo" : "";
            jsonObject.addProperty("field3", newField3Value);

            // "field5" upgrade: Map ("a=a", "b=b", "c=c") -> List ("a", "b", "c")

            // JsonElement field5 = jsonObject.remove("field5");
            // Map<String, String> oldField5Value = Utils.fromJson(field5, Map.class);
            // List<String> newField5Value = new ArrayList<>(oldField5Value.keySet());
            // jsonObject.add("field5", JSON_PARSER.parse(Utils.toJson(newField5Value)));
        }

        return jsonElement;
    }

}
