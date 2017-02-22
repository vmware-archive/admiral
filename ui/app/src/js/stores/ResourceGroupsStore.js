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

import * as actions from 'actions/Actions';
import services from 'core/services';
import utils from 'core/utils';
import constants from 'core/constants';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const OPERATION = {
  LIST: 'list'
};

let ResourceGroupsStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [actions.ResourceGroupsActions],

  onRetrieveGroups: function() {
    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['items'], constants.LOADING);
      this.emitChange();

      operation.forPromise(services.loadResourceGroups()).then((result) => {
        var groups = Object.values(result);

        this.setInData(['items'], groups);
        this.emitChange();
      });
    }
  },

  onEditGroup: function(group) {
    this.setInData(['editingItemData', 'item'], group);
    this.emitChange();
  },

  onCancelEditGroup: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateGroup: function(group) {
    services.createResourceGroup(group).then((createdGroup) => {
      var immutableGroup = Immutable(createdGroup);

      var groups = this.data.items.asMutable();
      groups.push(immutableGroup);

      this.setInData(['items'], groups);
      this.setInData(['newItem'], immutableGroup);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the new item is no longer actual
      this.setInData(['newItem'], null);
    }).catch(this.onGenericEditError);
  },

  onUpdateGroup: function(group) {
    services.updateResourceGroup(group).then((updatedGroup) => {
      // If the backend did not make any changes, the response will be empty
      updatedGroup = updatedGroup || group;
      var immutableGroup = Immutable(updatedGroup);

      var groups = this.data.items.asMutable();
      for (var i = 0; i < groups.length; i++) {
        if (groups[i].documentSelfLink === immutableGroup.documentSelfLink) {
          groups[i] = immutableGroup;
        }
      }

      this.setInData(['items'], groups);
      this.setInData(['updatedItem'], immutableGroup);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the updated item is no longer actual
      this.setInData(['updatedItem'], null);
    }).catch(this.onGenericEditError);
  },

  onDeleteGroup: function(group, fromCenterView) {

    services.deleteResourceGroup(group).then(() => {
      if (fromCenterView) {
        // delete was invoked from the center view
        actions.ResourceGroupsActions.projectOperationCompleted(
          constants.RESOURCES.PROJECTS.OPERATION.REMOVE);

      } else {
        // delete was invoked from the right context view

        let groups = this.data.items.filter((gr) => gr.documentSelfLink !== group.documentSelfLink);

        this.setInData(['items'], groups);
        this.emitChange();

      }

    }).catch((e) => {
      if (fromCenterView) {

        actions.ResourceGroupsActions.projectOperationCompleted(
          constants.RESOURCES.PROJECTS.OPERATION.REMOVE);

      } else {

        var validationErrors = utils.getValidationErrors(e);
        this.setInData(['updatedItem'], group);
        this.setInData(['validationErrors'], validationErrors);
        this.emitChange();

        // After we notify listeners, the updated item is no longer actual
        this.setInData(['updatedItem'], null);
        this.setInData(['validationErrors'], null);

      }
    });
  },

  onGenericEditError: function(e) {
    var validationErrors = utils.getValidationErrors(e);
    this.setInData(['editingItemData', 'validationErrors'], validationErrors);
    console.error(e);

    this.emitChange();
  },

  refreshCenterView: function() {
    var queryOptions = utils.getIn(this.data, ['listView', 'queryOptions']);
    actions.ContainerActions.openContainers(queryOptions, true);
  }

});

export default ResourceGroupsStore;
