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
import java.util.Arrays;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.serialization.ThreadLocalVersionHolder;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService2.UpgradeNewService2State;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.serialization.JsonMapper;

public enum UpgradeNewService2StateConverter implements
        JsonDeserializer<UpgradeNewService2State>, JsonSerializer<UpgradeNewService2State> {

    INSTANCE;

    public void init() {
        Utils.registerKind(UpgradeNewService2State.class, UpgradeNewService2State.KIND);
        Utils.registerCustomJsonMapper(UpgradeNewService2State.class,
                new JsonMapper((b) -> b.registerTypeAdapter(
                        UpgradeNewService2State.class,
                        UpgradeNewService2StateConverter.INSTANCE)));
    }

    @Override
    public UpgradeNewService2State deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context) throws JsonParseException {

        UpgradeNewService2State state = JSON_MAPPER.fromJson(json,
                UpgradeNewService2State.class);

        /*
         * Handle the upgrade from the previous version
         */

        // field3 is required! set default value if it applies
        if ((state.field3 == null) || (state.field3.isEmpty())) {
            state.field3 = "default value";
        }

        // field4 is required! set default value if it applies
        if (state.field4 == null) {
            state.field4 = 42L;
        }

        // field5 is required! set default value if it applies
        if ((state.field5 == null) || (state.field5.isEmpty())) {
            state.field5 = Arrays.asList("a", "b", "c");
        }

        return state;
    }

    @Override
    public JsonElement serialize(UpgradeNewService2State state, Type typeOfSrc,
            JsonSerializationContext context) {

        String version = ThreadLocalVersionHolder.getVersion();

        if (ReleaseConstants.API_VERSION_0_9_1.equals(version)) {

            /*
             * Handle the backward compatibility with the previous version
             */

        }

        String json = JSON_MAPPER.toJson(state);
        return JSON_PARSER.parse(json);
    }

}
