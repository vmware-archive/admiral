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
import links from 'core/links';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const OPERATION = {
  LIST: 'list'
};

let CredentialsStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [actions.CredentialsActions],

  onRetrieveCredentials: function() {
    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['items'], constants.LOADING);
      this.emitChange();

      operation.forPromise(services.loadCredentials()).then((result) => {
        // Transforming to the model of the view
        var credentials = [];
        for (var key in result) {
          if (result.hasOwnProperty(key)) {
            // TODO: remove once update to Xenon and fix credentials filtering
            try {
              var viewModel = toViewModel(result[key]);
              credentials.push(viewModel);
            } catch (e) {
              // do nothing
            }
          }
        }

        this.setInData(['items'], credentials);
        this.emitChange();
      });
    }
  },

  onEditCredential: function(credential) {
    this.setInData(['editingItemData', 'item'], credential);
    this.emitChange();
  },

  onCancelEditCredential: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateCredential: function(credential) {
    var dto = toDto(credential);
    services.createCredential(dto).then((createdCredential) => {
      var viewModel = toViewModel(createdCredential);

      var immutableCredential = Immutable(viewModel);

      var credentials = this.data.items.asMutable();
      credentials.push(immutableCredential);

      this.setInData(['items'], credentials);
      this.setInData(['newItem'], immutableCredential);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the new item is no logner actual
      this.setInData(['newItem'], null);
    }).catch(this.onGenericEditError);
  },

  onUpdateCredential: function(credential) {
    var dto = toDto(credential);
    services.updateCredential(dto).then((updatedCredential) => {
      // If the backend did not make any changes, the response will be empty
      updatedCredential = updatedCredential || dto;

      var viewModel = toViewModel(updatedCredential);

      var immutableCredential = Immutable(viewModel);

      var credentials = this.data.items.asMutable();

      for (var i = 0; i < credentials.length; i++) {
        if (credentials[i].id === immutableCredential.id) {
          credentials[i] = immutableCredential;
        }
      }

      this.setInData(['items'], credentials);
      this.setInData(['updatedItem'], immutableCredential);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the updated item is no logner actual
      this.setInData(['updatedItem'], null);
    }).catch(this.onGenericEditError);
  },

  onDeleteCredential: function(credential) {
    var dto = toDto(credential);
    services.deleteCredential(dto).then(() => {
      var credentials = this.data.items.asMutable();

      for (var i = credentials.length - 1; i >= 0; i--) {
        if (credentials[i].id === credential.id) {
          credentials.splice(i, 1);
        }
      }

      this.setInData(['items'], credentials);
      this.emitChange();
    }).catch((e) => {
      var validationErrors = utils.getValidationErrors(e);
      this.setInData(['updatedItem'], credential);
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

var toViewModel = function(dto) {
  var viewModel = {
    type: dto.type
  };

  viewModel.documentSelfLink = dto.documentSelfLink;
  viewModel.id = dto.documentSelfLink.substring(links.CREDENTIALS.length + 1);
  viewModel.customProperties = utils.getDisplayableCustomProperties(dto.customProperties);

  let name = utils.getCustomPropertyValue(dto.customProperties, '__authCredentialsName');
  if (!name) {
    viewModel.name = viewModel.id;
  } else {
    viewModel.name = name;
  }

  if (dto.type === constants.CREDENTIALS_TYPE.PASSWORD) {
    viewModel.username = dto.userEmail;
    viewModel.password = dto.privateKey;

  } else if (dto.type === constants.CREDENTIALS_TYPE.PUBLIC_KEY) {
    // As discussed on review https://reviewboard.eng.vmware.com/r/825015/ and based on current
    // integration tests
    viewModel.privateKey = dto.privateKey;
    viewModel.publicKey = dto.publicKey;

  } else if (dto.type === constants.CREDENTIALS_TYPE.PRIVATE_KEY) {
    viewModel.username = dto.userEmail;
    viewModel.privateKey = dto.privateKey;

  } else if (dto.type === constants.CREDENTIALS_TYPE.PUBLIC) {
    viewModel.publicKey = dto.publicKey;

  } else if (dto.type === constants.CREDENTIALS_TYPE.BEARER_TOKEN) {
    viewModel.privateKey = dto.privateKey;

  } else {
    throw 'Unknown type ' + dto.type;
  }

  return viewModel;
};

var toDto = function(viewModel) {
  var dto = {
    type: viewModel.type
  };

  // If we make a POST, the id will be picked up as document self link, prepended with
  // '/core/auth/credentials/'. If we make PATCH, this will be ignored
  dto.documentSelfLink = viewModel.documentSelfLink || viewModel.id;
  if (dto.type === constants.CREDENTIALS_TYPE.PASSWORD) {
    dto.userEmail = viewModel.username;
    dto.privateKey = viewModel.password;

  } else if (dto.type === constants.CREDENTIALS_TYPE.PRIVATE_KEY) {
    dto.userEmail = viewModel.username;
    dto.privateKey = viewModel.privateKey;

  } else if (dto.type === constants.CREDENTIALS_TYPE.PUBLIC_KEY) {
    // As discussed on review https://reviewboard.eng.vmware.com/r/825015/ and based on current
    // integration tests
    dto.privateKey = viewModel.privateKey;
    dto.publicKey = viewModel.publicKey;

  } else if (dto.type === constants.CREDENTIALS_TYPE.PUBLIC) {
    dto.publicKey = viewModel.publicKey;

  } else if (dto.type === constants.CREDENTIALS_TYPE.BEARER_TOKEN) {
    dto.privateKey = viewModel.privateKey;

  } else {
    throw 'Unknown type ' + dto.type;
  }

  dto.customProperties = viewModel.customProperties;
  if (viewModel.customProperties.asMutable) {
    dto.customProperties = viewModel.customProperties.asMutable();
  }

  if (!dto.customProperties.__authCredentialsName) {
    dto.customProperties.__authCredentialsName = viewModel.name;
  }

  return dto;
};

export default CredentialsStore;
