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

import DocsHelpVue from 'components/common/DocsHelpVue.html';
import docs from 'core/docs';

var DocsHelp = Vue.extend({
  template: DocsHelpVue,

  data: function() {
    return {
      helpUrl: '',
      qrUrl: null
    };
  },

  attached: function() {
    docs.getHelpUrlAndImage((data) => {
      this.helpUrl = data.helpUrl;
      this.qrUrl = data.qrUrl;
    });
  },

  methods: {
    openHelpInNewTab: function($event) {
      $event.preventDefault();

      var helpWindow = window.open(this.helpUrl, '_blank');
      helpWindow.focus();
    },
    closeHelp: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      this.$dispatch('close-help');
    }
  }
});

Vue.component('docs-help', DocsHelp);

export default DocsHelp;
