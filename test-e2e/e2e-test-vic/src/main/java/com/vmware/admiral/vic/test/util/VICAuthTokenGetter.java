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

package com.vmware.admiral.vic.test.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.codec.binary.Base64;
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
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.vmware.admiral.test.util.HttpUtils;

public class VICAuthTokenGetter {

    public static String getVICAuthToken(String target, String username, String password) {
        try {
            CookieStore cookies = new BasicCookieStore();
            HttpClient client = HttpUtils.createUnsecureHttpClient(cookies, null);
            HttpUriRequest get = new HttpGet(target);
            HttpResponse response = client.execute(get);
            Header header = response.getFirstHeader("Location");
            if (Objects.isNull(header)) {
                throw new RuntimeException("Not a valid VIC instance url");
            }
            String location = header.getValue();
            EntityUtils.consume(response.getEntity());
            String upa = "Basic "
                    + Base64.encodeBase64String((username + ":" + password).getBytes());
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("CastleAuthorization", upa));
            HttpPost post = new HttpPost(location);
            post.setEntity(new UrlEncodedFormEntity(params));
            response = client.execute(post);
            String responseHtml = EntityUtils.toString(response.getEntity());
            Document doc = Jsoup.parse(responseHtml, "UTF-8");
            EntityUtils.consume(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException(
                        "Invalid credentials, or target is not a valid VIC instance url\nResponse was: "
                                + responseHtml);
            }
            Element samlPostForm = doc.getElementById("SamlPostForm");
            String postTarget = samlPostForm.attr("action");
            String samlResponse = doc.getElementsByAttributeValue("name", "SAMLResponse").get(0)
                    .attr("value");
            params = new ArrayList<>();
            params.add(new BasicNameValuePair("SAMLResponse", samlResponse));
            params.add(new BasicNameValuePair("RelayState", "SessionId"));
            post = new HttpPost(postTarget);
            post.setEntity(new UrlEncodedFormEntity(params));
            response = client.execute(post);
            EntityUtils.consume(response.getEntity());
            for (Cookie c : cookies.getCookies()) {
                if (c.getName().equals("xenon-auth-cookie")) {
                    return c.getValue();
                }
            }
            throw new RuntimeException("Could not get session cookie");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
