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

import ContainerShellVue from 'components/containers/ContainerShellVue.html';

var ContainerShell = Vue.extend({
  template: ContainerShellVue,

  props: {
    showShell: {
      required: true,
      type: Boolean
    },
    shellUrl: {
      required: false,
      type: String
    },
    newTabEnabled: {
      required: false,
      type: Boolean
    }
  },

  methods: {
    openShellInNewTab: function() {
      let $iframe = $(this.$el).next('.modal').find('iframe');

      let iframeWindow = $iframe[0].contentWindow;
      // TODO: once we have the shellinabox served from the same domain as our app,
      // we could re-use the session like so
      let sessionId = iframeWindow.shellinabox && iframeWindow.shellinabox.session;
      let url = $iframe.attr('src');
      if (sessionId) {
        url += '#' + sessionId;
      }
      let newWindow = window.open(url);
      if (newWindow) {
        this.$dispatch('close-shell-modal');
      } else {
        // Probably browser is blocking a popup
      }
    },

    closeShellModal: function() {
      this.$dispatch('close-shell-modal');
    }
  }
});

Vue.component('container-shell', ContainerShell);

export default ContainerShell;
