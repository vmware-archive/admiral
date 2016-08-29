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
import ResourcePoolsListVue from 'ResourcePoolsListVue';
import utils from 'core/utils';
import { ResourcePoolsContextToolbarActions } from 'actions/Actions';
import EndpointsView from 'components/endpoints/EndpointsView'; //eslint-disable-line

var ResourcePoolsList = Vue.extend({
  template: ResourcePoolsListVue,
  data: function() {
    return {
      showContextPanel: utils.isApplicationCompute()
    };
  },
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  attached: function() {
    var $listHolder = $(this.$el).find('.list-holder');

    var list = InlineEditableListFactory.createResourcePoolsList($listHolder);

    this.unwatchModel = this.$watch('model', (model) => {
      list.setData(model);
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  },
  methods: {
    openToolbarEndpoints: ResourcePoolsContextToolbarActions.openToolbarEndpoints,
    closeToolbar: ResourcePoolsContextToolbarActions.closeToolbar
  },
  computed: {
    activeContextItem: function() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded: function() {
      return this.model.contextView && this.model.contextView.expanded;
    }
  }
});

Vue.component('resource-pools-list', ResourcePoolsList);

export default ResourcePoolsList;
