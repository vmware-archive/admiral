/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ClassUtils;

import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskFactoryService;

/**
 * The goal of this helper class is to ease the start of family of services (such as
 * {@link com.vmware.photon.controller.model.PhotonModelServices}) and at the same time expose their
 * public links.
 *
 * <p>
 * Impl notes: As of now the approach is to create {@code public static String[] LINKS} var holding
 * services links and {@code public static void startServices(ServiceHost host)} method starting all
 * the services. And the expectation is that the set of links and the set of started services should
 * be the same. In order to enforce that we introduce {@link ServiceMetadata} that is used to
 * calculate the links and start the services, thus the client of the class is focused on just
 * describing its set of services.
 */
public class StartServicesHelper {

    /**
     * Use this method to get the set of service links.
     *
     * @see com.vmware.photon.controller.model.PhotonModelServices#LINKS
     */
    public static String[] getServiceLinks(ServiceMetadata[] servicesMetadata) {
        return Arrays.stream(servicesMetadata)
                .map(ServiceMetadata::getLink)
                .toArray(String[]::new);
    }

    /**
     * Use this method to start the set of services asynchronously.
     *
     * @see com.vmware.photon.controller.model.PhotonModelServices#startServices(ServiceHost)
     */
    public static DeferredResult<Void> startServices(ServiceHost host, ServiceMetadata[]
            servicesMetadata) {

        return startServices(host, tryAddPrivilegedService(host), servicesMetadata);
    }

    /**
     * Use this method to start the set of services asynchronously.
     *
     * @see com.vmware.photon.controller.model.PhotonModelServices#startServices(ServiceHost)
     */
    public static DeferredResult<Void> startServices(
            ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService,
            ServiceMetadata[] servicesMetadata) {

        return DeferredResult
                .allOf(Arrays.stream(servicesMetadata)
                        .map(sm -> sm.start(host, addPrivilegedService))
                        .collect(Collectors.toList()))
                .thenApply(startOps -> (Void) null);
    }

    /**
     * Helper method to execute multiple DeferredResult<List<Operation>>'s and return the combined
     * list of their ops.
     */
    @SafeVarargs
    public static DeferredResult<List<Operation>> allOf(DeferredResult<List<Operation>> ... drs) {
        return allOf(Arrays.asList(drs));
    }

    /**
     * Helper method to execute multiple DeferredResult<List<Operation>>'s and return the combined
     * list of their ops.
     */
    public static DeferredResult<List<Operation>> allOf(List<DeferredResult<List<Operation>>> drs) {
        return DeferredResult
                .allOf(drs)
                .thenApply(opsLists -> opsLists
                        .stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));
    }

    /**
     * Use this method to start the set of services synchronously.
     */
    public static void startServicesSynchronously(
            ServiceHost host,
            ServiceMetadata[] servicesMetadata) {

        startServicesSynchronously(host, tryAddPrivilegedService(host), servicesMetadata);
    }

    /**
     * Use this method to start the set of services synchronously.
     */
    public static void startServicesSynchronously(
            ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService,
            ServiceMetadata[] servicesMetadata) {
        PhotonModelUtils.waitToComplete(startServices(host, addPrivilegedService, servicesMetadata));
    }

    /**
     * The following method is introduced to prevent touching tests to start services as
     * Privileged. It follows 'convention over configuration' principle. Passing any host that has
     * <b>public</b> {@code addPrivilegedService} method (such as {@code VerificationHost}) will
     * automatically use it if {@link ServiceMetadata#requirePrivileged}.
     *
     * <p>
     * In production code the EXPLICIT {@code addPrivilegedService} handler should be passed to
     * start the service.
     */
    private static Consumer<Class<? extends Service>> tryAddPrivilegedService(ServiceHost host) {

        final String addPrivilegedServiceName = "addPrivilegedService";

        Consumer<Class<? extends Service>> addPrivilegedService = null;

        for (Class<?> hostClass : ClassUtils.hierarchy(host.getClass())) {
            try {
                Method addPrivilegedServiceMethod = hostClass.getMethod(
                        addPrivilegedServiceName, Class.class);

                addPrivilegedService = (Class<? extends Service> serviceClass) -> {
                    try {
                        addPrivilegedServiceMethod.invoke(host, serviceClass);

                        host.log(Level.INFO,
                                "Auto starting of '%s' as Privileged by '%s': SUCCESS",
                                serviceClass.getSimpleName(),
                                host.getClass().getSimpleName());

                    } catch (Exception exc) {
                        host.log(Level.INFO,
                                "Auto starting of '%s' as Privileged by '%s': FAILED",
                                serviceClass.getSimpleName(),
                                host.getClass().getSimpleName());
                    }
                };
            } catch (NoSuchMethodException exc) {
                // Continue traversing ServiceHost class hierarchy
            }
        }

        if (addPrivilegedService == null) {
            // since this is used by tests ONLY log with FINE level
            host.log(Level.FINE, "Auto starting of services as Privileged is not supported by '%s'",
                    host.getClass().getSimpleName());
        }

        return addPrivilegedService;
    }

    /**
     * A meta-data describing a service including:
     * <ul>
     * <li>service type (such as service or factory)</li>
     * <li>service class</li>
     * <li>service instantiation logic (applicable for factory services)</li>
     * </ul>
     */
    public static class ServiceMetadata {

        private static final Map<Class<? extends Service>, ServiceMetadata> servicesCache = new ConcurrentHashMap<>();

        private static final Map<Class<? extends Service>, ServiceMetadata> factoryServicesCache = new ConcurrentHashMap<>();

        /**
         * Creates meta-data for a {@link Service}.
         *
         * @return cached ServiceMetadata
         */
        public static ServiceMetadata service(Class<? extends Service> serviceClass) {

            return servicesCache.computeIfAbsent(
                    serviceClass,
                    key -> new ServiceMetadata(false /* isFactory */, serviceClass, null));
        }

        /**
         * Creates meta-data for a {@link Service}.
         *
         * @return cached ServiceMetadata
         */
        public static ServiceMetadata service(Class<? extends Service> serviceClass,
                Supplier<FactoryService> factoryCreator) {

            return servicesCache.computeIfAbsent(
                    serviceClass,
                    key -> new ServiceMetadata(false /* isFactory */, serviceClass, factoryCreator));
        }

        /**
         * Creates meta-data for a {@link FactoryService}.
         *
         * @return cached ServiceMetadata
         */
        public static ServiceMetadata factoryService(Class<? extends Service> serviceClass) {

            return factoryService(serviceClass, null /* factoryCreator */);
        }

        /**
         * Creates meta-data for a {@link FactoryService}.
         *
         * @return cached ServiceMetadata
         */
        public static ServiceMetadata factoryService(
                Class<? extends Service> serviceClass,
                Supplier<FactoryService> factoryCreator) {

            return factoryServicesCache.computeIfAbsent(
                    serviceClass,
                    key -> new ServiceMetadata(true /* isFactory */, serviceClass, factoryCreator));
        }

        /**
         * Creates meta-data for a task service. It is a shortcut to
         * <code>factoryService(serviceClass, () -> TaskFactoryService.create(serviceClass))</code>.
         *
         * @return cached ServiceMetadata
         *
         * @see TaskFactoryService
         */
        public static ServiceMetadata taskService(Class<? extends Service> serviceClass) {

            return factoryService(serviceClass, () -> TaskFactoryService.create(serviceClass));
        }

        /**
         * Indicates whether this meta-data represents specialized {@link FactoryService}.
         */
        public final boolean isFactory;

        public final Class<? extends Service> serviceClass;

        /**
         * Optional FactoryService creator applicable for factory service ({@link #isFactory} ==
         * true).
         */
        public final Supplier<FactoryService> factoryCreator;

        private final String link;

        /**
         * Extra properties specific/attached to this service metadata. Used for extensibility
         * purpose.
         */
        public final Map<String, Object> extension = new HashMap<>();

        /**
         * A flag indicating that this service require to be started as privileged. It is up to the
         * starter [host] whether to grant system privilege or not.
         *
         * <p>
         * Default value is {@code false}.
         */
        public boolean requirePrivileged = false;

        private ServiceMetadata(
                boolean isFactory,
                Class<? extends Service> serviceClass,
                Supplier<FactoryService> factoryCreator) {

            this.isFactory = isFactory;
            this.serviceClass = serviceClass;
            this.factoryCreator = factoryCreator;

            // Calculate and cache the link
            this.link = initLink();
        }

        @Override
        public String toString() {
            return "ServiceMetadata["
                    + (this.isFactory ? "factory" : "service") + ":"
                    + this.serviceClass.getSimpleName() + "]";
        }

        /**
         * Indicates this service require to be started as privileged or not.
         */
        public ServiceMetadata requirePrivileged(boolean requirePrivileged) {

            this.requirePrivileged = requirePrivileged;

            return this;
        }

        /**
         * Get (through reflection by analogy with UriUtils.buildFactoryUri) the SELF_LINK of a
         * service or the FACTORY_LINK of a factory service.
         */
        public String getLink() {
            return this.link;
        }

        /**
         * There's no guarantee that's the actual Xenon service being run. That's just a Java
         * instance of this service class most commonly used to call {@link Service#getStateType()}.
         */
        public Service serviceInstance() {
            // Every call MUST return new service instance cause the same service
            // might be run on two hosts
            try {
                if (this.factoryCreator != null) {
                    FactoryService factoryService = this.factoryCreator.get();
                    return factoryService.createServiceInstance();
                } else {
                    return this.serviceClass.newInstance();
                }
            } catch (Throwable thr) {
                throw new InstantiationError("Failed to create an instance of "
                        + this.serviceClass
                        + ". Details: "
                        + Utils.toString(thr));
            }
        }

        /**
         * Returns the instance service of the factory, or {@code null} if this is not a factory.
         */
        public FactoryService factoryServiceInstance() {
            if (!this.isFactory) {
                return null;
            }

            if (this.factoryCreator == null) {
                return FactoryService.create(this.serviceClass);
            } else {
                return this.factoryCreator.get();
            }
        }

        /**
         * Get (through reflection by analogy with UriUtils.buildFactoryUri) the SELF_LINK of a
         * service or the FACTORY_LINK of a factory service.
         */
        private String initLink() throws IllegalAccessError {

            String selfLinkOrFactoryLinkName = this.isFactory
                    ? UriUtils.FIELD_NAME_FACTORY_LINK
                    : UriUtils.FIELD_NAME_SELF_LINK;

            try {
                Field selfLinkOrFactoryLink = this.serviceClass
                        .getDeclaredField(selfLinkOrFactoryLinkName);

                if (selfLinkOrFactoryLink != null) {
                    selfLinkOrFactoryLink.setAccessible(true);

                    return selfLinkOrFactoryLink.get(null).toString();
                }
            } catch (Exception e) {
            }

            throw new IllegalAccessError(String.format(
                    "'%s' service does not have public static '%s' field",
                    this.serviceClass,
                    selfLinkOrFactoryLinkName));
        }

        /**
         * Start the service asynchronously considering its type.
         */
        private DeferredResult<Operation> start(
                ServiceHost serviceHost,
                Consumer<Class<? extends Service>> addPrivilegedService) {

            if (addPrivilegedService != null && this.requirePrivileged) {
                addPrivilegedService.accept(this.serviceClass);
            }

            if (this.isFactory) {
                return startService(serviceHost, factoryServiceInstance());
            } else {
                return startService(serviceHost, serviceInstance());
            }
        }

        private DeferredResult<Operation> startService(ServiceHost serviceHost,
                FactoryService factoryService) {
            URI factoryUri = UriUtils.buildFactoryUri(serviceHost, this.serviceClass);
            Operation post = Operation.createPost(UriUtils.buildUri(serviceHost,
                    factoryUri.getPath()));
            return startService(serviceHost, post, factoryService);
        }

        private DeferredResult<Operation> startService(ServiceHost serviceHost, Service service) {
            URI u = null;
            if (ReflectionUtils.hasField(service.getClass(), UriUtils.FIELD_NAME_SELF_LINK)) {
                u = UriUtils.buildUri(serviceHost, service.getClass());
            } else if (service instanceof FactoryService) {
                try {
                    u = UriUtils.buildFactoryUri(serviceHost,
                            ((FactoryService) service).createServiceInstance().getClass());
                } catch (Throwable t) {
                    return DeferredResult.failed(t);
                }
            } else {
                return DeferredResult.failed(new IllegalStateException(String.format(
                        "Field SELF_LINK or FACTORY_LINK is required in service class %s",
                        service.getClass().getSimpleName())));
            }
            Operation startPost = Operation.createPost(u);
            return startService(serviceHost, startPost, service);
        }

        private DeferredResult<Operation> startService(ServiceHost serviceHost,
                Operation startPost, Service service) {
            DeferredResult<Operation> dr = new DeferredResult<>();
            startPost.setCompletion((o, e) -> {
                if (e != null) {
                    serviceHost.log(Level.SEVERE, "Service %s failed start: %s", o.getUri(), e);
                    dr.fail(e);
                    return;
                }

                serviceHost.log(Level.FINE, "Started %s", o.getUri().getPath());
                dr.complete(o);
            });

            serviceHost.log(Level.FINE, "Starting %s", startPost.getUri());
            try {
                serviceHost.startService(startPost, service);
            } catch (Throwable t) {
                serviceHost.log(Level.SEVERE, "ServiceHost.startService failed for service %s: "
                                + "%s", startPost.getUri(), Utils.toString(t));
                dr.fail(t);
            }
            return dr;
        }
    }
}
