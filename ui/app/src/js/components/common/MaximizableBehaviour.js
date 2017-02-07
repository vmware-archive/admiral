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

var MaximizableBehaviour = Vue.extend({
  template: `
    <div v-bind:class="{'maximized' : maximized}" class="maximizableControl">
      <slot></slot>
      <a class="maximizableControlToggle">
        <i :class="'fa fa-' + maximized ? 'compress' : 'expand'"></i>
      </a>
    </div>
  `,

  data: function() {
    return {
      maximized: false
    };
  },

  attached: function() {
    $(this.$el).on('click', '.maximizableControlToggle', (e) => {
      e.preventDefault();
      this.maximized = !this.maximized;
    });
  }
});

Vue.component('maximizable-behaviour', MaximizableBehaviour);

export default MaximizableBehaviour;
