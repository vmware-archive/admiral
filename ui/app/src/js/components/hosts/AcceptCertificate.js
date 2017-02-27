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

import AcceptCertificateVue from 'components/hosts/AcceptCertificateVue.html';

var AcceptCertificate = Vue.extend({
  template: AcceptCertificateVue,

  props: {
    model: {
      required: true,
      type: Object
    },
    address: {
      required: true,
      type: String
    }
  },

  computed: {
    warningMessage: function() {
      return i18n.t('app.host.details.certificateWarning', {
        address: this.address
      });
    }
  },

  data: function() {
    return {
      certificateShown: false
    };
  },

  methods: {
    showCertificate: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      this.certificateShown = true;
    },

    hideCertificate: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      this.certificateShown = false;
    },

    manageCertificates: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      this.$dispatch('manage-certificates');
    }
  }
});

Vue.component('accept-certificate', AcceptCertificate);

export default AcceptCertificate;
