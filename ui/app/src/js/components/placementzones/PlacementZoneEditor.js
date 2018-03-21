/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import VueDropdownSearch from 'components/common/VueDropdownSearch'; //eslint-disable-line
import PlacementZoneEditorVue from 'components/placementzones/PlacementZoneEditorVue.html';
import { PlacementZonesActions } from 'actions/Actions';
import constants from 'core/constants';
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
      tagsData: '',
      tagsToMatchData: ''
    };
  },

  attached() {
    this.name = this.model.item.name;
    this.nameInput = $('.name-input', this.$el);

    this.tagsData = utils.processTagsForDisplay(this.model.item.tags || []);
    this.tagsToMatchData = utils.processTagsForDisplay(this.model.item.tagsToMatch || []);

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
        constants.RESOURCE_TYPES.CONTAINER;

      if (this.tagsToMatch && this.tagsToMatch.length) {
        item.epzState = item.epzState || {};
      }

      item.epzState = $.extend(item.epzState || {}, {
        placementPolicy: this.placementPolicy && this.placementPolicy !== 'RANDOM'
            ? this.placementPolicy : 'DEFAULT'
      });

      let tags = utils.processTagsForSave(this.tagsData);
      let tagsToMatch = this.isDynamic() ? utils.processTagsForSave(this.tagsToMatchData) : [];

      var tagRequest = utils.createTagAssignmentRequest(item.documentSelfLink,
                                                          this.model.item.tags || [], tags);
      if (item.documentSelfLink) {
        // Update
        PlacementZonesActions.updatePlacementZone(item, tagRequest, tags, tagsToMatch);
      } else {
        // Create
        PlacementZonesActions.createPlacementZone(item, tagRequest, tagsToMatch);
      }
    },

    isDynamic() {
      return this.isDockerPlacementZone && this.dynamicInput.is(':checked');
    },

    onNameChange() {
      this.name = (this.nameInput.val() || '').trim();
      this.saveDisabled = !this.name;
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
    }
  }
});

Vue.component('placement-zone-editor', PlacementZoneEditor);

export default PlacementZoneEditor;
