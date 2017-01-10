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

package com.vmware.admiral.test.upgrade.common;

import java.util.function.Consumer;
import java.util.logging.Level;

import com.google.gson.JsonParser;

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService1.UpgradeOldService1State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService2.UpgradeOldService2State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService3.UpgradeOldService3State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService4.UpgradeOldService4State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService5.UpgradeOldService5State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService6.UpgradeOldService6State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService7.UpgradeOldService7State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService8.UpgradeOldService8State;
import com.vmware.admiral.test.upgrade.version2.BrandNewService.BrandNewServiceState;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService1.UpgradeNewService1State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService2.UpgradeNewService2State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService3.UpgradeNewService3State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService4.UpgradeNewService4State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService5.UpgradeNewService5State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService6.UpgradeNewService6State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService7.UpgradeNewService7State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService8.UpgradeNewService8State;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.serialization.JsonMapper;

public final class UpgradeUtil {

    private UpgradeUtil() {
    }

    /*
     * WARNING! This default JSON_MAPPER ignores Xenon's Utils.CUSTOM_JSON customizations! This may
     * be an issue if the mapped class attributes override their default mappers as well.
     */
    public static final JsonMapper JSON_MAPPER = new JsonMapper();

    public static final JsonParser JSON_PARSER = new JsonParser();

    public static final String UPGRADE_TASK_SERVICE_FACTORY_LINK = "/upgrade/task-services";

    public static final String UPGRADE_SERVICE1_FACTORY_LINK = "/upgrade/service1-services";
    public static final String UPGRADE_SERVICE2_FACTORY_LINK = "/upgrade/service2-services";
    public static final String UPGRADE_SERVICE3_FACTORY_LINK = "/upgrade/service3-services";
    public static final String UPGRADE_SERVICE4_FACTORY_LINK = "/upgrade/service4-services";
    public static final String UPGRADE_SERVICE5_FACTORY_LINK = "/upgrade/service5-services";
    public static final String UPGRADE_SERVICE6_FACTORY_LINK = "/upgrade/service6-services";
    public static final String UPGRADE_SERVICE7_FACTORY_LINK = "/upgrade/service7-services";
    public static final String UPGRADE_SERVICE8_FACTORY_LINK = "/upgrade/service8-services";

    public static final String UPGRADE_BRAND_NEW_SERVICE_FACTORY_LINK = "/upgrade/brand-new-service-services";

    public static final String UPGRADE_SERVICE1_STATE_KIND = Utils
            .buildKind(UpgradeOldService1State.class);
    public static final String UPGRADE_SERVICE2_STATE_KIND = Utils
            .buildKind(UpgradeOldService2State.class);
    public static final String UPGRADE_SERVICE3_STATE_KIND = Utils
            .buildKind(UpgradeOldService3State.class);
    public static final String UPGRADE_SERVICE4_STATE_KIND = Utils
            .buildKind(UpgradeOldService4State.class);
    public static final String UPGRADE_SERVICE5_STATE_KIND = Utils
            .buildKind(UpgradeOldService5State.class);
    public static final String UPGRADE_SERVICE6_STATE_KIND = Utils
            .buildKind(UpgradeOldService6State.class);
    public static final String UPGRADE_SERVICE7_STATE_KIND = Utils
            .buildKind(UpgradeOldService7State.class);
    public static final String UPGRADE_SERVICE8_STATE_KIND = Utils
            .buildKind(UpgradeOldService8State.class);

    public static String getFactoryLinkByDocumentKind(ServiceDocument doc) {
        if (doc instanceof BrandNewServiceState) {
            return UPGRADE_BRAND_NEW_SERVICE_FACTORY_LINK;
        } else if ((doc instanceof UpgradeOldService1State)
                || (doc instanceof UpgradeNewService1State)) {
            return UPGRADE_SERVICE1_FACTORY_LINK;
        } else if ((doc instanceof UpgradeOldService2State)
                || (doc instanceof UpgradeNewService2State)) {
            return UPGRADE_SERVICE2_FACTORY_LINK;
        } else if ((doc instanceof UpgradeOldService3State)
                || (doc instanceof UpgradeNewService3State)) {
            return UPGRADE_SERVICE3_FACTORY_LINK;
        } else if ((doc instanceof UpgradeOldService4State)
                || (doc instanceof UpgradeNewService4State)) {
            return UPGRADE_SERVICE4_FACTORY_LINK;
        } else if ((doc instanceof UpgradeOldService5State)
                || (doc instanceof UpgradeNewService5State)) {
            return UPGRADE_SERVICE5_FACTORY_LINK;
        } else if ((doc instanceof UpgradeOldService6State)
                || (doc instanceof UpgradeNewService6State)) {
            return UPGRADE_SERVICE6_FACTORY_LINK;
        } else if ((doc instanceof UpgradeOldService7State)
                || (doc instanceof UpgradeNewService7State)) {
            return UPGRADE_SERVICE7_FACTORY_LINK;
        } else if ((doc instanceof UpgradeOldService8State)
                || (doc instanceof UpgradeNewService8State)) {
            return UPGRADE_SERVICE8_FACTORY_LINK;
        } else {
            throw new IllegalArgumentException(
                    "Unkown factory link for type '" + doc.getClass().getSimpleName() + "'!");
        }
    }

    public static void setOperationRequestApiVersion(Operation op, String apiVersion) {
        if ((apiVersion != null) && (!apiVersion.isEmpty())) {
            String acceptHeader = op.getRequestHeader(Operation.ACCEPT_HEADER);
            if ((acceptHeader != null) && (!acceptHeader.isEmpty())) {
                acceptHeader += ";" + ReleaseConstants.VERSION_PREFIX + apiVersion;
            } else {
                acceptHeader = ReleaseConstants.VERSION_PREFIX + apiVersion;
            }
            op.addRequestHeader(Operation.ACCEPT_HEADER, acceptHeader);
        }
    }

    public static <T extends ServiceDocument> void upgradeState(Service service, String link,
            Class<T> clazz, String oldVersion, Consumer<T> callbackFunction) {

        Operation get = Operation.createGet(service, link).setCompletion((o, e) -> {
            if (e != null) {
                service.getHost().log(Level.WARNING,
                        "Failure retrieving document '%s': %s",
                        link, Utils.toString(e));
                return;
            }

            // Force the deserialization!
            String body = o.getBody(String.class);

            Operation put = Operation.createPut(service, link)
                    .setReferer(service.getHost().getUri())
                    .setBody(body)
                    .setCompletion((o2, e2) -> {
                        if (e2 != null) {
                            service.getHost().log(Level.WARNING,
                                    "Index update failed for document '%s': %s",
                                    link, Utils.toString(e2));
                        }
                        callbackFunction.accept(null);
                    });

            setOperationRequestApiVersion(put, oldVersion);
            put.sendWith(service);
        });

        setOperationRequestApiVersion(get, oldVersion);
        get.sendWith(service);
    }

}
