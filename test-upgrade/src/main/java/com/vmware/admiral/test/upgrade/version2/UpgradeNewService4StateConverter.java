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
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.serialization.ThreadLocalVersionHolder;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService4.UpgradeNewService4State;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.serialization.JsonMapper;

public enum UpgradeNewService4StateConverter implements
        JsonDeserializer<UpgradeNewService4State>, JsonSerializer<UpgradeNewService4State> {

    INSTANCE;

    public void init() {
        Utils.registerKind(UpgradeNewService4State.class, UpgradeNewService4State.KIND);
        Utils.registerCustomJsonMapper(UpgradeNewService4State.class,
                new JsonMapper((b) -> b.registerTypeAdapter(
                        UpgradeNewService4State.class,
                        UpgradeNewService4StateConverter.INSTANCE)));
    }

    @Override
    public UpgradeNewService4State deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context) throws JsonParseException {

        String version = ThreadLocalVersionHolder.getVersion();

        // System.out.println(">>>>>>>>>> deserialize '" + version + "' in thread: "
        // + Thread.currentThread().getName());
        // Thread.dumpStack();

        UpgradeNewService4State state = JSON_MAPPER.fromJson(json,
                UpgradeNewService4State.class);

        if (ReleaseConstants.API_VERSION_0_9_1.equals(version)) {

            /*
             * Handle the upgrade from the previous version
             */

            if ((state.field3 != null)
                    && (state.field3
                            .startsWith(UpgradeNewService4State.FIELD3_PREFIX_DEPRECATED))) {
                state.field3 = state.field3.replaceFirst(
                        UpgradeNewService4State.FIELD3_PREFIX_DEPRECATED,
                        UpgradeNewService4State.FIELD3_PREFIX);
            }

            if ((state.field4 != null)
                    && (!state.field4.startsWith(UpgradeNewService4State.FIELD4_PREFIX))) {
                state.field4 = UpgradeNewService4State.FIELD4_PREFIX + state.field4;
            }
        }

        return state;
    }

    @Override
    public JsonElement serialize(UpgradeNewService4State state, Type typeOfSrc,
            JsonSerializationContext context) {

        String version = ThreadLocalVersionHolder.getVersion();

        // System.out.println(">>>>>>>>>> serialize '" + version + "' in thread: "
        // + Thread.currentThread().getName());
        // Thread.dumpStack();

        if (ReleaseConstants.API_VERSION_0_9_1.equals(version)) {

            /*
             * Handle the backward compatibility with the previous version
             */

            if ((state.field3 != null)
                    && (state.field3.startsWith(UpgradeNewService4State.FIELD3_PREFIX))) {
                state.field3 = state.field3.replaceFirst(
                        UpgradeNewService4State.FIELD3_PREFIX,
                        UpgradeNewService4State.FIELD3_PREFIX_DEPRECATED);
            }

            if ((state.field4 != null)
                    && (state.field4.startsWith(UpgradeNewService4State.FIELD4_PREFIX))) {
                state.field4 = state.field4.replaceFirst(UpgradeNewService4State.FIELD4_PREFIX, "");
            }
        }

        String json = JSON_MAPPER.toJson(state);
        return JSON_PARSER.parse(json);
    }

}
