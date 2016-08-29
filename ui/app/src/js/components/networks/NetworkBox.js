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

const TEMPLATE = `<div class="network">
                    <div class="network-details">
                      <img class="network-icon" src="image-assets/network-and-security.png"/>
                      <div class="network-label">{{model.name}}</div>
                      <div class="network-actions">
                        <a class="btn item-edit hide"><i class="btn fa fa-pencil"></i></a>
                        <a class="btn item-delete"v-on:click="onDelete($event)">
                          <i class="fa fa-times"></i>
                        </a>
                      </div>
                    </div>
                    <div class="network-anchor-line" v-bind:class="{'shrink': !attached}"></div>
                  </div>`;

var NetworkBox = Vue.extend({
  template: TEMPLATE,
  props: {
    model: {required: true}
  },
  data: function() {
    return {
      attached: false
    };
  },
  attached: function() {
    this.attached = true;
    this.$dispatch('attached', this);
  },
  detached: function() {
    this.$dispatch('detached', this);
  },
  methods: {
    onDelete: function(e) {
      e.preventDefault();
      e.stopImmediatePropagation();
      this.$dispatch('remove', this);
    }
  }
});

Vue.component('network-box', NetworkBox);

export default NetworkBox;
