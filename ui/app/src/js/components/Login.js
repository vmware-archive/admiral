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

import LoginVue from 'LoginVue';
import VueAdapter from 'components/common/VueAdapter';
import EasterEgg from 'components/EasterEgg'; //eslint-disable-line
import LoginPanel from 'components/LoginPanel'; //eslint-disable-line

var LoginVueComponent = Vue.extend({
  template: LoginVue
});

const TAG_NAME = 'login-view';
Vue.component(TAG_NAME, LoginVueComponent);

function Login($el) {
  return new VueAdapter($el, TAG_NAME);
}

export default Login;
