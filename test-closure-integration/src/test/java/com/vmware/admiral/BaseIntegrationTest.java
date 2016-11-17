/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 */

package com.vmware.admiral;

import static junit.framework.TestCase.assertNotNull;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;

import org.junit.AfterClass;
import org.junit.Assert;

import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.closures.util.ClosureUtils;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescriptionFactoryService;
import com.vmware.admiral.closures.services.images.DockerImage;
import com.vmware.admiral.closures.services.images.DockerImageFactoryService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TestLogger;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * Base of integration tests.
 */
public class BaseIntegrationTest {

    public static final int DOCKER_IMAGE_BUILD_TIMEOUT_SECONDS = 30 * 60;
    protected static String IMAGE_NAME_PREFIX = "vmware/photon-closure-runner_";
    protected static String BASE_IMAGE_NAME_PREFIX = "rageorgiev/photon-closure-runner_";

    protected static final String TEST_INTEGRATION_PROPERTIES_FILE_PATH = "integration-test.properties";
    protected static final String TEST_CLOSURE_URL_PROP_NAME = "test.closure.url";
    protected static final String TEST_WEB_SERVER_URL_PROP_NAME = "test.webserver.url";
    protected static final String TEST_DOCKER_HOST_PROP_NAME = "docker.host.address";
    protected static final String TEST_DOCKER_PORT_PROP_NAME = "docker.host.port.API";
    protected static final String TEST_DOCKER_REGISTRY_HOST = "registry.host.address";

    public static final String DOCKER_COMPUTE_ID = "test-docker-host-compute-id";

    private static final Properties testProperties = loadTestProperties();

    protected static TestLogger logger;

    protected static final Queue<ServiceDocument> documentsForDeletion = new LinkedBlockingQueue<>();

    public static final long DEFAULT_DOCUMENT_EXPIRATION_MICROS = Long.getLong(
            "dcp.document.test.expiration.time.seconds", TimeUnit.MINUTES.toMicros(30));

    protected static ComputeState dockerHostCompute;
    private static AuthCredentialsService.AuthCredentialsServiceState dockerHostAuthCredentials;

    private static SslTrustCertificateService.SslTrustCertificateState dockerHostSslTrust;

    protected static final Queue<ServiceDocument> documentsForDeletionAfterClass = new LinkedBlockingQueue<>();

    protected BaseIntegrationTest() {
        logger = new TestLogger(getClass());
    }

    protected static String getRegistryHostAddress() {
        return testProperties.getProperty(TEST_DOCKER_REGISTRY_HOST);
    }

    protected static String getServiceHostUrl() {
        return testProperties.getProperty(TEST_CLOSURE_URL_PROP_NAME);
    }

    protected static String getTestWebServerUrl() {
        return testProperties.getProperty(TEST_WEB_SERVER_URL_PROP_NAME);
    }

    protected static String getDockerHostUrl() {
        // if a dynamic port is used, build the URL from the host and port parts
        String port = testProperties.getProperty(TEST_DOCKER_PORT_PROP_NAME);

        if (port != null) {
            String host = testProperties.getProperty(TEST_DOCKER_HOST_PROP_NAME, "127.0.0.1");
            String baseUrl = String.format("tcp://%s:%s", host, port);
            return baseUrl;
        }

        return testProperties.getProperty(TEST_CLOSURE_URL_PROP_NAME);
    }

    @AfterClass
    public static void baseAfterClass() throws Exception {
        try {
            while (!documentsForDeletionAfterClass.isEmpty()) {
                delete(documentsForDeletionAfterClass.poll());
            }
        } catch (Exception e) {
            documentsForDeletionAfterClass.clear();
            e.printStackTrace();
        }
    }

    protected static void cleanUpAfterClass(ServiceDocument document) {
        documentsForDeletionAfterClass.add(document);
    }

    private static ComputeState createDockerComputeHost() {
        ComputeState cs = new ComputeState();
        cs.id = DOCKER_COMPUTE_ID;
        cs.documentSelfLink = cs.id;
        cs.primaryMAC = UUID.randomUUID().toString();
        cs.address = "ssh://somehost:22"; // this will be used for ssh to access the host
        cs.powerState = ComputeService.PowerState.ON;
        cs.resourcePoolLink = GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK;
        cs.adapterManagementReference = URI.create("http://localhost:8081"); // not real reference
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());

        addDefaultDocumentTimeout(cs);
        return cs;
    }

    protected static String waitForBuildCompletion(String serviceHostUri, String imagePrefix, ClosureDescription
            closureDescription,
            long timeout)
            throws
            Exception {
        String imageName = prepareImageName(imagePrefix, closureDescription);
        System.out.println(
                "Build for docker execution image: " + imageName + " on host: " + dockerHostCompute
                        .documentSelfLink);
        String dockerBuildImageLink = createImageBuildRequestUri(imageName, dockerHostCompute.documentSelfLink);
        long startTime = System.currentTimeMillis();
        while (!isImageReady(serviceHostUri, dockerBuildImageLink) && !isTimeoutElapsed(startTime,
                DOCKER_IMAGE_BUILD_TIMEOUT_SECONDS)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Docker image " + imageName + " build on host: " + dockerHostCompute.documentSelfLink);

        Thread.sleep(closureDescription.resources.timeoutSeconds.intValue() * 1000 + timeout);

        return dockerBuildImageLink;
    }

    protected static void waitForTaskState(String taskLink, String serviceHostUri, TaskState.TaskStage state)
            throws Exception {
        Closure fetchedClosure = getClosure(taskLink, serviceHostUri);
        long startTime = System.currentTimeMillis();
        while (state != fetchedClosure.state && !isTimeoutElapsed(startTime,
                60)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean isTimeoutElapsed(long startTime, int timeout) {
        return System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(timeout);

    }

    private static boolean isImageReady(String serviceHostUri, String dockerBuildImageLink) throws
            Exception {
        if (logger != null) {
            logger.info("Checking docker build image request for image: {} ", dockerBuildImageLink);
        }

        System.out.println("Waiting for docker image build: " + serviceHostUri + dockerBuildImageLink);

        SimpleHttpsClient.HttpResponse imageRequestResponse = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod
                .GET, serviceHostUri + dockerBuildImageLink, null);

        if (imageRequestResponse == null || imageRequestResponse.responseBody == null) {
            return false;
        }

        DockerImage imageRequest = Utils.fromJson(imageRequestResponse.responseBody, DockerImage.class);
        assertNotNull(imageRequest);

        System.out.println("Waiting for docker image build. State: " + imageRequest.taskInfo.stage);
        if (TaskState.isFailed(imageRequest.taskInfo) || TaskState.isCancelled(imageRequest.taskInfo)) {
            throw new Exception("Unable to build docker execution image: " + dockerBuildImageLink);
        }

        return TaskState.isFinished(imageRequest.taskInfo);

    }

    protected static String createImageBuildRequestUri(String imageName, String computeStateLink) {
        String imageBuildRequestId = ClosureUtils.calculateHash(new String[] { imageName, "/", computeStateLink });

        return UriUtils.buildUriPath(DockerImageFactoryService.FACTORY_LINK, imageBuildRequestId);
    }

    private static String prepareImageName(String imagePrefix, ClosureDescription taskDef) {
        return imagePrefix + ":" + prepareImageTag(taskDef);
    }

    private static String prepareImageTag(ClosureDescription closureDescription) {
        if (ClosureUtils.isEmpty(closureDescription.sourceURL)) {
            if (ClosureUtils.isEmpty(closureDescription.dependencies)) {
                // no dependencies
                return "latest";
            }

            return ClosureUtils.calculateHash(
                    new String[] { closureDescription.dependencies });
        } else {
            return ClosureUtils.calculateHash(
                    new String[] { closureDescription.sourceURL });
        }

    }

    protected static void cleanResourceUri(String resUri) throws Exception {
        System.out.println("Sending DELETE of: " + resUri);
        SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.DELETE, resUri);
    }

    protected ClosureDescription createClosureDescription(String taskDefPayload, String serviceHostUri)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException {
        SimpleHttpsClient.HttpResponse taskDefResponse = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod
                .POST, serviceHostUri + ClosureDescriptionFactoryService.FACTORY_LINK, taskDefPayload);
        assertNotNull(taskDefResponse);
        return Utils.fromJson(taskDefResponse.responseBody, ClosureDescription.class);
    }

    protected Closure createClosure(ClosureDescription closureDescription, String serviceHostUri)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException {
        Closure closureState = new Closure();
        closureState.descriptionLink = closureDescription.documentSelfLink;
        SimpleHttpsClient.HttpResponse taskResponse = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod
                .POST, serviceHostUri + ClosureFactoryService.FACTORY_LINK, Utils.toJson(closureState));
        assertNotNull(taskResponse);

        return Utils.fromJson(taskResponse.responseBody, Closure.class);
    }

    protected void executeTask(Closure createdClosure, Closure closureRequest, String serviceHostUri)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException {
        SimpleHttpsClient.HttpResponse taskExecResponse = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod
                .POST, serviceHostUri + createdClosure.documentSelfLink, Utils.toJson(closureRequest));
        assertNotNull(taskExecResponse);
    }

    protected static Closure getClosure(String taskLink, String serviceHostUri)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException {
        SimpleHttpsClient.HttpResponse taskResponse = SimpleHttpsClient
                .execute(SimpleHttpsClient.HttpMethod.GET, serviceHostUri + taskLink);
        assertNotNull(taskResponse);

        return Utils.fromJson(taskResponse.responseBody, Closure.class);
    }

    protected void verifyJsonArrayBooleans(Object[] javaArray, JsonArray jsArray) {
        assertEquals(javaArray.length, jsArray.size());
        for (int i = 0; i < javaArray.length; i++) {
            assertEquals(javaArray[i], jsArray.get(i).getAsBoolean());
        }
    }

    protected void verifyJsonArrayStrings(Object[] javaArray, JsonArray jsArray) {
        assertEquals(javaArray.length, jsArray.size());
        for (int i = 0; i < javaArray.length; i++) {
            assertEquals(javaArray[i], jsArray.get(i).getAsString());
        }
    }

    protected void verifyJsonArrayInts(Object[] javaArray, JsonArray jsArray) {
        assertEquals(javaArray.length, jsArray.size());
        for (int i = 0; i < javaArray.length; i++) {
            assertEquals(javaArray[i], jsArray.get(i).getAsInt());
        }
    }

    protected static void setUpDockerHostAuthentication() throws Exception {
        System.out.println("********************************************************************");
        System.out.println("----------  Setup: Add CoreOS VM as DockerHost ComputeState --------");
        System.out.println("********************************************************************");
        System.out.println("---------- 1. Create a Docker Host Container Description. --------");

        System.out.println(
                "---------- 2. Setup auth credentials for the CoreOS VM (Container Host). --------");
        dockerHostAuthCredentials = new AuthCredentialsService.AuthCredentialsServiceState();
        dockerHostAuthCredentials.type = AuthCredentialsType.PublicKey.name();

        dockerHostAuthCredentials.privateKey = CommonTestStateFactory.getFileContent(getTestRequiredProp("docker"
                + ".client.key.file"));
        dockerHostAuthCredentials.publicKey = CommonTestStateFactory.getFileContent(getTestRequiredProp("docker"
                + ".client.cert.file"));

        dockerHostAuthCredentials = postDocument(AuthCredentialsService.FACTORY_LINK, dockerHostAuthCredentials);

        Assert.assertNotNull("Failed to create host credentials", dockerHostAuthCredentials);

        System.out.println("---------- 3. Create Docker Host ComputeState for CoreOS VM. --------");
        dockerHostCompute = createDockerComputeHost();
        dockerHostCompute.address = getTestRequiredProp("docker.host.address");
        dockerHostCompute.customProperties.put(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME,
                getTestRequiredProp("docker.host.port." + ContainerHostService.DockerAdapterType.API));

        dockerHostCompute.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.toString());

        // link credentials to host
        dockerHostCompute.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                dockerHostAuthCredentials.documentSelfLink);
        dockerHostCompute = addHost(dockerHostCompute);

        System.out.println("---------- 4. Add the Docker Host SSL Trust Certificate. --------");
        dockerHostSslTrust = CommonTestStateFactory
                .createSslTrustCertificateState(getTestRequiredProp("docker.host.ssl.trust.file"),
                        CommonTestStateFactory.REGISTRATION_DOCKER_ID);

        dockerHostSslTrust.resourceLink = dockerHostCompute.documentSelfLink;
        postDocument(SslTrustCertificateService.FACTORY_LINK, dockerHostSslTrust);

    }

    private static Properties loadTestProperties() {
        Properties properties = new Properties();
        try {
            properties.load(BaseIntegrationTest.class.getClassLoader()
                    .getResourceAsStream(TEST_INTEGRATION_PROPERTIES_FILE_PATH));

            String baseDcpUrl = System.getProperty(TEST_CLOSURE_URL_PROP_NAME);
            if (baseDcpUrl != null) {
                properties.put(TEST_CLOSURE_URL_PROP_NAME, baseDcpUrl);
            }
            String dockerHost = System.getProperty(TEST_DOCKER_HOST_PROP_NAME);
            if (dockerHost != null) {
                properties.put(TEST_DOCKER_HOST_PROP_NAME, dockerHost);
            }
            String dockerPort = System.getProperty(TEST_DOCKER_PORT_PROP_NAME);
            if (dockerPort != null) {
                properties.put(TEST_DOCKER_PORT_PROP_NAME, dockerPort);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during test properties loading.", e);
        }
        return properties;
    }

    protected static ComputeState addHost(ComputeState computeState) throws Exception {
        if (computeState.id != null) {
            String documentSelfLink = buildServiceUri(ComputeService.FACTORY_LINK,
                    computeState.id);
            String body = sendRequest(SimpleHttpsClient.HttpMethod.GET, documentSelfLink, null);
            if (body != null && !body.isEmpty()) {
                delete(documentSelfLink);
            }
        }

        ContainerHostService.ContainerHostSpec hostSpec = new ContainerHostService.ContainerHostSpec();
        hostSpec.hostState = computeState;
        hostSpec.acceptCertificate = true;

        SimpleHttpsClient.HttpResponse httpResponse = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.PUT,
                getBaseUrl() + buildServiceUri(ContainerHostService.SELF_LINK),
                Utils.toJson(hostSpec));

        if (HttpURLConnection.HTTP_NO_CONTENT != httpResponse.statusCode) {
            throw new IllegalArgumentException("Add host failed with status code: "
                    + httpResponse.statusCode);
        }

        List<String> headers = httpResponse.headers.get(Operation.LOCATION_HEADER);
        String computeStateLink = headers.get(0);

        ComputeState computeStateDoc = getDocument(computeStateLink, ComputeState.class);
        cleanUpAfterClass(computeStateDoc);
        return computeStateDoc;
    }

    protected static <T extends ServiceDocument> T getDocument(String seflLink,
            Class<? extends T> type) throws Exception {
        String body = sendRequest(SimpleHttpsClient.HttpMethod.GET, seflLink, null);
        if (body == null || body.isEmpty()) {
            return null;
        }
        return Utils.fromJson(body, type);
    }

    protected static String getTestRequiredProp(String key) {
        String property = testProperties.getProperty(key);
        if (property == null || property.isEmpty()) {
            throw new IllegalStateException(String.format("Property '%s' is required", key));
        }
        return property;
    }

    protected static <T extends ServiceDocument> T postDocument(String fabricLink, T document)
            throws Exception {
        return postDocument(fabricLink, document, TestDocumentLifeCycle.FOR_DELETE);
    }

    public enum TestDocumentLifeCycle {
        FOR_DELETE, FOR_DELETE_AFTER_CLASS, NO_DELETE
    }

    @SuppressWarnings("unchecked")
    protected static <T extends ServiceDocument> T postDocument(String fabricLink, T document,
            TestDocumentLifeCycle documentLifeCycle) throws Exception {
        if (document.documentSelfLink != null && !document.documentSelfLink.isEmpty()) {
            String servicePathUrl = buildServiceUri(fabricLink, extractId(document.documentSelfLink));
            String body = sendRequest(SimpleHttpsClient.HttpMethod.GET, servicePathUrl, null);
            if (body != null && !body.isEmpty()) {
                delete(servicePathUrl);
            }
        }
        String body = sendRequest(SimpleHttpsClient.HttpMethod.POST, fabricLink, Utils.toJson(document));
        if (body == null) {
            return null;
        }
        T doc = (T) Utils.fromJson(body, document.getClass());
        switch (documentLifeCycle) {
        case FOR_DELETE:
            cleanUpAfter(doc);
            break;
        default:
            break;
        }
        return doc;
    }

    protected static String sendRequest(SimpleHttpsClient.HttpMethod method, String link, String body)
            throws Exception {
        SimpleHttpsClient.HttpResponse httpResponse = SimpleHttpsClient.execute(method, getBaseUrl() +
                buildServiceUri(link), body);
        return httpResponse.responseBody;
    }

    protected static String buildServiceUri(String... paths) {
        StringBuilder url = new StringBuilder();
        for (String path : paths) {
            url.append(UriUtils.normalizeUriPath(path));
        }
        return url.toString();
    }

    protected static String extractId(String link) {
        AssertUtil.assertNotNull(link, "link");
        if (link.endsWith(UriUtils.URI_PATH_CHAR)) {
            link = link.substring(0, link.length() - 1);
        }
        return link.substring(link.lastIndexOf(UriUtils.URI_PATH_CHAR) + 1);
    }

    protected static void delete(String documentSelfLink) throws Exception {
        sendRequest(SimpleHttpsClient.HttpMethod.DELETE, documentSelfLink, Utils.toJson(new ServiceDocument()));
    }

    protected static void delete(ServiceDocument document) throws Exception {
        delete(document.documentSelfLink);
    }

    protected static void cleanUpAfter(ServiceDocument document) {
        documentsForDeletion.add(document);
    }

    protected static String getBaseUrl() {
        // if a dynamic port is used, build the URL from the host and port parts
        if (testProperties.getProperty(TEST_CLOSURE_URL_PROP_NAME).isEmpty()) {

            String port = testProperties.getProperty(TEST_DOCKER_PORT_PROP_NAME);

            if (port != null) {
                String host = testProperties.getProperty(TEST_DOCKER_HOST_PROP_NAME, "127.0.0.1");
                String baseUrl = String.format("http://%s:%s", host, port);
                return baseUrl;
            }

        }
        return testProperties.getProperty(TEST_CLOSURE_URL_PROP_NAME);
    }

    public static void addDefaultDocumentTimeout(ServiceDocument serviceDocument) {
        serviceDocument.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                + DEFAULT_DOCUMENT_EXPIRATION_MICROS;
    }

}
