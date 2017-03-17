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

import templateHelpers from 'core/templateHelpers';

window.i18n = i18next;

var initializer = {};
initializer.init = function(callback) {
  Vue.config.debug = false;
  Vue.config.silent = false;

  templateHelpers.register();

  const initI18N = function() {
    return new Promise((resolve) => {
      i18next.use(i18nextXHRBackend)
        .use(i18nextBrowserLanguageDetector)
        .init({
          ns: ['admiral'],
          defaultNS: 'admiral',
          fallbackLng: 'en',
          backend: {
            loadPath: 'messages/{{ns}}.{{lng}}.json'
          },
          debug: true
      }, resolve);
    });
  };

  const DEFAULT_ADAPTERS = [{
    id: 'aws',
    name: 'AWS',
    iconSrc: 'image-assets/endpoints/aws.png',
    endpointEditor: 'aws-endpoint-editor',
    computeProfileEditor: 'aws-compute-profile-editor',
    networkProfileEditor: 'aws-network-profile-editor',
    storageProfileEditor: 'aws-storage-profile-editor'
  }, {
    id: 'azure',
    name: 'Azure',
    iconSrc: 'image-assets/endpoints/azure.png',
    endpointEditor: 'azure-endpoint-editor',
    computeProfileEditor: 'azure-compute-profile-editor',
    networkProfileEditor: 'azure-network-profile-editor',
    storageProfileEditor: 'azure-storage-profile-editor'
  }, {
    id: 'vsphere',
    name: 'vSphere',
    iconSrc: 'image-assets/endpoints/vsphere.png',
    endpointEditor: 'vsphere-endpoint-editor',
    computeProfileEditor: 'vsphere-compute-profile-editor',
    networkProfileEditor: 'vsphere-network-profile-editor',
    storageProfileEditor: 'vsphere-storage-profile-editor'
  }];

  var ft = require('core/ft').default;
  var services = require('core/services').default;
  var utils = require('core/utils').default;
  require('components/common/CommonComponentsRegistry').default; //eslint-disable-line

  services.loadConfigurationProperties().then((properties) => {
    var configurationProperties = {};
    for (var prop in properties) {
      if (properties.hasOwnProperty(prop)) {
        configurationProperties[properties[prop].key] = properties[prop].value;
      }
    }
    utils.initializeConfigurationProperties(configurationProperties);
    return Promise.all([
      ft.isExternalPhotonAdaptersEnabled() ? services.loadAdapters() : Promise.resolve([]),
      initI18N()
    ]);
  }).then(([adapters]) => {
    utils.initializeAdapters(DEFAULT_ADAPTERS.concat(Object.values(adapters).map((adapter) => {
      return {
        id: adapter.id,
        name: adapter.name,
        iconSrc: adapter.customProperties.icon.replace(/^\//, ''),
        endpointEditor: adapter.customProperties.endpointEditor,
        computeProfileEditor: adapter.customProperties.computeProfileEditor,
        networkProfileEditor: adapter.customProperties.networkProfileEditor,
        storageProfileEditor: adapter.customProperties.storageProfileEditor
      };
    })));
    return Promise.all(Object.values(adapters).map((adapter) =>
      services.loadScript(adapter.customProperties.uiLink.replace(/^\//, ''))));
  }).then(() => {
    callback();
  }).catch((err) => {
    console.warn('Error when loading configuration! Error: ', err);
  });
};

export default initializer;
