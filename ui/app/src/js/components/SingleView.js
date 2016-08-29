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

import ContainerDetails from 'components/containers/ContainerDetails';
import ContainerDefinitionForm from 'components/containers/ContainerDefinitionForm';
import ContainersStore from 'stores/ContainersStore';
import * as actions from 'actions/Actions';
import modal from 'core/modal';

function SingleView($el) {
  this.$el = $el;

  addEventListeners.call(this);
  initRoutes.call(this);
}

function removeOldViews() {
  if (this.view) {
    if (this.view.detach) {
      this.view.detach();
    }

    this.view = null;
    this.$el.empty();
  }
}

function openContainerDetails() {
  this.view = new ContainerDetails();
  this.$el.append(this.view.getEl());
  this.view.attached();
}

function openContainerForm() {
  var definitionForm = new ContainerDefinitionForm();
  $('#main').append(definitionForm.getEl());

  // Hook for the CAFE Container extension
  window.validate = function() {
    var validationErrors = definitionForm.validate();
    definitionForm.applyValidationErrors(validationErrors);
    return !validationErrors;
  };

  // Hook for the CAFE Container extension
  window.getContainerDescription = function() {
    return definitionForm.getContainerDescription();
  };
}

function addEventListeners() {
  var _this = this;
  ContainersStore.listen(function(data) {
    if (_this.view instanceof ContainerDetails && data.selectedItemDetails) {
      _this.view.setData(data.selectedItemDetails);
    }
  });
}

function initRoutes() {
  var _this = this;

  var parseHash = function(newHash) {
    modal.hide();
    removeOldViews.call(_this);
    crossroads.parse(newHash);
  };

  crossroads.addRoute('/containers/{containerId}', function(containerId) {
    openContainerDetails.call(_this);
    actions.ContainerActions.openContainerDetails(containerId);
  });

  crossroads.addRoute('/container-form', function() {
    openContainerForm.call(_this);
  });

  crossroads.bypassed.add(function() {
    _this.$el.html('Unknown place!');
  });

  hasher.stop();
  hasher.initialized.add(parseHash);
  hasher.changed.add(parseHash);
  hasher.init();
}

export default SingleView;
