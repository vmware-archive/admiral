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

import java.net.URI;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test AuthClientHandler.
 */
@RunWith(Enclosed.class)
public class AuthClientHandlerTest {

    public static class NotParameterizedPart {
        @Test
        public void testReplaceIdTokenWithPlaceholderSuccess() throws Exception {

            String logoutURL = "https://10.146.39.99/openidconnect/logout/esxcloud?id_token_hint=eyJhbGciOiJSUzI1NiJ9.eyJzaWQ"
                    + "iOiJhLVhxc2phS1NZYTdYQmVuRG55anlpRkFJUGNVbS1xMXJvUkszcG02TDdzIiwic3ViIjoiYWRtaW5pc3RyYXRvckBlc3hjbG91ZCIsIml"
                    + "zcyI6Imh0dHBzOlwvXC8xMC4xNDYuMzkuOTlcL29wZW5pZGNvbm5lY3RcL2VzeGNsb3VkIiwiZ2l2ZW5fbmFtZSI6IkFkbWluaXN0cmF0b3I"
                    + "iLCJpYXQiOjE0MzgzNzAwMzAsImV4cCI6MTQzODM4NDQzMCwidG9rZW5fY2xhc3MiOiJpZF90b2tlbiIsInRlbmFudCI6ImVzeGNsb3VkIiw"
                    + "ibm9uY2UiOiIxIiwiYXVkIjoiYjM3MWU5ZDAtMGNiYi00MzM5LWE2MjQtZWNiZTI4MjcwNThmIiwiZmFtaWx5X25hbWUiOiJlc3hjbG91ZCI"
                    + "sImp0aSI6IjhycVAwUzYybEFjS2dsV0VYNmhLa29kNmVrTl93ellfRFk5RzdhWlFrbnMiLCJ0b2tlbl90eXBlIjoiQmVhcmVyIn0.MowzDrk"
                    + "7DPEv9T_a6F2xJFBwNljYnr7QSX5PjDYJ2pneRlhVELsRcI7Cqg0g4TSPKxfgFqg8KCVYTOm0gmGVt-K6zaxaTs3BkvbVOdEjLJY4RVGtEzG"
                    + "PHZ4oLHcpWH-VKdZ_WGfnmTQ_8VlDj5aEwKClEDHIW4QG7Mai7WSdZwANhQrJ_T_ZpVQRKM7LffaHcPeTBgMZi5gWl6mAzGrY_5e4bLkw9FS"
                    + "gJQeKSNSYeZ-c437yYvU1dmgzx2A2yR5fmbxnI3eAkNSWB9U5ZujHUntfp4sOcKNTnQKWJVbkRcZloj3cR1l_vw4MUGonD8Rt41MZBIUue5u"
                    + "QRctg5rT2HaGVY7kL0dZpmp-9g6Q_SnTsr4oJ3tIMey19VjISx44FUzMHoEvJQgyI-E4BwcIrjoeoPaVchT1Qdbi-Zh5zKK9jGgoPqOjNeSv"
                    + "sR5XVT7Xy857aXL8OFpZ8r4HSoZXT68vnfpqT_eQFdy59Sl6o-xG7_-OU1OUoiYB5OVP8ZqShajZ9kICD7m1SG3QYUnxqlW6I2JsMOsbVOPp"
                    + "BRjPyHnf8k0CcV9ChjtIHHBHjXnh9woszu34_HCkie2n1pALG7AyEIOOdbAv33_rPcGfZJJhwycr4xXd-n6DYGtPDRgHmzKWDveFsLvXoV4t"
                    + "9vW5HgKLFldBrLMPQgWUbpXzWWWs&post_logout_redirect_uri=https%3A%2F%2F10.118.97.239%2Fapi%2Flogin-redirect.htm"
                    + "l&state=E&correlation_id=EMhk6IVFwXs-wUrn90iYHA1aCULgP6sSMfomPcrw8xk";

            String logoutURLWithPlaceholder = "https://10.146.39.99/openidconnect/logout/esxcloud?id_token_hint=[ID_TOKEN_PLA"
                    + "CEHOLDER]&post_logout_redirect_uri=https%3A%2F%2F10.118.97.239%2Fapi%2Flogin-redirect.html&state=E&correlati"
                    + "on_id=EMhk6IVFwXs-wUrn90iYHA1aCULgP6sSMfomPcrw8xk";

            Assert.assertEquals(new URI(logoutURLWithPlaceholder),
                    AuthClientHandler.replaceIdTokenWithPlaceholder(new URI(logoutURL)));
        }
    }

    @RunWith(Parameterized.class)
    public static class TheParameterizedPart {

        @Parameters
        public static Object[][] data() {
            return new Object[][] {
                    { "" },
                    { "https://10.146.39.99/openidconnect/logout/esxcloud?" },
                    { "https://10.146.39.99/openidconnect/logout/esxcloud?&post_logout_redirect_uri=https%3A%2F%2F10.118.97.239%2F"
                            + "api%2Flogin-redirect.htm" },
            };
        }

        private String logoutURL;

        public TheParameterizedPart(String logoutURL) {
            this.logoutURL = logoutURL;
        }

        @Test(expected = IllegalArgumentException.class)
        public void testReplaceIdTokenWithPlaceholderWithInvalidLogoutUrl() throws Exception {
            AuthClientHandler.replaceIdTokenWithPlaceholder(new URI(logoutURL));
        }
    }
}
