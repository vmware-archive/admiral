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
import CredentialsStore from 'stores/CredentialsStore';
import CertificatesStore from 'stores/CertificatesStore';
import ContextPanelStoreMixin from 'stores/mixins/ContextPanelStoreMixin';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

let RegistriesStore;

let itemSelectTimeout;

const DOCKER_REGISTRY_ENDPOINT_TYPE = 'container.docker.registry';

const OPERATION = {
  LIST: 'LIST'
};

let _createRegistryHostSpec = function(registry) {
  var registryState = $.extend({}, registry);
  registryState.endpointType = DOCKER_REGISTRY_ENDPOINT_TYPE;
  registryState.authCredentialsLink =
    registryState.credential ? registryState.credential.documentSelfLink : null;
  delete registryState.credential;

  var registryHostSpec = {};
  registryHostSpec.hostState = registryState;

  return registryHostSpec;
};

let doUpdateRegistryDto = function(dto) {
  services.updateRegistry(dto).then((updatedRegistry) => {
    // If the backend did not make any changes, the response will be empty
    updatedRegistry = updatedRegistry || dto;

    var immutableRegistry = Immutable(updatedRegistry);

    var registries = this.data.registries.items.asMutable();

    for (var i = 0; i < registries.length; i++) {
      if (registries[i].documentSelfLink === immutableRegistry.documentSelfLink) {
        registries[i] = immutableRegistry;
      }
    }

    this.setInData(['registries', 'items'], registries);
    this.setInData(['registries', 'updatedItem'], immutableRegistry);
    this.setInData(['registries', 'editingItemData'], null);
    this.emitChange();

    // After we notify listeners, the updated item is no logner actual
    this.setInData(['registries', 'updatedItem'], null);
  }).catch(this.onGenericEditError);
};



RegistriesStore = Reflux.createStore({
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],

  init: function() {
    CredentialsStore.listen((credentialsData) => {
      if (this.isContextPanelActive(constants.CONTEXT_PANEL.CREDENTIALS)) {
        this.setActiveItemData(credentialsData);

        var itemToSelect = credentialsData.newItem || credentialsData.updatedItem;
        if (itemToSelect && this.data.contextView.shouldSelectAndComplete) {
          clearTimeout(itemSelectTimeout);
          itemSelectTimeout = setTimeout(() => {
            this.setInData(['registries', 'editingItemData', 'selectedCredential'],
              itemToSelect);
            this.emitChange();

            this.closeToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }

        if (credentialsData.items && this.data.registries.editingItemData) {
          this.setInData(['registries', 'editingItemData', 'credentials'],
            credentialsData.items);
        }

        RegistriesStore.emitChange();
      }

      if (credentialsData.items && this.data.registries && this.data.registries.editingItemData) {
        this.setInData(['registries', 'editingItemData', 'credentials'],
          credentialsData.items);

        RegistriesStore.emitChange();
      }
    });

    CertificatesStore.listen((certificatesData) => {
      if (this.isContextPanelActive(constants.CONTEXT_PANEL.CERTIFICATES)) {
        this.setActiveItemData(certificatesData);

        var itemToSelect = certificatesData.newItem || certificatesData.updatedItem;
        if (itemToSelect && this.data.contextView.shouldSelectAndComplete) {
          clearTimeout(itemSelectTimeout);
          itemSelectTimeout = setTimeout(() => {
            this.closeToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      RegistriesStore.emitChange();
    });
  },

  listenables: [actions.RegistryActions, actions.RegistryContextToolbarActions],

  onOpenRegistries: function() {
    this.setInData(['contextView'], {});

    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['registries', 'items'], constants.LOADING);

      operation.forPromise(services.loadRegistries()).then((result) => {
        // Transforming from associative array to array
        var registries = [];
        for (var key in result) {
          if (result.hasOwnProperty(key)) {
            registries.push(result[key]);
          }
        }

        this.setInData(['registries', 'items'], registries);
        this.emitChange();
      });
    }

    this.emitChange();

    actions.CredentialsActions.retrieveCredentials();
    actions.CertificatesActions.retrieveCertificates();
  },

  onEditRegistry: function(registry) {
    var registryModel = {};
    if (registry) {
      registryModel = $.extend({}, registry);
    }

    var credentials = CredentialsStore.getData().items;

    if (credentials && registry) {
      for (var i = 0; i < credentials.length; i++) {
        var credential = credentials[i];
        if (credential.documentSelfLink === registry.authCredentialsLink) {
          registryModel.credential = credential;
          break;
        }
      }
    }

    this.setInData(['registries', 'editingItemData', 'item'], registryModel);
    this.setInData(['registries', 'editingItemData', 'credentials'], credentials);

    this.emitChange();
  },

  onCancelEditRegistry: function() {
    this.setInData(['registries', 'editingItemData'], null);
    this.emitChange();
  },

  onCreateRegistry: function(registry) {
    this.setInData(['registries', 'editingItemData', 'validationErrors'], null);

    var registryHostSpec = _createRegistryHostSpec(registry);

    services.createOrUpdateRegistry(registryHostSpec).then(([result, status, xhr]) => {
      if (result && result.certificate) {
        this.setInData(['registries', 'editingItemData',
          'shouldAcceptCertificate'
        ], {
          certificateHolder: result
        });
        this.emitChange();
      } else {
        this.onRegistryAdded(result, status, xhr);
      }
    }).catch(this.onGenericEditError);
  },

  onCheckInsecureRegistry: function(address) {
    if (address && address.toLowerCase().startsWith('http:')) {
      this.setInData(['registries', 'editingItemData', 'validationErrors', '_insecure'], true);
    } else {
      this.setInData(['registries', 'editingItemData', 'validationErrors'], null);
    }

    this.emitChange();
  },

  onVerifyRegistry: function(registry) {
    this.setInData(['registries', 'editingItemData', 'validationErrors'], null);

    var registryHostSpec = _createRegistryHostSpec(registry);

    services.validateRegistry(registryHostSpec).then((registryHostSpec) => {
      if (registryHostSpec && registryHostSpec.certificate) {
        this.setInData(['registries', 'editingItemData',
          'shouldAcceptCertificate'
        ], {
          certificateHolder: registryHostSpec,
          verify: true
        });
        this.emitChange();
      } else {
        this.setInData(['registries', 'editingItemData', 'validationErrors',
            '_valid'
          ],
          true);
        this.emitChange();
      }
    }).catch(this.onGenericEditError);
  },

  onAcceptCertificateAndCreateRegistry: function(certificateHolder, registry) {
    services.createCertificate(certificateHolder)
      .then(() => {
        this.setInData(['registries', 'editingItemData',
            'shouldAcceptCertificate'
          ],
          null);
        this.emitChange();

        var registryHostSpec = _createRegistryHostSpec(registry);

        return services.createOrUpdateRegistry(registryHostSpec);
      })
      .then(([result, status, xhr]) => {
        this.onRegistryAdded(result, status, xhr);
      })
      .catch(this.onGenericEditError);
  },

  onAcceptCertificateAndVerifyRegistry: function(certificateHolder, registry) {
    services.createCertificate(certificateHolder)
      .then(() => {
        this.setInData(['registries', 'editingItemData',
            'shouldAcceptCertificate'
          ],
          null);
        this.emitChange();

        var registryHostSpec = _createRegistryHostSpec(registry);

        return services.validateRegistry(registryHostSpec);
      })
      .then(() => {
        this.setInData(['registries', 'editingItemData', 'validationErrors',
            '_valid'
          ],
          true);
        this.emitChange();
      })
      .catch(this.onGenericEditError);
  },

  onRegistryAdded: function(data, status, xhr) {
    this.setInData(['registries', 'items'], constants.LOADING);
    this.setInData(['registries', 'editingItemData'], null);
    this.emitChange();

    var newRegistryLocation = xhr.getResponseHeader('Location');

    services.loadRegistries().then((allRegistries) => {
      var newRegistry;

      // Transforming from associative array to array
      var registries = [];
      for (var key in allRegistries) {
        if (key === newRegistryLocation) {
          newRegistry = new Immutable(allRegistries[key]);
          registries.push(newRegistry);
        } else {
          registries.push(allRegistries[key]);
        }
      }

      this.setInData(['registries', 'newItem'], newRegistry);
      this.setInData(['registries', 'items'], registries);

      this.emitChange();

      // After we notify listeners, the new item is no logner actual
      this.setInData(['registries', 'newItem'], null);
    });
  },

  onUpdateRegistry: function(registry) {
    this.setInData(['registries', 'editingItemData', 'validationErrors'], null);

    var registryHostSpec = _createRegistryHostSpec(registry);
    services.createOrUpdateRegistry(registryHostSpec).then(([result]) => {
        if (result && result.certificate) {
          this.setInData(['registries', 'editingItemData',
            'shouldAcceptCertificate'
          ], {
            certificateHolder: result
          });
          this.emitChange();
        } else {
          let immutableRegistry = Immutable(registry);

          let registries = this.data.registries.items.map((reg) => {
            if (reg.documentSelfLink === immutableRegistry.documentSelfLink) {
              return immutableRegistry;
            } else {
              return reg;
            }
          });

          this.setInData(['registries', 'items'], registries);
          this.setInData(['registries', 'updatedItem'], immutableRegistry);
          this.setInData(['registries', 'editingItemData'], null);
          this.emitChange();

          // After we notify listeners, the updated item is no logner actual
          this.setInData(['registries', 'updatedItem'], null);
        }
      }).catch(this.onGenericEditError);
  },

  onEnableRegistry: function(registry) {
    var dto = {
      documentSelfLink: registry.documentSelfLink,
      disabled: false
    };

    doUpdateRegistryDto.call(this, dto);
  },

  onDisableRegistry: function(registry) {
    var dto = {
      documentSelfLink: registry.documentSelfLink,
      disabled: true
    };

    doUpdateRegistryDto.call(this, dto);
  },

  onDeleteRegistry: function(registry) {
    services.deleteRegistry(registry).then(() => {
      var registries = this.data.registries.items.asMutable();

      for (var i = registries.length - 1; i >= 0; i--) {
        if (registries[i].documentSelfLink === registry.documentSelfLink) {
          registries.splice(i, 1);
        }
      }

      this.setInData(['registries', 'items'], registries);
      this.emitChange();
    });
  },

  onOpenToolbarCredentials: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.CREDENTIALS, CredentialsStore.getData(), false);
  },

  onOpenToolbarCertificates: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.CERTIFICATES, CertificatesStore.getData(),
      false);
  },

  onCloseToolbar: function() {
    this.closeToolbar();
  },

  onCreateCredential: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.CREDENTIALS, CredentialsStore.getData(), true);
    actions.CredentialsActions.editCredential();
  },

  onManageCredentials: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.CREDENTIALS, CredentialsStore.getData(), true);
  },

  onManageCertificates: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.CERTIFICATES, CertificatesStore.getData(),
      true);
  },

  onGenericEditError: function(e) {
    var validationErrors = utils.getValidationErrors(e);

    this.setInData(['registries', 'editingItemData', 'validationErrors'], validationErrors);
    console.error(e);
    this.emitChange();
  }
});

export default RegistriesStore;
