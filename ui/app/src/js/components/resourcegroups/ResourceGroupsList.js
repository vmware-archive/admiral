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

import InlineEditableListFactory from 'components/common/InlineEditableListFactory';

var ResourceGroupsList = Vue.extend({
  template: `<div><div v-else class="list-holder"></div></div>`,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  attached: function() {
    var $listHolder = $(this.$el).find('.list-holder');

    var list = InlineEditableListFactory.createGroupsList($listHolder);

    this.unwatchModel = this.$watch('model', (model) => {
      list.setData(model);
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  }
});

Vue.component('resource-groups-list', ResourceGroupsList);

export default ResourceGroupsList;
