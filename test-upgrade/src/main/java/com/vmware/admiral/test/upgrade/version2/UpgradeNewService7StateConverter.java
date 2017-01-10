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

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.serialization.ThreadLocalVersionHolder;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService7.UpgradeNewService7State;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.serialization.JsonMapper;

public enum UpgradeNewService7StateConverter implements
        JsonDeserializer<UpgradeNewService7State>, JsonSerializer<UpgradeNewService7State> {

    INSTANCE;

    public void init() {
        Utils.registerKind(UpgradeNewService7State.class, UpgradeNewService7State.KIND);
        Utils.registerCustomJsonMapper(UpgradeNewService7State.class,
                new JsonMapper((b) -> b.registerTypeAdapter(
                        UpgradeNewService7State.class,
                        UpgradeNewService7StateConverter.INSTANCE)));
    }

    @Override
    public UpgradeNewService7State deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context) throws JsonParseException {

        JsonObject jsonObject = json.getAsJsonObject();

        if (jsonObject.has("field3")) {

            // "field3" renamed to "upgradedField3"

            JsonElement field3 = jsonObject.remove("field3");
            String field3Value = field3.getAsString();
            jsonObject.addProperty("upgradedField3", field3Value);
        }

        return JSON_MAPPER.fromJson(json, UpgradeNewService7State.class);
    }

    @Override
    public JsonElement serialize(UpgradeNewService7State state, Type typeOfSrc,
            JsonSerializationContext context) {

        String version = ThreadLocalVersionHolder.getVersion();

        String json = JSON_MAPPER.toJson(state);
        JsonElement jsonElement = JSON_PARSER.parse(json);

        if (ReleaseConstants.API_VERSION_0_9_1.equals(version)) {

            /*
             * Handle the backward compatibility with the previous version
             */
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            // "upgradedField3" renamed to "field3"

            JsonElement field3 = jsonObject.remove("upgradedField3");
            String field3Value = field3.getAsString();
            jsonObject.addProperty("field3", field3Value);
        }

        return jsonElement;
    }

}
