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

var VueBigActionButton = Vue.extend({
  template: `<div class="big-action" v-show="supported">
           <a class="btn btn-circle-outline" :title="label" :data-name="name""
              v-on:mousedown="pressed()">
             <i v-if="iconName" :class="'fa fa-' + iconName"></i>
             <img v-if="iconSrc" v-bind:src="iconSrc"/>
             </a>
           <div class="big-action-label">{{label}}</div>
         </div>`,
  props: {
    name: {
      required: true,
      type: String
    },
    iconName: {
      required: true,
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
    spinning: {
      required: false,
      type: Boolean,
      default: false
    },
    confirmable: {
      required: false,
      type: Boolean,
      default: false
    },
    supported: {
      required: false,
      type: Boolean,
      default: true
    }
  },
  methods: {
    pressed: function() {
      if (this.spinning) {
        this.spinStart();
      }

      return this.notifyAction();
    },
    spinStart: function() {
      $(this.$el).find('i').addClass('fa-spin');

      clearTimeout(this.timeoutId);

      this.timeoutId = setTimeout(() => {
        this.spinStop();
      }, 1000);
    },

    spinStop: function() {
      $(this.$el).find('i').removeClass('fa-spin');
    },
    notifyAction: function() {
      if (this.confirmable) {
        this.$dispatch('title-action-confirm', {
          name: this.name,
          title: this.label
        });
      } else {
        this.$dispatch('title-action', this.name);
      }
    }
  }
});

Vue.component('big-action-button', VueBigActionButton);

export default VueBigActionButton;
