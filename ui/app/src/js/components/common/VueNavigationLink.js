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

var VueNavigationLink = Vue.extend({
  template: `<span class="navigation-link-holder" v-show="show" title="{{tooltip}}">
      <a class="navigation-link" href="{{link}}"><img v-if="iconName" class="nav-item-image"
          v-bind:src="iconName"> {{label}}</a>&nbsp;</span>`,
  props: {
    link: {
      required: false,
      type: String,
      default: '#'
    },
    label: {
      required: false
    },
    iconName: {
      required: false,
      type: String
    },
    show: {
      required: false,
      type: Boolean,
      default: true
    },
    tooltip: {
      required: false,
      type: String,
      default: null
    }
  }
});

Vue.component('navigation-link', VueNavigationLink);

export default VueNavigationLink;
