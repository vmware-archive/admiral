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

package com.vmware.admiral.common.util;

import java.net.URI;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class OperationUtil {

    public static Operation createForcedPost(URI uri) {
        return Operation.createPost(uri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
    }

    public static Operation createForcedPost(Service sender, String targetPath) {
        return createForcedPost(UriUtils.buildUri(sender.getHost(), targetPath));
    }

    /**
     * Helper method executing Operation.createGet.
     * Calls {@code callbackFunction} passing the result,
     * or in case of exception just logs it and returns.
     *
     * @param service          {@link Service}
     * @param link             document link
     * @param callbackFunction callback to receive the result
     * @param <T>              document state class, extends {@link ServiceDocument}
     */
    public static <T extends ServiceDocument> void getDocumentState(Service service, String link,
            Class<T> classT, Consumer<T> callbackFunction) {
        getDocumentState(service, link, classT, callbackFunction, null);
    }

    /**
     * Helper method executing Operation.createGet.
     * Calls {@code callbackFunction} passing the result,
     * or in case of exception logs it and calls the {@code failureFunction}.
     *
     * @param service          {@link Service}
     * @param link             document link
     * @param callbackFunction callback to receive the result
     * @param failureFunction  error callback
     * @param <T>              document state class, extends {@link ServiceDocument}
     */
    public static <T extends ServiceDocument> void getDocumentState(Service service, String link,
            Class<T> classT, Consumer<T> callbackFunction, Consumer<Throwable> failureFunction) {
        service.sendRequest(
                Operation
                        .createGet(service, link)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                service.getHost().log(
                                        Level.WARNING,
                                        "Failure retrieving document [%s], referrer: [%s],"
                                                + " context id [%s] : %s",
                                        link, o.getRefererAsString(),
                                        o.getContextId(), Utils.toString(e));

                                if (failureFunction != null) {
                                    failureFunction.accept(e);
                                }
                                return;
                            }

                            callbackFunction.accept(o.getBody(classT));
                        })
        );
    }

}
