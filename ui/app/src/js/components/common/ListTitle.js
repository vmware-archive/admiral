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

import ListTitleVue from 'ListTitleVue';

var ListTitleVueComponent = Vue.extend({
  template: ListTitleVue,
  props: {
    hasSearchQuery: {
      required: false,
      type: Boolean,
      default: false
    },
    count: {
      required: false,
      type: Number,
      default: -1
    },
    title: {
      required: true,
      type: String
    },
    titleSearch: {
      required: false,
      type: String
    }
  },
  data: function() {
    return {
      actionName: null,
      actionTitle: null
    };
  },
  methods: {
    notifyRefresh: function() {
      this.$dispatch('refresh-list');
    }
  },
  events: {
    'title-action': function(actionName) {

      this.$dispatch('do-action', actionName);
    },
    'title-action-confirm': function(actionData) {
      this.actionName = actionData.name;
      this.actionTitle = actionData.title;
    },
    'action-confirmed': function(actionName) {

      this.$dispatch('do-action', actionName);

      this.actionName = null;
      this.actionTitle = null;
    },
    'action-cancelled': function() {
      this.actionName = null;
      this.actionTitle = null;
    }
  }
});

Vue.component('list-title', ListTitleVueComponent);

export default ListTitleVueComponent;
