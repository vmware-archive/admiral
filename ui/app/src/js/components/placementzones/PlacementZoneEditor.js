/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import VueDropdownSearch from 'components/common/VueDropdownSearch'; //eslint-disable-line
import VueTags from 'components/common/VueTags'; //eslint-disable-line
import PlacementZoneEditorVue from 'components/placementzones/PlacementZoneEditorVue.html';
import { PlacementZonesActions } from 'actions/Actions';
import constants from 'core/constants';
import services from 'core/services';
import utils from 'core/utils';

var PlacementZoneEditor = Vue.extend({
  template: PlacementZoneEditorVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  computed: {
    showEndpoint: () => {
      return utils.isApplicationCompute();
    },
    isDockerPlacementZone: function() {
      return !this.model.item.placementZoneType
        || this.model.item.placementZoneType === constants.PLACEMENT_ZONE.TYPE.DOCKER;
    }
  },
  data() {
    let placementPolicy = this.model.item.epzState && this.model.item.epzState.placementPolicy;
    return {
      placementPolicy: placementPolicy && placementPolicy !== 'DEFAULT'
          ? placementPolicy : 'RANDOM',
      saveDisabled: !this.model.item.name,
      tags: this.model.item.tags || [],
      tagsToMatch: this.model.item.tagsToMatch || []
    };
  },
  methods: {
    cancel($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      PlacementZonesActions.cancelEditPlacementZone();
    },
    save($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      let item = {
        resourcePoolState: {}
      };

      if (this.model.item.documentSelfLink) {
        item.documentSelfLink = this.model.item.documentSelfLink;
        item.resourcePoolState.documentSelfLink = this.model.item.documentSelfLink;
      }

      if (this.model.item.epzState) {
        item.epzState = {
          documentSelfLink: this.model.item.epzState.documentSelfLink,
          resourcePoolLink: this.model.item.resourcePoolState.documentSelfLink
        };
      }

      item.resourcePoolState.name = this.name;
      item.resourcePoolState.customProperties = {};

      if (this.model.item.placementZoneType) {
        item.resourcePoolState.customProperties.__placementZoneType =
          this.model.item.placementZoneType;
      }

      item.resourcePoolState.customProperties.__resourceType =
        utils.isApplicationCompute()
          // Prelude UI
          ? constants.RESOURCE_TYPES.COMPUTE
          // Admiral UI
          : constants.RESOURCE_TYPES.CONTAINER;

      if (this.endpoint) {
        item.resourcePoolState.customProperties.__endpointLink =
            this.endpoint.documentSelfLink;
      }

      if (this.tagsToMatch && this.tagsToMatch.length) {
        item.epzState = item.epzState || {};
      }

      item.epzState = $.extend(item.epzState || {}, {
        placementPolicy: this.placementPolicy && this.placementPolicy !== 'RANDOM'
            ? this.placementPolicy : 'DEFAULT'
      });

      var tagRequest = utils.createTagAssignmentRequest(item.documentSelfLink,
          this.model.item.tags || [], this.tags);
      if (item.documentSelfLink) {
        PlacementZonesActions.updatePlacementZone(item, tagRequest,
            this.tags, this.isDynamic() ? this.tagsToMatch : []);
      } else {
        PlacementZonesActions.createPlacementZone(item, tagRequest,
            this.isDynamic() ? this.tagsToMatch : []);
      }
    },
    searchEndpoints(...args) {
      return new Promise((resolve, reject) => {
        services.searchEndpoints.apply(null, args).then((result) => {
          result.items.forEach((item) =>
            item.iconSrc = utils.getAdapter(item.endpointType).iconSrc);
          resolve(result);
        }).catch(reject);
      });
    },
    isDynamic() {
      return this.isDockerPlacementZone
        && this.dynamicInput.is(':checked');
    },
    onEndpointChange(endpoint) {
      this.endpoint = endpoint;
      this.saveDisabled = !this.name || !this.endpoint;
    },
    onNameChange() {
      this.name = (this.nameInput.val() || '').trim();
      this.saveDisabled = !this.name || !this.endpoint;
    },
    onPlacementPolicyChange(value) {
      this.placementPolicy = value && value.name;
    },
    onDynamicChange() {
      if (this.isDynamic()) {
        this.tagsToMatchContainer.show();
      } else {
        this.tagsToMatchContainer.hide();
      }
    },
    onTagsChange(tags) {
      this.tags = tags;
    },
    onTagsToMatchChange(tagsToMatch) {
      this.tagsToMatch = tagsToMatch;
    }
  },
  attached() {
    this.name = this.model.item.name;
    this.nameInput = $('.name-input', this.$el);
    if (this.isDockerPlacementZone) {
      this.dynamicInput = $('.dynamic-input', this.$el);
      this.tagsToMatchContainer = $('.tagsToMatch', this.$el);

      if (this.model.item && this.model.item.tagsToMatch && this.model.item.tagsToMatch.length) {
        this.dynamicInput.prop('checked', true);
        this.tagsToMatchContainer.show();
      } else {
        this.dynamicInput.prop('checked', false);
        this.tagsToMatchContainer.hide();
      }
    }

    Vue.nextTick(() => {
      this.nameInput.focus();
    });
  }
});

Vue.component('placement-zone-editor', PlacementZoneEditor);

export default PlacementZoneEditor;
