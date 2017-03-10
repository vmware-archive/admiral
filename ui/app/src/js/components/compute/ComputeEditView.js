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

import { ComputeActions, ComputeContextToolbarActions } from 'actions/Actions';
import VueTags from 'components/common/VueTags'; //eslint-disable-line
import ComputeEditViewVue from 'components/compute/ComputeEditViewVue.html';
import utils from 'core/utils';

export default Vue.component('compute-edit-view', {
  template: ComputeEditViewVue,
  props: {
    model: {
      required: true
    }
  },
  data() {
    return {
      tags: this.model.item.tags || []
    };
  },
  computed: {
    validationErrors() {
      return this.model.validationErrors || {};
    },
    activeContextItem() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded() {
      return this.model.contextView && this.model.contextView.expanded;
    }
  },
  methods: {
    onPlacementZoneChange(placementZone) {
      this.placementZone = placementZone;
    },
    onTagsChange(tags) {
      this.tags = tags;
    },
    saveCompute() {
      let model = {
        dto: this.model.item.dto,
        resourcePoolLink: this.placementZone ? this.placementZone.documentSelfLink : null,
        selfLinkId: this.model.item.selfLinkId
      };
      var tagRequest = utils.createTagAssignmentRequest(this.model.item.documentSelfLink,
          this.model.item.tags, this.tags);
      ComputeActions.updateCompute(model, tagRequest);
    },
    openToolbarPlacementZones: ComputeContextToolbarActions.openToolbarPlacementZones,
    closeToolbar: ComputeContextToolbarActions.closeToolbar,
    createPlacementZone: ComputeContextToolbarActions.createPlacementZone,
    managePlacementZones: ComputeContextToolbarActions.managePlacementZones
  }
});
