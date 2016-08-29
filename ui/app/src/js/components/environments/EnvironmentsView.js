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

import EnvironmentsViewVue from 'EnvironmentsViewVue';
import EnvironmentEditor from 'components/environments/EnvironmentEditor'; //eslint-disable-line
import { EnvironmentsActions } from 'actions/Actions';

var propertiesToString = function(properties) {
  return properties ? Object.keys(properties).join(', ') : '';
};

var EnvironmentsView = Vue.extend({
  template: EnvironmentsViewVue,
  props: {
    model: {
      required: true,
      type: Object,
      default: () => {
        return {
          environments: {},
          contextView: {}
        };
      }
    }
  },
  data: function() {
    var sortOrders = {
      name: 1,
      endpointType: 1,
      properties: 1
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

      EnvironmentsActions.editEnvironment({});
    },
    editItem: function(item, $event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      EnvironmentsActions.editEnvironment(item);
    },
    deleteItem: function(item, $event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      EnvironmentsActions.deleteEnvironment(item);
    },
    sortBy: function(key) {
      this.sortKey = key;
      this.sortOrders[key] = this.sortOrders[key] * -1;
    }
  },
  filters: {
    propertiesToString: propertiesToString,
    envrionmentOrderBy: function(items, sortKey, reverse) {
      if (!sortKey) {
        return items;
      }
      var order = reverse && reverse < 0 ? -1 : 1;

      return items.asMutable().sort(function(a, b) {

        if (sortKey === 'properties') {
          a = propertiesToString(a.properties);
          b = propertiesToString(b.properties);
        } else {
          a = a[sortKey];
          b = b[sortKey];
        }
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

Vue.component('environments-view', EnvironmentsView);

export default EnvironmentsView;
