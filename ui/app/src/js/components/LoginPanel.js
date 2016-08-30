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

import LoginPanelVue from 'LoginPanelVue';
import services from 'core/services';

const SUCCESSFUL_REDIRECT_TIMEOUT = 1000;

var LoginPanel = Vue.extend({
  template: LoginPanelVue,

  computed: {
    loginDisabled: function() {
      return !!this.loading || !this.username || !this.password;
    }
  },

  data: function() {
    return {
      loading: false,
      loginSuccess: false,
      loginError: false,
      username: '',
      password: ''
    };
  },

  methods: {
    login: function() {
      if (this.loginDisabled) {
        return;
      }

      this.loading = true;
      this.loginSuccess = false;
      this.loginError = false;

      services.login(this.username, this.password).then(() => {
        this.loading = false;
        this.loginSuccess = true;
        setTimeout(() => {
          window.location.assign('/');
        }, SUCCESSFUL_REDIRECT_TIMEOUT);
      }, (e) => {
        this.loading = false;
        this.loginError = true;
        console.log(e);
      });
    },

    changeUNPW: function() {
      this.username = $(this.$el).find('#username').val();
      this.password = $(this.$el).find('#password').val();
    }
  }
});

Vue.component('login-panel', LoginPanel);

export default LoginPanel;
