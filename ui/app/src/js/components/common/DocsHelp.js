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

import DocsHelpVue from 'components/common/DocsHelpVue.html';
import docs from 'core/docs';
import modal from 'core/modal';

var DocsHelp = Vue.extend({
  data: function() {
    return {
      helpUrl: '',
      qrUrl: null
    };
  },
  template: DocsHelpVue,
  attached: function() {

    docs.getHelpUrlAndImage((data) => {
      this.helpUrl = data.helpUrl;
      this.qrUrl = data.qrUrl;
    });
  }
});

Vue.component('docs-help', DocsHelp);

var docsHelp = {
  open: function() {
    var $holder = $('<div class="modal fade" tabindex="-1" ' +
                    'role="dialog"><docs-help></docs-help></div>');
    modal.show($holder);
    this.vue = new Vue({
      el: $holder[0]
    });

    var _this = this;

    $holder.on('click', '.btn', function(e) {
      if ($(this).hasClass('closeHelp')) {
        e.preventDefault();
      }
      modal.hide();
      _this.vue.$destroy(true);
      _this.vue = null;
    });
  }
};

export default docsHelp;
