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

package com.vmware.admiral.auth.lightwave.pc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Generates the X509 certificate to be used to identify the client
 * when accessing LightWave service.
 */
public class X509CertificateHelper {

    public X509Certificate generateX509Certificate()
            throws NoSuchAlgorithmException, CertificateException, OperatorCreationException {

        return generateX509Certificate("RSA", "SHA1withRSA");
    }

    public X509Certificate generateX509Certificate(String keyPairAlg, String sigAlg)
            throws NoSuchAlgorithmException, CertificateException, OperatorCreationException {

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(keyPairAlg);
        keyGen.initialize(1024, new SecureRandom());

        KeyPair clientKeyPair = keyGen.generateKeyPair();

        String solutionUser = "oidc.client" + UUID.randomUUID().toString();

        return generateCertificate(clientKeyPair, solutionUser, sigAlg);
    }

    public String x509CertificateToBase64(X509Certificate x509Certificate)
            throws CertificateEncodingException {
        Base64 base64 = new Base64();

        return new String(base64.encode(x509Certificate.getEncoded()));
    }

    public X509Certificate getX509CertificateFromBase64(String base64Cert)
            throws CertificateException {
        byte[] sslTrustBytes = base64Cert.getBytes();
        InputStream is = new ByteArrayInputStream(Base64.decodeBase64(sslTrustBytes));

        CertificateFactory cf = CertificateFactory.getInstance("X509");

        return (X509Certificate) cf.generateCertificate(is);
    }

    private X509Certificate generateCertificate(KeyPair keyPair, String dn, String sigAlg)
            throws OperatorCreationException, CertificateException {
        ContentSigner sigGen = new JcaContentSignerBuilder(sigAlg).build(keyPair.getPrivate());

        Date startDate = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        Date endDate = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000);

        X509v3CertificateBuilder v3CertGen = new JcaX509v3CertificateBuilder(
                new X500Name("CN=" + dn),
                new BigInteger(64, new SecureRandom()), startDate, endDate,
                new X500Name("CN=" + dn), keyPair.getPublic());

        X509CertificateHolder certHolder = v3CertGen.build(sigGen);
        X509Certificate x509Certificate = new JcaX509CertificateConverter()
                .getCertificate(certHolder);

        return x509Certificate;
    }

}
