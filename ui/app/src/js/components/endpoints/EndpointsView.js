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

import EndpointsViewVue from 'components/endpoints/EndpointsViewVue.html';
import EndpointEditor from 'components/endpoints/EndpointEditor'; //eslint-disable-line
import { EndpointsActions } from 'actions/Actions';

var EndpointsView = Vue.extend({
  template: EndpointsViewVue,
  props: {
    model: {
      required: true,
      type: Object,
      default: () => {
        return {
          endpoints: {},
          contextView: {}
        };
      }
    }
  },
  data: function() {
    var sortOrders = {
      name: 1,
      endpointType: 1
    };
    return {
      sortKey: '',
      sortOrders: sortOrders
    };
  },
  computed: {
    itemsCount: function() {
      var items = this.model.items;

      return items ? Object.keys(items).length : 0;
    }
  },
  methods: {
    isHighlightedItem: function(item) {
      return this.isNewItem(item) || this.isUpdatedItem(item);
    },
    isNewItem: function(item) {
      return item === this.model.newItem;
    },
    isUpdatedItem: function(item) {
      return item === this.model.updatedItem;
    },
    isEditingItem: function(item) {
      var editingItem = this.model.editingItemData && this.model.editingItemData.item;
      return editingItem && editingItem.documentSelfLink === item.documentSelfLink;
    },
    isEditingNewItem: function() {
      var editingItem = this.model.editingItemData && this.model.editingItemData.item;
      return editingItem && !editingItem.documentSelfLink;
    },
    isEditingOrHighlightedItem: function(item) {
      return this.isEditingItem(item) || this.isHighlightedItem(item);
    },
    addNewItem: function($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      EndpointsActions.editEndpoint({});
    },
    editItem: function(item, $event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      EndpointsActions.editEndpoint(item);
    },
    deleteItem: function(item, $event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      EndpointsActions.deleteEndpoint(item);
    },
    sortBy: function(key) {
      this.sortKey = key;
      this.sortOrders[key] = this.sortOrders[key] * -1;
    },
    refresh: function() {
      EndpointsActions.retrieveEndpoints();
    }
  },
  filters: {
    orderBy: function(items, sortKey, reverse) {
      if (!sortKey) {
        return items;
      }
      var order = reverse && reverse < 0 ? -1 : 1;

      return items.asMutable().sort(function(a, b) {
        a = a[sortKey];
        b = b[sortKey];

        if (!a) {
          a = '';
        }
        if (!b) {
          b = '';
        }
        return a.toLowerCase().localeCompare(b.toLowerCase()) * order;
      });
    }
  },
  attached: function() {
  },
  detached: function() {
  }
});

Vue.component('endpoints-view', EndpointsView);

export default EndpointsView;
