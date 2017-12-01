/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Level;

import javax.net.ssl.SSLContext;

import io.swagger.models.Contact;
import io.swagger.models.Info;
import io.swagger.models.License;

import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.auth.idm.AuthConfigProvider;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.SessionService;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.SecurityUtils;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.host.interceptor.AuthCredentialsInterceptor;
import com.vmware.admiral.host.interceptor.InUsePlacementZoneInterceptor;
import com.vmware.admiral.host.interceptor.OperationInterceptorRegistry;
import com.vmware.admiral.host.interceptor.ProjectInterceptor;
import com.vmware.admiral.host.interceptor.SchedulerPlacementZoneInterceptor;
import com.vmware.admiral.request.ContainerLoadBalancerBootstrapService;
import com.vmware.admiral.service.common.AuthBootstrapService;
import com.vmware.admiral.service.common.ConfigurationService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionManager;
import com.vmware.admiral.service.common.NodeMigrationService;
import com.vmware.admiral.service.common.harbor.HostInitHarborServices;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.LoaderFactoryService;
import com.vmware.xenon.common.LoaderService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.http.netty.NettyHttpListener;
import com.vmware.xenon.common.http.netty.NettyHttpServiceClient;
import com.vmware.xenon.services.common.MigrationTaskService;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.swagger.SwaggerDescriptorService;

/**
 * Stand alone process entry point for management of infrastructure and applications.
 */
public class ManagementHost extends ServiceHost implements IExtensibilityRegistryHost {

    static {
        if (System.getProperty("service.document.version.retention.limit") == null) {
            System.setProperty("service.document.version.retention.limit", "10");
        }
        if (System.getProperty("service.document.version.retention.floor") == null) {
            System.setProperty("service.document.version.retention.floor", "2");
        }

        SecurityUtils.ensureTlsDisabledAlgorithms();
        SecurityUtils.ensureTrustStoreSettings();
    }

    /**
     * Flag to start a mock adapter instance useful for integration tests
     */
    public boolean startMockHostAdapterInstance;

    /**
     * Users configuration file (full path). Specifying a file automatically enables Xenon's Authx
     * services. Exclusive with authConfig.
     */
    public String localUsers;

    /**
     * External authentication configuration file (full path). Specifying a file automatically
     * enables Xenon's Authx services. Exclusive with localUsers.
     */
    public String authConfig;

    /**
     * Uri for clustering traffic. If set, another listener is started, which must be passed as
     * peerNodes to the clustered nodes, and advertised as publicUri.
     */
    public String nodeGroupPublicUri;

    /**
     * File path to key file (same value as ServiceHost.Arguments)
     */
    public Path keyFile;

    /**
     * Key passphrase (same value as ServiceHost.Arguments)
     */
    public String keyPassphrase;

    /**
     * File path to certificate file (same value as ServiceHost.Arguments)
     */
    public Path certificateFile;

    private ExtensibilitySubscriptionManager extensibilityRegistry;

    private OperationInterceptorRegistry interceptors = new OperationInterceptorRegistry();

    public static void main(String[] args) throws Throwable {
        ManagementHost h = new ManagementHost();
        h.initializeHostAndServices(args);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            h.log(Level.WARNING, "Host stopping ...");
            h.stop();
            h.log(Level.WARNING, "Host is stopped");
        }));
    }

    /**
     * Registers service operation interceptors.
     */
    protected void registerOperationInterceptors() {
        InUsePlacementZoneInterceptor.register(interceptors);
        SchedulerPlacementZoneInterceptor.register(interceptors);
        CompositeComponentInterceptor.register(interceptors);
        AuthCredentialsInterceptor.register(interceptors);
        ProjectInterceptor.register(interceptors);
    }

    protected ManagementHost initializeHostAndServices(String[] args) throws Throwable {
        log(Level.INFO, "Initializing ...");
        initialize(args);

        log(Level.INFO, "Registering service interceptors ...");
        registerOperationInterceptors();

        log(Level.INFO, "Starting ...");
        start();

        log(Level.INFO, "Setting authorization context ...");
        // Set system user's authorization context to allow the services start privileged access.
        setAuthorizationContext(getSystemAuthorizationContext());

        log(Level.INFO, "**** Management host starting ... ****");

        startFabricServices();
        startManagementServices();
        startClosureServices(this, startMockHostAdapterInstance);
        startLoadBalancerServices(this);
        startSwaggerService();

        log(Level.INFO, "**** Management host started. ****");

        log(Level.INFO, "**** Enabling dynamic service loading... ****");

        enableDynamicServiceLoading();

        log(Level.INFO, "**** Dynamic service loading enabled. ****");
        log(Level.INFO, "**** Migration service starting... ****");
        super.startFactory(new MigrationTaskService());
        // Clean up authorization context to avoid privileged access.
        setAuthorizationContext(null);

        return this;
    }

    @Override
    public ServiceHost initialize(String[] args) throws Throwable {
        CommandLineArgumentParser.parse(this, args);
        Arguments baseArgs = new Arguments();
        if (AuthUtil.isAuthxEnabled(this)) {
            baseArgs.isAuthorizationEnabled = true;
        }
        ServiceHost h = super.initialize(args, baseArgs);
        h.setProcessOwner(true);
        validatePeerArgs();

        // if only secure connection is used, make the auth cookie secure
        if (h.getPort() == ServiceHost.PORT_VALUE_LISTENER_DISABLED
                && h.getSecurePort() != ServiceHost.PORT_VALUE_LISTENER_DISABLED) {
            // Default netty listener is created during startup
            // getSecureListener will return null here
            NettyHttpListener listener = new NettyHttpListener(h);
            listener.setSecureAuthCookie(true);
            h.setSecureListener(listener);
        }

        ConfigurationState[] configs = ConfigurationService.getConfigurationProperties();
        ConfigurationUtil.initialize(configs);

        return h;
    }

    protected void startFabricServices() throws Throwable {
        this.log(Level.INFO, "Fabric services starting ...");
        HostInitPhotonModelServiceConfig.startServices(this);

        this.log(Level.INFO, "Fabric services started.");
    }

    /**
     * Start all services related to closures support.
     */
    protected void startClosureServices(ServiceHost host, boolean startMockHostAdapterInstance)
            throws Throwable {
        host.log(Level.INFO, "Closure services starting...");
        HostInitClosureServiceConfig.startServices(host, startMockHostAdapterInstance);
        host.log(Level.INFO, "Closure services started.");
    }

    /**
     * Start all services related to container load balancer support.
     */
    protected void startLoadBalancerServices(ServiceHost host) throws Throwable {
        host.log(Level.INFO, "Container load balancer services starting...");
        HostInitLoadBalancerServiceConfig.startServices(host);
        host.log(Level.INFO, "Container load balancer services started.");
    }

    /**
     * Start all services required to support management of infrastructure and applications.
     */
    protected void startCommonServices() throws Throwable {
        this.log(Level.INFO, "Common service starting ...");

        HostInitCommonServiceConfig.startServices(this);
        HostInitAuthServiceConfig.startServices(this);
        HostInitUpgradeServiceConfig.startServices(this);

        registerForServiceAvailability(AuthBootstrapService.startTask(this), true,
                AuthBootstrapService.FACTORY_LINK);

        this.log(Level.INFO, "Common services started.");
    }

    /**
     * Start all services required to support management of infrastructure and applications.
     */
    protected void startManagementServices() throws Throwable {
        this.log(Level.INFO, "Management service starting ...");

        registerForServiceAvailability(CaSigningCertService.startTask(this), true,
                CaSigningCertService.FACTORY_LINK);
        registerForServiceAvailability(ContainerLoadBalancerBootstrapService.startTask(this), true,
                ContainerLoadBalancerBootstrapService.FACTORY_LINK);

        HostInitComputeServicesConfig.startServices(this, false);
        HostInitComputeBackgroundServicesConfig.startServices(this);
        HostInitRequestServicesConfig.startServices(this);
        HostInitImageServicesConfig.startServices(this);
        HostInitUiServicesConfig.startServices(this);
        HostInitHarborServices.startServices(this, startMockHostAdapterInstance);
        HostInitDockerAdapterServiceConfig.startServices(this, startMockHostAdapterInstance);
        HostInitKubernetesAdapterServiceConfig.startServices(this, startMockHostAdapterInstance);
        HostInitRegistryAdapterServiceConfig.startServices(this);

        this.log(Level.INFO, "Management services started.");
    }

    /**
     * Start Swagger service.
     */
    protected void startSwaggerService() {
        this.log(Level.INFO, "Swagger service starting ...");

        // Serve Swagger 2.0 compatible API description
        SwaggerDescriptorService swagger = new SwaggerDescriptorService();

        // Exclude some core services
        swagger.setExcludedPrefixes(
                "/favicon.ico",
                "/index.html",
                "/index-embedded.html",
                "/inline.",
                "/fontawesome-webfont.",
                "/ng",
                "/ogui",
                "/iaas", // TODO - remove it!
                "/META-INF/",
                "/assets/",
                "/login/assets/",
                "/container-icons/",
                "/container-identicons/",
                "/container-image-icons",
                "/main.",
                "/vendor.",
                "/scripts.",
                "/styles.",
                "/core/");
        swagger.setExcludeUtilities(true);

        // Provide API metainfo
        Info apiInfo = new Info();
        apiInfo.setVersion("1.3.0");
        apiInfo.setTitle("Admiral");

        apiInfo.setLicense(new License().name("Apache 2.0")
                .url("https://github.com/vmware/admiral/blob/master/LICENSE"));
        apiInfo.setContact(new Contact().url("https://github.com/vmware/admiral"));

        swagger.setInfo(apiInfo);

        // Serve swagger on default uri
        this.startService(swagger);
        this.log(Level.INFO, "Swagger service started. Checkout Swagger UI at: %s%s/ui",
                this.getUri(), ServiceUriPaths.SWAGGER);
    }

    private void startExtensibilityRegistry() {
        extensibilityRegistry = new ExtensibilitySubscriptionManager();
        this.startService(extensibilityRegistry);
    }

    /**
     * The directory from which services are dynamically loaded; see
     * {@link #enableDynamicServiceLoading()}
     */
    private static final String DYNAMIC_SERVICES_PATH = System.getProperty(
            ManagementHost.class.getPackage().getName() + ".dynamic_services_path",
            "/etc/xenon/dynamic-services");

    /**
     * Enable Xenon services to be dynamically loaded, by starting the LoaderService. TODO: This
     * code is not required for Admiral, but rather for other components that are implemented as
     * Xenon services, so most of this host startup logic could be extracted out of "admiral" into a
     * separate component that just starts Xenon, and then "admiral" and other components could just
     * instruct Xenon to load their services.
     */
    void enableDynamicServiceLoading() {
        // https://github.com/vmware/xenon/wiki/LoaderService#loader-service-host
        // 1. start the loader service
        startService(
                Operation.createPost(
                        UriUtils.buildUri(
                                this,
                                LoaderFactoryService.class)),
                new LoaderFactoryService());
        // 2. configure service discovery from DYNAMIC_SERVICES_PATH
        LoaderService.LoaderServiceState payload = new LoaderService.LoaderServiceState();
        payload.loaderType = LoaderService.LoaderType.FILESYSTEM;
        payload.path = DYNAMIC_SERVICES_PATH;
        payload.servicePackages = new HashMap<>();
        sendRequest(Operation.createPost(UriUtils.buildUri(this, LoaderFactoryService.class))
                .setBody(payload)
                .setReferer(getUri()));
    }

    private void validatePeerArgs() throws Throwable {
        if (nodeGroupPublicUri != null) {
            URI uri = new URI(nodeGroupPublicUri);

            if (this.getPort() != -1 && uri.getPort() == this.getPort()) {
                throw new IllegalArgumentException("--nodeGroupPublicUri port must be different"
                        + " from --port");
            }

            if (this.getSecurePort() != -1 && uri.getPort() == this.getSecurePort()) {
                throw new IllegalArgumentException("--nodeGroupPublicUri port must be different"
                        + " from --securePort");
            }

            if (uri.getPort() < 0 || uri.getPort() >= Short.MAX_VALUE * 2) {
                throw new IllegalArgumentException("--nodeGroupPublicUri port is not in range");
            }

            if (uri.getScheme() == null) {
                throw new IllegalArgumentException("--nodeGroupPublicUri scheme must be set");
            }

            if (uri.getHost() == null) {
                throw new IllegalArgumentException("--nodeGroupPublicUri host must be set");
            }
        }
    }

    @Override
    public ServiceHost startFactory(Service service) {
        interceptors.subscribeToService(service);
        return super.startFactory(service);
    }

    @Override
    public ServiceHost startService(Operation post, Service service) {
        interceptors.subscribeToService(service);
        return super.startService(post, service);
    }

    @Override
    public ServiceHost start() throws Throwable {
        // Only initialize ServerX509TrustManager
        ServerX509TrustManager trustManager = ServerX509TrustManager.init(this);
        ServiceClient serviceClient = createServiceClient(CertificateUtil.createSSLContext(
                trustManager, null), 0);
        setClient(serviceClient);

        AuthConfigProvider authProvider = AuthUtil.getPreferredProvider(AuthConfigProvider.class);
        // TODO this should be moved to HostInitAuthServiceConfig once HostInitServiceHelper gets
        // support for privileged services
        addPrivilegedService(SessionService.class);
        addPrivilegedService(PrincipalService.class);
        addPrivilegedService(ProjectService.class);
        addPrivilegedService(ProjectFactoryService.class);

        // NodeMigrationService needs to be privileged in order to not get forbidden during the
        // migration process.
        addPrivilegedService(NodeMigrationService.class);

        // RegistryAdapterService needs to be privileged to remove the Xenon auth when getting an
        // authorization token from a repository (Harbor may misbehave with it).
        addPrivilegedService(RegistryAdapterService.class);

        if (AuthUtil.useAuthConfig(this)) {

            Service authService = authProvider.getAuthenticationService();
            addPrivilegedService(authService.getClass());
            setAuthenticationService(authService);

            com.vmware.xenon.common.AuthUtils.registerUserLinkBuilder(
                    authProvider.getAuthenticationServiceSelfLink(),
                    authProvider.getAuthenticationServiceUserLinkBuilder());
        }

        super.start();

        startDefaultCoreServicesSynchronously();

        if (AuthUtil.useAuthConfig(this)) {
            Collection<FactoryService> authServiceFactories = authProvider.createServiceFactories();
            if ((authServiceFactories != null && !authServiceFactories.isEmpty())) {
                startFactoryServicesSynchronously(authServiceFactories.toArray(new Service[] {}));
            }
            Collection<Service> authServices = authProvider.createServices();
            if ((authServices != null && !authServices.isEmpty())) {
                startCoreServicesSynchronously(authServices.toArray(new Service[] {}));
            }

            Collection<Class<? extends Service>> privilegeServices = authProvider
                    .getPrivilegedServices();
            if (privilegeServices != null && !privilegeServices.isEmpty()) {
                // Register privileged services.
                privilegeServices.stream().forEach(service -> this.addPrivilegedService(service));
            }
        }

        startPeerListener();

        log(Level.INFO, "Setting authorization context ...");
        // Set system user's authorization context to allow the services start privileged access.
        setAuthorizationContext(getSystemAuthorizationContext());

        startCommonServices();

        startExtensibilityRegistry();

        // now start ServerX509TrustManager
        trustManager.start();
        setAuthorizationContext(null);
        return this;
    }

    private ServiceClient createServiceClient(SSLContext sslContext,
            int requestPayloadSizeLimit) {
        try {
            // Use the class name and prefix of GIT commit ID as the user agent name and version
            String commitID = (String) getState().codeProperties
                    .get(GIT_COMMIT_SOURCE_PROPERTY_COMMIT_ID);
            if (commitID == null) {
                throw new LocalizableValidationException("CommitID code property not found!",
                        "host.commit.id.not.found");
            }
            commitID = commitID.substring(0, 8);
            String userAgent = ServiceHost.class.getSimpleName() + "/" + commitID;
            ServiceClient serviceClient = NettyHttpServiceClient.create(userAgent,
                    null,
                    getScheduledExecutor(),
                    this);
            if (requestPayloadSizeLimit > 0) {
                serviceClient.setRequestPayloadSizeLimit(requestPayloadSizeLimit);
            }
            serviceClient.setSSLContext(sslContext);

            return serviceClient;
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to create ServiceClient", e);
        }
    }

    private void startPeerListener() throws Throwable {
        if (nodeGroupPublicUri != null) {
            URI uri = new URI(nodeGroupPublicUri);
            NettyHttpListener peerListener = new NettyHttpListener(this);
            if (UriUtils.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                peerListener.setSSLContextFiles(certificateFile.toUri(),
                        keyFile.toUri(), keyPassphrase);
            }
            peerListener.start(uri.getPort(), uri.getHost());
        }
    }

    @Override
    public void setAuthorizationContext(AuthorizationContext context) {
        super.setAuthorizationContext(context);
    }

    @Override
    public AuthorizationContext getSystemAuthorizationContext() {
        return super.getSystemAuthorizationContext();
    }

    @Override
    public ExtensibilitySubscriptionManager getExtensibilityRegistry() {
        return extensibilityRegistry;
    }

    @Override
    public boolean handleRequest(Service service, Operation inboundOp) {

        if (AuthUtil.useAuthConfig(this)) {
            AuthUtils.validateSessionData(inboundOp, getGuestAuthorizationContext());
        }

        return super.handleRequest(service, inboundOp);
    }

}
