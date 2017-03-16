/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host.interceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.logging.Level;

import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.Utils;

/**
 * A registry of service operation interceptors.
 */
public class OperationInterceptorRegistry {
    private static class InterceptorData {
        public Action action;
        public BiFunction<Service, Operation, DeferredResult<Void>> interceptor;
    }

    private final Map<Class<? extends Service>, Collection<InterceptorData>> serviceInterceptors =
            new HashMap<>();
    private final Map<Class<? extends Service>, Collection<InterceptorData>> factoryServiceInterceptors =
            new HashMap<>();

    public OperationInterceptorRegistry() {
    }

    /**
     * Register a service interceptor for the given service type and http action.
     */
    public void addServiceInterceptor(Class<? extends Service> serviceType, Action action,
            BiFunction<Service, Operation, DeferredResult<Void>> interceptor) {
        InterceptorData data = new InterceptorData();
        data.action = action;
        data.interceptor = interceptor;
        this.serviceInterceptors.computeIfAbsent(serviceType, s -> new ArrayList<>()).add(data);
    }

    /**
     * Register a factory service interceptor for the given instance service type and http action.
     */
    public void addFactoryServiceInterceptor(Class<? extends Service> serviceInstanceType,
            Action action, BiFunction<Service, Operation, DeferredResult<Void>> interceptor) {
        InterceptorData data = new InterceptorData();
        data.action = action;
        data.interceptor = interceptor;
        this.factoryServiceInterceptors.computeIfAbsent(
                serviceInstanceType, s -> new ArrayList<>()).add(data);
    }

    /**
     * Checks whether there are registered interceptors for the given service, and if there are,
     * adds them to the operation processing chain of the service.
     */
    public void subscribeToService(Service service) {
        Collection<InterceptorData> interceptors = checkForInterceptor(service);
        if (interceptors != null) {
            OperationProcessingChain chain = getServiceOperationProcessingChain(service);
            for (InterceptorData data : interceptors) {
                Predicate<Operation> filter = new DeferredOperationPredicate(service, data.action,
                        data.interceptor);
                chain.add(filter);
            }
        }
    }

    private Collection<InterceptorData> checkForInterceptor(Service service) {
        Collection<InterceptorData> data = this.serviceInterceptors.get(service.getClass());
        if (data != null) {
            return data;
        }

        if (service instanceof FactoryService) {
            try {
                Service actualInstance = ((FactoryService) service).createServiceInstance();
                data = this.factoryServiceInterceptors.get(actualInstance.getClass());
            } catch (Throwable e) {
                service.getHost().log(Level.WARNING, "Failure instantiating service instance: %s",
                        Utils.toString(e));
            }
        }
        return data;
    }

    private OperationProcessingChain getServiceOperationProcessingChain(Service service) {
        OperationProcessingChain chain = service.getOperationProcessingChain();
        if (chain == null) {
            chain = new OperationProcessingChain(service);
            service.setOperationProcessingChain(chain);
        }
        return chain;
    }
}
