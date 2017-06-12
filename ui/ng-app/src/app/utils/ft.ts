

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

import { Utils } from './utils';

var isDocsAvailable = false;


/**
 * General feature toggle utility
 */
export class FT {
  public static setDocsAvailable(value) {
    isDocsAvailable = value;
  }

  public static isContextAwareHelpEnabled() {
   return Utils.getConfigurationPropertyBoolean('allow.ensemble.help');
  }

  public static isContextAwareHelpAvailable() {
   return this.isContextAwareHelpEnabled() && isDocsAvailable;
  }

  public static isNimbusEnabled() {
    return Utils.getConfigurationPropertyBoolean('allow.nimbus');
  }

  public static isExternalPhotonAdaptersEnabled() {
    return Utils.getConfigurationPropertyBoolean('allow.external.photon.adapters');
  }

  public static isVchHostOptionEnabled() {
    return Utils.getConfigurationPropertyBoolean('allow.ft.host-option.vch');
  }

  public static isKubernetesHostOptionEnabled() {
    return Utils.getConfigurationPropertyBoolean('allow.ft.host-option.kubernetes');
  }

  public static isCreateHostOptionEnabled() {
    return Utils.getConfigurationPropertyBoolean('allow.ft.host-option.create');
  }

  public static areClosuresAllowed() {
    return Utils.getConfigurationPropertyBoolean('allow.closures');
  }

  public static showProjectsInNavigation() {
    return Utils.getConfigurationPropertyBoolean('allow.ft.projects.in.navigation');
  }

  public static isPublicKeyCredentialsDisabled() {
    return Utils.getConfigurationPropertyBoolean('disable.credentials.publicKey');
  }

  public static isRequestGraphEnabled() {
    return Utils.getConfigurationPropertyBoolean('allow.ft.request-graph');
  }

  public static isApplicationEmbedded() {
    return Utils.getConfigurationPropertyBoolean('embedded');
  }

  public static isHbrEnabled() {
    return Utils.getConfigurationPropertyBoolean('harbor.tab.url') && this.isApplicationEmbedded();
  }
};
