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

import ResourcePoolsRowEditTemplate from 'ResourcePoolsRowEditTemplate';
import MulticolumnInputs from 'components/common/MulticolumnInputs';
import Alert from 'components/common/Alert';
import { ResourcePoolsActions, ResourcePoolsContextToolbarActions } from 'actions/Actions';
import constants from 'core/constants';
import utils from 'core/utils';
import DropdownSearchMenu from 'components/common/DropdownSearchMenu';

const endpointManageOptions = [{
  id: 'endpoint-create',
  name: i18n.t('app.endpoint.createNew'),
  icon: 'plus'
}, {
  id: 'endpoint-manage',
  name: i18n.t('app.endpoint.manage'),
  icon: 'pencil'
}];

function ResourcePoolRowEditor() {
  this.$el = $(ResourcePoolsRowEditTemplate({
    showEndpoint: utils.isApplicationCompute()
  }));

  this.alert = new Alert(this.$el, this.$el.find('.resourcePoolEdit'));

  this.customProperties = new MulticolumnInputs(this.$el.find('.custom-properties'), {
    name: {
      header: i18n.t('customProperties.name'),
      placeholder: i18n.t('customProperties.nameHint')
    },
    value: {
      header: i18n.t('customProperties.value'),
      placeholder: i18n.t('customProperties.valueHint')
    }
  });
  this.customProperties.setVisibilityFilter(utils.shouldHideCustomProperty);

  var endpointHolder = this.$el.find('.endpoint.dropdown-holder');

  if (utils.isApplicationCompute()) {
    this.endpointInput = new DropdownSearchMenu(endpointHolder, {
      title: i18n.t('dropdownSearchMenu.title', {
        entity: i18n.t('app.endpoint.entity')
      }),
      searchDisabled: true
    });
    this.endpointInput.setManageOptions(endpointManageOptions);
    this.endpointInput.setManageOptionSelectCallback(function(option) {
      if (option.id === 'endpoint-create') {
        ResourcePoolsContextToolbarActions.createEndpoint();
      } else {
        ResourcePoolsContextToolbarActions.manageEndpoints();
      }
    });
  }

  addEventListeners.call(this);
}

ResourcePoolRowEditor.prototype.getEl = function() {
  return this.$el;
};

ResourcePoolRowEditor.prototype.setData = function(data) {
  if (this.resourcePool !== data.item) {
    this.resourcePool = data.item;

    if (this.resourcePool) {
      this.$el.find('.title').html(i18n.t('app.resourcePool.edit.update'));
      this.$el.find('.name-input').val(this.resourcePool.name);
      this.customProperties.setData(utils.objectToArray(this.resourcePool.customProperties));
    } else {
      this.$el.find('.title').html(i18n.t('app.resourcePool.edit.createNew'));
      this.$el.find('.name-input').val('');
      this.customProperties.setData(null);
    }
  }
  this.$el.find('.name-input').first().focus();

  this.$el.find('.resourcePoolEdit-save').removeAttr('disabled').removeClass('loading');

  var currentEndpointDocumentSelfLink = this.resourcePool && this.resourcePool.__endpointLink;

  var selectedEndpoint = data.selectedEndpoint;

  if (this.endpointInput) {
    var adaptedEndpoints = [];
    if (data.endpoints) {
      for (var i = 0; i < data.endpoints.length; i++) {
        var endpoint = data.endpoints[i];
        let adaptedEndpoint = {
          id: endpoint.documentSelfLink,
          name: endpoint.name,
          iconSrc: 'image-assets/endpoints/' + endpoint.endpointType + '.png'
        };
        adaptedEndpoints.push(adaptedEndpoint);

        if (!selectedEndpoint && endpoint.documentSelfLink === currentEndpointDocumentSelfLink) {
          selectedEndpoint = endpoint;
        }
      }
    }
    this.endpointInput.setOptions(adaptedEndpoints);

    var oldSelectedEndpoint = this.selectedEndpoint || {};
    if (selectedEndpoint &&
        selectedEndpoint.documentSelfLink !== oldSelectedEndpoint.documentSelfLink) {
      let adaptedEndpoint = {
        id: selectedEndpoint.documentSelfLink,
        name: selectedEndpoint.name,
        iconSrc: 'image-assets/endpoints/' + selectedEndpoint.endpointType + '.png'
      };
      this.endpointInput.setSelectedOption(adaptedEndpoint);
    } else if (!selectedEndpoint) {
      this.endpointInput.setSelectedOption(null);
    }

    this.selectedEndpoint = selectedEndpoint;
  }

  applyValidationErrors.call(this, this.$el, data.validationErrors);

  toggleButtonsState(this.$el);

  this.data = data;
};

var addEventListeners = function() {
  var _this = this;

  this.$el.find('.resourcePoolEdit').on('click', '.resourcePoolEdit-save', function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');

    var rp = {};

    if (_this.resourcePool) {
      $.extend(rp, _this.resourcePool);
    }

    rp.name = _this.$el.find('.name-input').val();
    rp.customProperties = utils.arrayToObject(_this.customProperties.getData());

    if (_this.endpointInput) {
      rp.customProperties = rp.customProperties || {};
      var selectedEndpoint = _this.endpointInput.getSelectedOption();
      rp.customProperties.__endpointLink = selectedEndpoint && selectedEndpoint.documentSelfLink;
    }

    if (_this.resourcePool) {
      ResourcePoolsActions.updateResourcePool(rp);
    } else {
      ResourcePoolsActions.createResourcePool(rp);
    }
  });

  this.$el.find('.resourcePoolEdit').on('click', '.resourcePoolEdit-cancel', function(e) {
    e.preventDefault();
    _this.resourcePool = null;
    ResourcePoolsActions.cancelEditResourcePool();
  });

  this.$el.find('.name-input').on('input change', function() {
    toggleButtonsState(_this.$el);
  });
};

var applyValidationErrors = function($el, errors) {
  errors = errors || {};

  this.alert.toggle($el, constants.ALERTS.TYPE.FAIL, errors._generic);
};

var toggleButtonsState = function($el) {
  var nameValue = $el.find('.name-input').val();
  if (nameValue) {
    $el.find('.resourcePoolEdit-save').removeAttr('disabled').removeClass('loading');
  } else {
    $el.find('.resourcePoolEdit-save').attr('disabled', true);
  }
};

export default ResourcePoolRowEditor;
