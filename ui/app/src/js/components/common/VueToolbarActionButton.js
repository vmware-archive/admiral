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

var VueToolbarActionButton = Vue.extend({
  template: `<div class="toolbar-action" v-show="supported">
           <a class="btn btn-circle-outline"
              data-toggle="tooltip" data-placement="top" :title="tooltip"
              :data-name="id">
             <i v-if="iconName" :class="'fa fa-' + iconName"></i>
             <img v-if="iconSrc" v-bind:src="iconSrc"/>
          </a>
           <div class="toolbar-action-label">{{label}}</div>
         </div>`,
  props: {
    id: {
      required: false,
      type: String
    },
    iconName: {
      required: false,
      type: String
    },
    iconSrc: {
      required: false,
      type: String
    },
    label: {
      required: true,
      type: String
    },
    supported: {
      required: false,
      type: Boolean,
      default: true
    },
    tooltip: {
      required: false,
      type: String,
      default: null
    }
  },
  attached: function() {
    if (this.tooltip) {
      $(this.$el).find('a').tooltip({html: true});
    }
  }
});

Vue.component('toolbar-action-button', VueToolbarActionButton);

export default VueToolbarActionButton;
