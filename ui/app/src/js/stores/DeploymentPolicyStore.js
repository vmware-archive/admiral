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

import * as actions from 'actions/Actions';
import services from 'core/services';
import utils from 'core/utils';
import constants from 'core/constants';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const OPERATION = {
  LIST: 'list'
};

let DeploymentPolicyStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [actions.DeploymentPolicyActions],

  onRetrieveDeploymentPolicies: function() {
    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['items'], constants.LOADING);
      this.emitChange();

      operation.forPromise(services.loadDeploymentPolicies()).then((result) => {
        var policies = [];
        for (var key in result) {
          if (result.hasOwnProperty(key)) {
            policies.push(result[key]);
          }
        }

        this.setInData(['items'], policies);
        this.emitChange();
      });
    }
  },

  onEditDeploymentPolicy: function(deploymentPolicy) {
    this.setInData(['editingItemData', 'item'], deploymentPolicy);
    this.emitChange();
  },

  onCancelEditDeploymentPolicy: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateDeploymentPolicy: function(deploymentPolicy) {
    services.createDeploymentPolicy(deploymentPolicy).then((createdDeploymentPolicy) => {
      var immutableDeploymentPolicy = Immutable(createdDeploymentPolicy);

      var deploymentPolicy = this.data.items.asMutable();
      deploymentPolicy.push(immutableDeploymentPolicy);

      this.setInData(['items'], deploymentPolicy);
      this.setInData(['newItem'], immutableDeploymentPolicy);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the new item is no logner actual
      this.setInData(['newItem'], null);
    }).catch(this.onGenericEditError);
  },

  onUpdateDeploymentPolicy: function(deploymentPolicy) {
    services.updateDeploymentPolicy(deploymentPolicy).then((updatedDeploymentPolicy) => {
      // If the backend did not make any changes, the response will be empty
      updatedDeploymentPolicy = updatedDeploymentPolicy || deploymentPolicy;

      var immutableDeploymentPolicy = Immutable(updatedDeploymentPolicy);

      var deploymentPolicies = this.data.items.asMutable();

      for (var i = 0; i < deploymentPolicies.length; i++) {
        if (deploymentPolicies[i].documentSelfLink === immutableDeploymentPolicy.documentSelfLink) {
          deploymentPolicies[i] = immutableDeploymentPolicy;
        }
      }

      this.setInData(['items'], deploymentPolicies);
      this.setInData(['updatedItem'], immutableDeploymentPolicy);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the updated item is no logner actual
      this.setInData(['updatedItem'], null);
    }).catch(this.onGenericEditError);
  },

  onDeleteDeploymentPolicy: function(deploymentPolicy) {
    services.deleteDeploymentPolicy(deploymentPolicy).then(() => {
      let deploymentPolicies =
          this.data.items.filter((dp) => dp.documentSelfLink !== deploymentPolicy.documentSelfLink);
      this.setInData(['items'], deploymentPolicies);
      this.emitChange();
    }).catch((e) => {
      var validationErrors = utils.getValidationErrors(e);
      this.setInData(['updatedItem'], deploymentPolicy);
      this.setInData(['validationErrors'], validationErrors);
      this.emitChange();

      // After we notify listeners, the updated item is no logner actual
      this.setInData(['updatedItem'], null);
      this.setInData(['validationErrors'], null);
    });
  },

  onGenericEditError: function(e) {
    var validationErrors = utils.getValidationErrors(e);
    this.setInData(['editingItemData', 'validationErrors'], validationErrors);
    console.error(e);
    this.emitChange();
  }

});

export default DeploymentPolicyStore;
