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

var VueActionButton = Vue.extend({
  template: `
    <div class="action" v-show="supported">
      <a href="#" :disabled="disabled" :title="tooltip"
        :class="'btn btn-circle-outline container-action-' + name">
        <i :class="'fa fa-' + iconName"></i>
      </a>
      <div v-if="label" class="action-label">{{label}}</div>
    </div>`,
  props: {
    name: {
      required: true,
      type: String
    },
    label: {
      required: false,
      type: String
    },
    iconName: {
      required: true,
      type: String
    },
    supported: {
      required: false,
      type: Boolean,
      default: true
    },
    disabled: {
      required: false,
      type: Boolean,
      default: false
    },
    tooltip: {
      required: false,
      type: String,
      default: null
    }
  }
});

Vue.component('action-button', VueActionButton);

export default VueActionButton;
