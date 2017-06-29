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
import CertificatesStore from 'stores/CertificatesStore';

const ENUMERATION_RETRIES = 10;
const OPERATION = {
  LIST: 'list'
};

const verifyEnumeration = function(endpoint, retries, callback) {
  services.searchCompute(endpoint.resourcePoolLink, '').then((result) => {
    setTimeout(() => {
      if (result.totalCount !== 0 || retries === 0) {
        callback(result);
      } else {
        verifyEnumeration(endpoint, retries - 1, callback);
      }
    }, 1000);
  });
};

const onOpenToolbarItem = function(name, data, shouldSelectAndComplete) {
  var contextViewData = {
    expanded: true,
    activeItem: {
      name: name,
      data: data
    },
    shouldSelectAndComplete: shouldSelectAndComplete
  };

  this.setInData(['editingItemData', 'contextView'], contextViewData);
  this.emitChange();
};

const isContextPanelActive = function(name) {
  var activeItem = this.data.editingItemData.contextView &&
      this.data.editingItemData.contextView.activeItem;
  return activeItem && activeItem.name === name;
};

let EndpointsStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [actions.EndpointContextToolbarActions, actions.EndpointsActions],

  init: function() {

    CertificatesStore.listen((certificatesData) => {
      if (!this.data.editingItemData) {
        return;
      }

      this.setInData(['editingItemData', 'certificates'], certificatesData.items);
      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.CERTIFICATES)) {
        this.setInData(['editingItemData', 'contextView', 'activeItem', 'data'],
          certificatesData);

        var itemToSelect = certificatesData.newItem || certificatesData.updatedItem;
        if (itemToSelect && this.data.editingItemData.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['editingItemData', 'certificate'], itemToSelect);
            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      this.emitChange();
    });

    this.setInData(['deleteConfirmationLoading'], false);
  },

  onRetrieveEndpoints: function() {
    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['itemsLoading'], true);
      this.emitChange();

      operation.forPromise(services.loadEndpoints()).then((result) => {
        var endpoints = [];
        for (var key in result) {
          if (result.hasOwnProperty(key)) {
            endpoints.push(result[key]);
          }
        }
        Promise.all(endpoints.map((endpoint) => {
          let documentLink = endpoint.endpointProperties.linkedEndpointLink;
          return documentLink ?
              services.loadEndpoint(documentLink) : Promise.resolve();
        })).then((linkedEndpoints) => {
          linkedEndpoints.forEach((e, i) => endpoints[i].linkedEndpoint = e);
          this.setInData(['items'], endpoints);
          this.setInData(['itemsLoading'], false);
          this.emitChange();
        });
      });
    }
  },

  onEditEndpoint: function(endpoint) {
    this.setInData(['editingItemData', 'item'], endpoint);
    this.setInData(['editingItemData', 'verified'], false);
    this.emitChange();
    actions.CertificatesActions.retrieveCertificates();
  },

  onCancelEditEndpoint: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateEndpoint: function(endpoint) {

    this.setInData(['editingItemData', 'item'], endpoint);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    services.createEndpoint(endpoint).then((createdEndpoint) => {
      verifyEnumeration(createdEndpoint, ENUMERATION_RETRIES, () => {
        var immutableEndpoint = Immutable(createdEndpoint);

        var endpoints = this.data.items ? this.data.items.asMutable() : [];

        endpoints.push(immutableEndpoint);

        this.setInData(['items'], endpoints);
        this.setInData(['newItem'], immutableEndpoint);
        this.setInData(['editingItemData'], null);
        this.emitChange();

        setTimeout(() => {
          this.setInData(['newItem'], null);
          this.emitChange();
        }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
      });
    }).catch(this.onGenericEditError);
  },

  onUpdateEndpoint: function(endpoint) {

    this.setInData(['editingItemData', 'item'], endpoint);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    services.updateEndpoint(endpoint).then((updatedEndpoint) => {
      // If the backend did not make any changes, the response will be empty
      updatedEndpoint = updatedEndpoint || endpoint;

      verifyEnumeration(updatedEndpoint, ENUMERATION_RETRIES, () => {
        var immutableEndpoint = Immutable(updatedEndpoint);
        var endpoints = this.data.items.asMutable();

        for (var i = 0; i < endpoints.length; i++) {
          if (endpoints[i].documentSelfLink === immutableEndpoint.documentSelfLink) {
            endpoints[i] = immutableEndpoint;
          }
        }

        this.setInData(['items'], endpoints);
        this.setInData(['updatedItem'], immutableEndpoint);
        this.setInData(['editingItemData'], null);
        this.emitChange();

        setTimeout(() => {
          this.setInData(['updatedItem'], null);
          this.emitChange();
        }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
      });
    }).catch(this.onGenericEditError);
  },

  onDeleteEndpoint: function(endpoint) {
    this.setInData(['deleteConfirmationLoading'], true);
    this.emitChange();

    services.deleteEndpoint(endpoint).then(() => {
      var endpoints = this.data.items.filter((item) =>
          item.documentSelfLink !== endpoint.documentSelfLink);
      this.setInData(['items'], endpoints);
      this.setInData(['deleteConfirmationLoading'], false);
      this.emitChange();
    }).catch((e) => {
      var validationErrors = utils.getValidationErrors(e);
      this.setInData(['updatedItem'], endpoint);
      this.setInData(['validationErrors'], validationErrors);
      this.emitChange();

      // After we notify listeners, the updated item is no logner actual
      setTimeout(() => {
        this.setInData(['updatedItem'], null);
        this.setInData(['validationErrors'], null);
        this.setInData(['deleteConfirmationLoading'], false);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
    });
  },

  onVerifyEndpoint: function(endpoint) {
    this.setInData(['editingItemData', 'certificateInfo'], null);
    this.setInData(['editingItemData', 'item'], endpoint);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'verifying'], true);
    this.emitChange();
    this.verifyEndpoint(endpoint);
  },

  onAcceptVerifyEndpoint: function() {
    this.setInData(['editingItemData', 'item', 'endpointProperties', 'certificate'],
        this.data.editingItemData.certificateInfo.certificate);
    this.setInData(['editingItemData', 'certificateInfo'], null);
    this.emitChange();
    this.verifyEndpoint(this.data.editingItemData.item);
  },

  verifyEndpoint: function(endpoint) {
    services.verifyEndpoint(endpoint).then((verifiedEndpoint) => {
      if (verifiedEndpoint && verifiedEndpoint.certificateInfo) {
        this.setInData(['editingItemData', 'certificateInfo'], verifiedEndpoint.certificateInfo);
      } else {
        this.setInData(['editingItemData', 'validationErrors', '_valid'], i18n.t('verified'));
        this.setInData(['editingItemData', 'verified'], true);
      }
      this.setInData(['editingItemData', 'verifying'], false);
      this.emitChange();
      setTimeout(() => {
        this.setInData(['editingItemData', 'validationErrors'], null);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
    }).catch(this.onGenericEditError);
  },

  onCancelVerifyEndpoint: function() {
    this.setInData(['editingItemData', 'certificateInfo'], null);
    this.emitChange();
  },

  onGenericEditError: function(e) {
    var validationErrors = utils.getValidationErrors(e);
    this.setInData(['editingItemData', 'validationErrors'], validationErrors);
    this.setInData(['editingItemData', 'saving'], false);
    this.setInData(['editingItemData', 'verifying'], false);
    console.error(e);
    this.emitChange();
  },

  onManageCertificates: function() {
    this.setInData(['editingItemData', 'certificateInfo'], null);
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.CERTIFICATES,
        CertificatesStore.getData(), true);
  },

  onOpenToolbarCertificates: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.CERTIFICATES,
        CertificatesStore.getData(), false);
  },

  onCloseToolbar: function() {
    if (!this.data.editingItemData) {
      this.closeToolbar();
    } else {
      let contextViewData = {
        expanded: false,
        activeItem: null
      };
      this.setInData(['editingItemData', 'contextView'], contextViewData);
      this.emitChange();
    }
  }
});

export default EndpointsStore;
