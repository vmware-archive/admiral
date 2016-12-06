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
import PlacementZonesListVue from 'components/placementzones/PlacementZonesListVue.html';
import utils from 'core/utils';
import { PlacementZonesContextToolbarActions } from 'actions/Actions';
import EndpointsView from 'components/endpoints/EndpointsView'; //eslint-disable-line

var PlacementZonesList = Vue.extend({
  template: PlacementZonesListVue,
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

    var list = InlineEditableListFactory.createPlacementZonesList($listHolder);

    this.unwatchModel = this.$watch('model', (model) => {
      list.setData(model);
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  },
  methods: {
    openToolbarEndpoints: PlacementZonesContextToolbarActions.openToolbarEndpoints,
    closeToolbar: PlacementZonesContextToolbarActions.closeToolbar
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

Vue.component('placement-zones-list', PlacementZonesList);

export default PlacementZonesList;
