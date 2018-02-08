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

package com.vmware.admiral.vic.test.ui.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.vmware.admiral.test.util.AuthContext;
import com.vmware.admiral.test.util.HttpUtils;

public class IdentitySourceConfigurator {

    private final String TARGET;
    private final String USERNAME;
    private final String PASSWORD;
    private static final String ADD_AD_ENDPOINT = "/psc/mutation/add?propertyObjectType=com.vmware.vsphere.client.sso.admin.model.IdentitySourceLdapSpec";
    HttpClient client;

    public IdentitySourceConfigurator(AuthContext vcenterAuthContext) {
        if (!vcenterAuthContext.getTarget().startsWith("https://")) {
            this.TARGET = "https://" + vcenterAuthContext.getTarget();
        } else {
            this.TARGET = vcenterAuthContext.getTarget();
        }
        this.USERNAME = vcenterAuthContext.getUsername();
        this.PASSWORD = vcenterAuthContext.getPassword();
    }

    public void addIdentitySource(String specBody) {
        if (Objects.isNull(client)) {
            client = authenticateClient(TARGET, USERNAME, PASSWORD);
        }
        try {
            StringEntity entity = new StringEntity(specBody);
            HttpPost post = new HttpPost(TARGET + ADD_AD_ENDPOINT);
            post.setEntity(entity);
            HttpResponse response = client.execute(post);
            EntityUtils.consume(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Could not add identity source, response code was: "
                        + response.getStatusLine().getStatusCode());
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not add identity source: ", e);
        }
    }

    private HttpClient authenticateClient(String target, String username,
            String password) {
        String loginTarget = target + "/psc/login";
        CookieStore cookies = new BasicCookieStore();
        HttpClient client = HttpUtils.createUnsecureHttpClient(cookies, null);
        HttpUriRequest get = new HttpGet(loginTarget);
        try {
            HttpResponse response = client.execute(get);
            String location = response.getFirstHeader("Location").getValue();
            EntityUtils.consume(response.getEntity());
            get = new HttpGet(loginTarget);
            response = client.execute(get);
            EntityUtils.consume(response.getEntity());
            String upa = "Basic "
                    + Base64.encodeBase64String((username + ":" + password).getBytes());
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("CastleAuthorization", upa));
            HttpPost post = new HttpPost(location);
            post.setEntity(new UrlEncodedFormEntity(params));
            response = client.execute(post);
            Document doc = Jsoup
                    .parse(IOUtils.toString(response.getEntity().getContent(), "UTF-8"));
            EntityUtils.consume(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException(
                        "Authenticating to the PSC failed due to invalid credentials");
            }

            Element samlPostForm = doc.getElementById("SamlPostForm");
            String postTarget = samlPostForm.attr("action");
            Elements samlElements = doc.getElementsByAttributeValue("name", "SAMLResponse");
            if (samlElements.size() == 0) {
                throw new RuntimeException(
                        "Authenticating to the PSC failed, probably the login sequence has changed");
            }
            String samlResponse = samlElements.get(0).attr("value");

            params = new ArrayList<>();
            params.add(new BasicNameValuePair("SAMLResponse", samlResponse));
            post = new HttpPost(postTarget);
            post.setEntity(new UrlEncodedFormEntity(params));
            response = client.execute(post);
            EntityUtils.consume(response.getEntity());

            if (response.getStatusLine().getStatusCode() != 302) {
                throw new RuntimeException(
                        "Authenticating to the PSC failed, probably the login sequence has changed");
            }
            get = new HttpGet(target + "/psc/");
            response = client.execute(get);
            EntityUtils.consume(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException(
                        "Authenticating to the PSC failed, probably the login sequence has changed");
            }
            String token = null;
            for (Cookie c : cookies.getCookies()) {
                if (c.getName().equals("XSRF-TOKEN")) {
                    token = c.getValue();
                    break;
                }
            }
            if (!Objects.isNull(token)) {
                // PSC 6.5
                List<Header> headers = new ArrayList<>();
                headers.add(new BasicHeader("X-XSRF-TOKEN", token));
                return HttpUtils.createUnsecureHttpClient(cookies, headers);
            } else {
                return client;
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not get auth headers", e);
        }
    }

}
