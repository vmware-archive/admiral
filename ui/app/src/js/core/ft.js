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

import utils from 'core/utils';

var isDocsAvailable = false;


/**
 * General feature toggle utility
 */
var ft = {

  setDocsAvailable: function(value) {
    isDocsAvailable = value;
  },

  isContextAwareHelpEnabled: function() {
   return utils.getConfigurationPropertyBoolean('allow.ensemble.help');
  },

  isContextAwareHelpAvailable: function() {
   return this.isContextAwareHelpEnabled() && isDocsAvailable;
  },

  isNimbusEnabled: function() {
    return utils.getConfigurationPropertyBoolean('allow.nimbus');
  },

  isVchHostOptionEnabled: function() {
    return utils.getConfigurationPropertyBoolean('allow.ft.host-option.vch');
  },

  isExternalKubernetesEnabled: function() {
    return this.isPksEnabled()
              && utils.getConfigurationPropertyBoolean('allow.ft.host-option.kubernetes');
  },

  isPksEnabled: function() {
    return !utils.isVic() && utils.getConfigurationPropertyBoolean('allow.ft.pks');
  },

  isCreateHostOptionEnabled: function() {
    return utils.getConfigurationPropertyBoolean('allow.ft.host-option.create');
  },

  areClosuresAllowed: function() {
    return utils.getConfigurationPropertyBoolean('allow.closures');
  },

  showProjectsInNavigation: function() {
    return utils.getConfigurationPropertyBoolean('allow.ft.projects.in.navigation');
  },

  isPublicKeyCredentialsDisabled: function() {
    return utils.getConfigurationPropertyBoolean('disable.credentials.publicKey');
  },

  isRequestGraphEnabled: function() {
    return utils.getConfigurationPropertyBoolean('allow.ft.request-graph');
  },

  isDeploymentPoliciesEnabled: function() {
    return utils.isApplicationEmbedded();
  },

  isHostPublicUriEnabled: function() {
    return utils.getConfigurationPropertyBoolean('allow.ft.hosts.public-address');
  },

  isHostsViewLinksEnabled: function() {
    return utils.getConfigurationPropertyBoolean('allow.ft.hosts.view.links');
  },

  allowHostEventsSubscription: function() {
    return utils.getConfigurationPropertyBoolean('allow.host.events.subscription');
  },

  areFavoriteImagesEnabled: function() {
    return !utils.isVic() && utils.getConfigurationPropertyBoolean('allow.ft.favorite-images');
  }
};

export default ft;
