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

package com.vmware.admiral.tiller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.HttpClientAware;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import okhttp3.OkHttpClient;

import org.microbean.helm.Tiller;
import org.microbean.kubernetes.Pods;

import com.vmware.admiral.tiller.client.TillerConfig;
import com.vmware.admiral.tiller.client.TillerConfig.TillerConnectionType;

/**
 * Can create both plain-text and TLS-secured {@link Tiller} instances. Client credentials are
 * supported in TLS-mode.
 */
public class TillerFactory {

    public static Tiller newTiller(TillerConfig tillerConfig)
            throws MalformedURLException, SSLException {
        Config k8sConfig = buildK8sConfig(tillerConfig);
        DefaultKubernetesClient k8sClient = new DefaultKubernetesClient(k8sConfig);
        return newTiller(k8sClient, tillerConfig);
    }

    private static <T extends HttpClientAware & KubernetesClient> Tiller newTiller(T client,
            TillerConfig tillerConfig) throws MalformedURLException, SSLException {
        Objects.requireNonNull(client);

        String tillerNamespace = Tiller.DEFAULT_NAMESPACE;
        int tillerPort = Tiller.DEFAULT_PORT;
        Map<String, String> tillerLabels = Tiller.DEFAULT_LABELS;

        if (tillerConfig != null
                && tillerConfig.getTillerNamespace() != null
                && !tillerConfig.getTillerNamespace().isEmpty()) {
            tillerNamespace = tillerConfig.getTillerNamespace();
        }
        if (tillerConfig != null
                && tillerConfig.getTillerPort() != null
                && tillerConfig.getTillerPort() > 0) {
            tillerPort = tillerConfig.getTillerPort();
        }
        if (tillerConfig != null
                && tillerConfig.getTillerLabels() != null
                && !tillerConfig.getTillerLabels().isEmpty()) {
            tillerLabels = tillerConfig.getTillerLabels();
        }

        LocalPortForward portForward = buildPortForward(client, tillerNamespace, tillerPort,
                tillerLabels);
        SslContext sslContext = buildSslContext(tillerConfig);
        ManagedChannel managedChannel = buildChannel(portForward, sslContext);

        return new Tiller(managedChannel) {

            @Override
            public void close() throws IOException {
                super.close();
                if (client != null) {
                    client.close();
                }
            }

        };
    }

    static Config buildK8sConfig(TillerConfig config) {
        if (config == null) {
            return null;
        }

        return new ConfigBuilder()
                .withMasterUrl(config.getK8sApiUrl())
                .withTrustCerts(config.getK8sTrustCertificateAuthority())
                .withCaCertData(config.getK8sCertificateAuthority())
                .withClientCertData(config.getK8sClientCertificate())
                .withClientKeyData(config.getK8sClientKey())
                .withClientKeyPassphrase(config.getK8sClientKeyPassphrase())
                .build();
    }

    private static SslContext buildSslContext(TillerConfig tillerConfig) throws SSLException {
        if (tillerConfig == null
                || tillerConfig.getTillerConnectionType() == TillerConnectionType.PLAIN_TEXT) {
            return null;
        }

        SslContextBuilder contextBuilder = GrpcSslContexts.forClient();
        if (tillerConfig.getTillerConnectionType() == TillerConnectionType.TLS_VERIFY) {
            contextBuilder.trustManager(
                    stringToStream(tillerConfig.getTillerCertificateAuthority()));
        } else {
            contextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
        }

        contextBuilder.keyManager(
                stringToStream(tillerConfig.getTillerClientCertificate()),
                stringToStream(tillerConfig.getTillerClientKey()),
                tillerConfig.getTillerClientKeyPassphrase());

        return contextBuilder.build();
    }

    static InputStream stringToStream(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return stringToStream(s, StandardCharsets.UTF_8);
    }

    private static InputStream stringToStream(String s, Charset charset) {
        return new ByteArrayInputStream(s.getBytes(charset));
    }

    private static <T extends HttpClientAware & KubernetesClient> LocalPortForward buildPortForward(
            T client, String namespaceHousingTiller, int tillerPort,
            Map<String, String> tillerLabels) throws MalformedURLException {

        final OkHttpClient httpClient = client.getHttpClient();
        if (httpClient == null) {
            throw new IllegalArgumentException("client",
                    new IllegalStateException("client.getHttpClient() == null"));
        }
        LocalPortForward portForward = Pods.forwardPort(httpClient,
                client.pods().inNamespace(namespaceHousingTiller).withLabels(tillerLabels),
                tillerPort);
        if (portForward == null) {
            throw new TillerFactoryException("Could not forward port to a Ready Tiller pod's port "
                    + tillerPort + " in namespace " + namespaceHousingTiller + " with labels "
                    + tillerLabels);
        }
        return portForward;
    }

    private static ManagedChannel buildChannel(LocalPortForward portForward,
            SslContext sslContext) {
        Objects.requireNonNull(portForward);
        final InetAddress localAddress = portForward.getLocalAddress();
        if (localAddress == null) {
            throw new IllegalArgumentException("portForward",
                    new IllegalStateException("portForward.getLocalAddress() == null"));
        }
        final String hostAddress = localAddress.getHostAddress();
        if (hostAddress == null) {
            throw new IllegalArgumentException("portForward", new IllegalStateException(
                    "portForward.getLocalAddress().getHostAddress() == null"));
        }

        NettyChannelBuilder channelBuilder = NettyChannelBuilder
                .forAddress(hostAddress, portForward.getLocalPort())
                .idleTimeout(5L, TimeUnit.SECONDS)
                .keepAliveTime(30L, TimeUnit.SECONDS)
                .maxInboundMessageSize(Tiller.MAX_MESSAGE_SIZE);

        if (sslContext != null) {
            channelBuilder.useTransportSecurity()
                    .sslContext(sslContext);
        } else {
            // default to plain text connection if there is no SSL configuration
            channelBuilder.usePlaintext(true);
        }

        return channelBuilder.build();
    }

}
