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
import Alert from 'components/common/Alert';
import { ResourcePoolsActions, ResourcePoolsContextToolbarActions } from 'actions/Actions';
import constants from 'core/constants';
import utils from 'core/utils';
import DropdownSearchMenu from 'components/common/DropdownSearchMenu';
import Tags from 'components/common/Tags';

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

  this.tags = new Tags(this.$el.find('.tags-input'));
  this.$el.find('.tags').hide();

  addEventListeners.call(this);
}

ResourcePoolRowEditor.prototype.getEl = function() {
  return this.$el;
};

ResourcePoolRowEditor.prototype.setData = function(data) {

  let title = this.$el.find('.title');
  let nameInput = this.$el.find('.name-input');
  let dynamicInput = this.$el.find('.dynamic-input');
  let tags = this.$el.find('.tags');

  if (this.item !== data.item) {
    this.item = data.item;

    if (data.item && data.item.resourcePoolState) {
      title.html(i18n.t('app.resourcePool.edit.update'));
      nameInput.val(data.item.resourcePoolState.name);
      if (data.item.resourcePoolState.__tags && data.item.resourcePoolState.__tags.length) {
        dynamicInput.prop('checked', true);
        tags.show();
      } else {
        dynamicInput.prop('checked', false);
        tags.hide();
      }
      this.tags.setValue(data.item.resourcePoolState.__tags);
    } else {
      title.html(i18n.t('app.resourcePool.edit.createNew'));
      nameInput.val('');
      dynamicInput.prop('checked', false);
      tags.hide();
      this.tags.setValue([]);
    }
  }

  // Fix for css navigation transition glitch
  // https://bugs.chromium.org/p/chromium/issues/detail?id=97367
  setTimeout(() => {
    nameInput.first().focus();
  }, 310);

  this.$el.find('.resourcePoolEdit-save').removeAttr('disabled').removeClass('loading');

  var currentEndpointDocumentSelfLink = this.item && this.item.resourcePoolState
      && this.item.resourcePoolState.__endpointLink;

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

  this.$el.on('change', '.dynamic-input', (e) => {
    let tags = this.$el.find('.tags');
    if (e.target.checked) {
      tags.show();
    } else {
      tags.hide();
    }
    this.tags.setValue([]);
  });

  this.$el.find('.resourcePoolEdit').on('click', '.resourcePoolEdit-save', (e) => {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');

    let item = {
      resourcePoolState: {}
    };
    if (this.item) {
      item.documentSelfLink =
        this.item.resourcePoolState.documentSelfLink;
      item.resourcePoolState.documentSelfLink =
        this.item.resourcePoolState.documentSelfLink;
      if (this.item.epzState) {
        item.epzState = {
          documentSelfLink: this.item.epzState.documentSelfLink,
          resourcePoolLink: this.item.resourcePoolState.documentSelfLink
        };
      }
    }
    item.resourcePoolState.name = this.$el.find('.name-input').val();
    item.resourcePoolState.customProperties = {};

    if (this.endpointInput) {
      let selectedEndpoint = this.endpointInput.getSelectedOption();
      item.resourcePoolState.customProperties.__endpointLink =
          selectedEndpoint && selectedEndpoint.id;
    }

    let tags = this.tags.getValue().reduce((prev, curr) => {
      if (!prev.find((tag) => tag.key === curr.key && tag.value === curr.value)) {
        prev.push(curr);
      }
      return prev;
    }, []);

    if (this.item) {
      ResourcePoolsActions.updateResourcePool(item, tags);
    } else {
      ResourcePoolsActions.createResourcePool(item, tags);
    }
  });

  this.$el.find('.resourcePoolEdit').on('click', '.resourcePoolEdit-cancel', (e) => {
    e.preventDefault();
    this.item = null;
    ResourcePoolsActions.cancelEditResourcePool();
  });

  this.$el.find('.name-input').on('input change', () => toggleButtonsState(this.$el));
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
