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

var VueActionConfirmation = Vue.extend({
  template: `<div v-if="actionName" class="action-confirmation" transition="fade-in">
      <a href="#" class="admiral-btn warn action-confirm btn-{{actionName}}"
        v-on:click="confirmAction($event)"><span>{{actionTitle}}</span></a>
      <a href="#" class="action-cancel"
        v-on:click="cancelAction($event)"><span>{{i18n('cancel')}}</span></a>
    </div>`,

  props: {
    actionName: {
      required: false,
      type: String
    },
    actionTitle: {
      required: false,
      type: String
    }
  },
  methods: {
    confirmAction: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      this.$dispatch('action-confirmed', this.actionName);
      this.show = false;
    },
    cancelAction: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      this.$dispatch('action-cancelled', this.actionName);
      this.show = false;
    }
  }
});

Vue.component('action-confirmation', VueActionConfirmation);

export default VueActionConfirmation;

