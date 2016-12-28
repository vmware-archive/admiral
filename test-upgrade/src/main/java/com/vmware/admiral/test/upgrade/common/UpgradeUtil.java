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

import java.net.URI;
import java.util.logging.Level;

import com.vmware.admiral.test.upgrade.version1.UpgradeOldService1.UpgradeOldService1State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService2.UpgradeOldService2State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService3.UpgradeOldService3State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService4.UpgradeOldService4State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService5.UpgradeOldService5State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService6.UpgradeOldService6State;
import com.vmware.admiral.test.upgrade.version2.BrandNewService.BrandNewServiceState;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService1.UpgradeNewService1State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService2.UpgradeNewService2State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService3.UpgradeNewService3State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService4.UpgradeNewService4State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService5.UpgradeNewService5State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService6.UpgradeNewService6State;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public final class UpgradeUtil {

    private UpgradeUtil() {
    }

    public static final String UPGRADE_SERVICE1_FACTORY_LINK = "/upgrade/service1-services";
    public static final String UPGRADE_SERVICE2_FACTORY_LINK = "/upgrade/service2-services";
    public static final String UPGRADE_SERVICE3_FACTORY_LINK = "/upgrade/service3-services";
    public static final String UPGRADE_SERVICE4_FACTORY_LINK = "/upgrade/service4-services";
    public static final String UPGRADE_SERVICE5_FACTORY_LINK = "/upgrade/service5-services";
    public static final String UPGRADE_SERVICE6_FACTORY_LINK = "/upgrade/service6-services";

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
        } else {
            throw new IllegalArgumentException(
                    "Unkown factory link for type '" + doc.getClass().getSimpleName() + "'!");
        }
    }

    /**
     * Forces the update of the Lucene index for the provided document by sending a self PUT request
     * with the document itself.
     *
     * If the index is not updated, queries referencing new values added/modified during the upgrade
     * won't return the expected results until some update (e.g. PUT or PATCH) is actually done to
     * the document.
     *
     * @param host
     *            {@link ServiceHost}
     * @param doc
     *            {@link ServiceDocument}
     */
    public static void forceLuceneIndexUpdate(ServiceHost host, ServiceDocument doc) {
        URI uri = UriUtils.buildUri(host, doc.documentSelfLink);
        host.log(Level.INFO, "Forcing index update for '%s'...", doc.documentSelfLink);
        host.sendRequest(Operation.createPut(uri)
                .setReferer(host.getUri())
                .setBody(doc)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.WARNING, "Index update failed for '%s': %s",
                                doc.documentSelfLink, Utils.toString(ex));
                    }
                }));
    }

}
