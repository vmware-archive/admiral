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
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService5.UpgradeNewService5State;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.serialization.JsonMapper;

public enum UpgradeNewService5StateConverter implements
        JsonDeserializer<UpgradeNewService5State>, JsonSerializer<UpgradeNewService5State> {

    INSTANCE;

    public void init() {
        Utils.registerKind(UpgradeNewService5State.class, UpgradeNewService5State.KIND);
        Utils.registerCustomJsonMapper(UpgradeNewService5State.class,
                new JsonMapper((b) -> b.registerTypeAdapter(
                        UpgradeNewService5State.class,
                        UpgradeNewService5StateConverter.INSTANCE)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public UpgradeNewService5State deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context) throws JsonParseException {

        UpgradeNewService5State state = JSON_MAPPER.fromJson(json,
                UpgradeNewService5State.class);

        if (state.field3 != null && state.field4 != null && state.field5 != null) {
            state.field345 = String.join("#",
                    Arrays.asList(state.field3, state.field4, state.field5));
            state.field3 = null;
            state.field4 = null;
            state.field5 = null;
        }

        if (state.field678 != null) {
            String[] values = state.field678.split("/");
            state.field6 = values[0];
            state.field7 = values[1];
            state.field8 = values[2];
            state.field678 = null;
        }

        return state;
    }

    @SuppressWarnings("deprecation")
    @Override
    public JsonElement serialize(UpgradeNewService5State state, Type typeOfSrc,
            JsonSerializationContext context) {

        String version = ThreadLocalVersionHolder.getVersion();

        if (ReleaseConstants.API_VERSION_0_9_1.equals(version)) {

            /*
             * Handle the backward compatibility with the previous version
             */
            if (state.field345 != null) {
                String[] values = state.field345.split("#");
                state.field3 = values[0];
                state.field4 = values[1];
                state.field5 = values[2];
            }

            if (state.field6 != null && state.field7 != null && state.field8 != null) {
                state.field678 = String.join("/",
                        Arrays.asList(state.field6, state.field7, state.field8));
            }
        }

        String json = JSON_MAPPER.toJson(state);
        return JSON_PARSER.parse(json);
    }

}
