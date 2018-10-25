/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { ConfigUtils } from './config-utils';

var isDocsAvailable = false;


/**
 * General feature toggle utility
 */
export class FT {
  public static setDocsAvailable(value) {
    isDocsAvailable = value;
  }

  public static isContextAwareHelpEnabled() {
   return ConfigUtils.getConfigurationPropertyBoolean('allow.ensemble.help');
  }

  public static isContextAwareHelpAvailable() {
   return this.isContextAwareHelpEnabled() && isDocsAvailable;
  }

  public static isNimbusEnabled() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.nimbus');
  }

  public static isExternalPhotonAdaptersEnabled() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.external.photon.adapters');
  }

  public static isVchHostOptionEnabled() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.ft.host-option.vch');
  }

  public static isExternalKubernetesEnabled() {
    return this.isPksEnabled()
        && ConfigUtils.getConfigurationPropertyBoolean('allow.ft.host-option.kubernetes');
  }

  public static isCreateHostOptionEnabled() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.ft.host-option.create');
  }

  public static areClosuresAllowed() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.closures');
  }

  public static showProjectsInNavigation() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.ft.projects.in.navigation');
  }

  public static isPublicKeyCredentialsDisabled() {
    return ConfigUtils.getConfigurationPropertyBoolean('disable.credentials.publicKey');
  }

  public static isRequestGraphEnabled() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.ft.request-graph');
  }

  public static isApplicationEmbedded() {
    return ConfigUtils.getConfigurationPropertyBoolean('embedded');
  }

  public static isVca() {
    return ConfigUtils.getConfigurationPropertyBoolean('vca');
  }

  public static isVra() {
      return FT.isApplicationEmbedded() && !FT.isVca();
  }

  public static isHbrEnabled() {
    return !this.isApplicationEmbedded()
                && ConfigUtils.getConfigurationProperty('harbor.tab.url');
  }

  public static isVic() {
    return ConfigUtils.getConfigurationPropertyBoolean('vic');
  }

  public static isProfilesSplitEnabled() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.ft.profiles.split');
  }

  public static isHostPublicUriEnabled() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.ft.hosts.public-address');
  }

  public static isHostsViewLinksEnabled() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.ft.hosts.view.links');
  }

  public static showHostsView() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.ft.hosts.view');
  }

  public static allowHostEventsSubscription() {
    return ConfigUtils.getConfigurationPropertyBoolean('allow.host.events.subscription');
  }

  public static isPksEnabled() {
      return !this.isVic() && ConfigUtils.getConfigurationPropertyBoolean('allow.ft.pks');
  }
};
