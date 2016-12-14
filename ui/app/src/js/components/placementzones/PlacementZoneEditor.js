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

import DropdownSearch from 'components/common/VueDropdownSearch'; //eslint-disable-line
import PlacementZoneEditorVue from 'components/placementzones/PlacementZoneEditorVue.html';
import Tags from 'components/common/Tags';
import { PlacementZonesActions } from 'actions/Actions';
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
    showEndpoint: function() {
      return utils.isApplicationCompute();
    }
  },
  data: function() {
    return {
      saveDisabled: !this.model.item.name
    };
  },
  methods: {
    cancel: function($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      PlacementZonesActions.cancelEditPlacementZone();
    },
    save: function($event) {
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

      if (this.endpoint) {
        item.resourcePoolState.customProperties.__endpointLink =
            this.endpoint.documentSelfLink;
      }

      let tags = this.tags.getValue().reduce((prev, curr) => {
        if (!prev.find((tag) => tag.key === curr.key && tag.value === curr.value)) {
          prev.push(curr);
        }
        return prev;
      }, []);

      if (item.documentSelfLink) {
        PlacementZonesActions.updatePlacementZone(item, tags);
      } else {
        PlacementZonesActions.createPlacementZone(item, tags);
      }
    },
    searchEndpoints: function(...args) {
      return new Promise((resolve, reject) => {
        services.searchEndpoints.apply(null, args).then((result) => {
          result.items.forEach((item) =>
            item.iconSrc = `image-assets/endpoints/${item.endpointType}.png`);
          resolve(result);
        }).catch(reject);
      });
    },
    onEndpointChange: function(endpoint) {
      this.endpoint = endpoint;
    },
    onNameChange: function() {
      this.name = (this.nameInput.val() || '').trim();
      this.saveDisabled = !this.name;
    },
    onDynamicChange: function() {
      if (this.dynamicInput.is(':checked')) {
        this.tagsContainer.show();
      } else {
        this.tagsContainer.hide();
      }
      this.tags.setValue([]);
    }
  },
  attached: function() {
    this.name = this.model.item.name;
    this.nameInput = $('.name-input', this.$el);
    this.dynamicInput = $('.dynamic-input', this.$el);
    this.tagsInput = $('.tags-input', this.$el);
    this.tagsContainer = $('.tags', this.$el);

    this.tags = new Tags(this.tagsInput);

    if (this.model.item && this.model.item.tags && this.model.item.tags.length) {
      this.dynamicInput.prop('checked', true);
      this.tagsContainer.show();
      this.tags.setValue(this.model.item.tags);
    } else {
      this.dynamicInput.prop('checked', false);
      this.tagsContainer.hide();
      this.tags.setValue([]);
    }

    Vue.nextTick(() => {
      this.nameInput.focus();
    });
  }
});

Vue.component('placement-zone-editor', PlacementZoneEditor);

export default PlacementZoneEditor;
