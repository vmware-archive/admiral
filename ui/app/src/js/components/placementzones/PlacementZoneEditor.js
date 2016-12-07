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

import PlacementZoneEditorVue from 'components/placementzones/PlacementZoneEditorVue.html';
import DropdownSearchMenu from 'components/common/DropdownSearchMenu';
import Tags from 'components/common/Tags';
import { PlacementZonesActions } from 'actions/Actions';
import services from 'core/services';
import utils from 'core/utils';

const INITIAL_FILTER = '';
const HOST_RESULT_LIMIT = 10;

function endpointRenderer(endpoint) {
  return `
    <div>
      <img src="image-assets/endpoints/${endpoint.endpointType}.png">${endpoint.name}
    </div>`;
}

function endpointSearchCallback(q, callback) {
  services.searchEndpoints(q || INITIAL_FILTER, HOST_RESULT_LIMIT).then((result) => {
    result.items = result.items.map((host) => {
      host.name = utils.getHostName(host);
      return host;
    });
    callback(result);
  });
}

function toggleChanged() {
  this.$dispatch('change', this.destinationInput.getSelectedOption());
}

var PlacementZoneEditor = Vue.extend({
  template: PlacementZoneEditorVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  components: {
    endpointSearch: {
      template: '<div></div>',
      props: {

      },
      attached: function() {
        this.destinationInput = new DropdownSearchMenu($(this.$el), {
          title: i18n.t('dropdownSearchMenu.title', {
            entity: i18n.t('app.endpoint.entity')
          }),
          searchPlaceholder: i18n.t('app.placementZone.edit.endpointPlaceholder')
        });
        this.destinationInput.setOptionsRenderer(endpointRenderer);
        this.destinationInput.setOptionSelectCallback(() => toggleChanged.call(this));
        this.destinationInput.setClearOptionSelectCallback(() => toggleChanged.call(this));
        this.destinationInput.setFilterCallback(endpointSearchCallback.bind(this));
        this.destinationInput.setFilter(INITIAL_FILTER);
      }
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
