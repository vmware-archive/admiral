/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import * as I18n from 'i18next';
import { Constants } from './constants';
import { ConfigUtils } from './config-utils';
import { FT } from './ft';
import { Roles } from './roles';
import { ProjectService } from './project.service';

const LOGIN_PATH="/login/";

const REGISTRY_SCHEME_REG_EXP = /^(https?):\/\//;

export class Utils {
  public static ERROR_NOT_FOUND = 404;

  public static getHashWithQuery(hash:string, queryOptions:any):string {
    var queryString;
    if (queryOptions) {
      queryString = this.paramsToURI(queryOptions);
    }

    if (queryString) {
      return hash + '?' + queryString;
    } else {
      return hash;
    }
  }

  public static paramsToURI(params) {
    var str = [];
    for (var p in params) {
      if (params.hasOwnProperty(p)) {
        var v = params[p];
        var encodedKey = encodeURI(p);

        if (v instanceof Array) {
          for (var i in v) {
            if (v.hasOwnProperty(i)) {
              str.push(encodedKey + '=' + encodeURI(v[i]));
            }
          }
        } else {
          str.push(encodedKey + '=' + encodeURI(v));
        }
      }
    }

    return str.join('&');
  }

  public static uriToParams(uri) {
    var result = {};
    uri.split('&').forEach(function(part) {
      if (part) {
        var item = part.split('=');
        result[decodeURIComponent(item[0])] = item[1] ? decodeURIComponent(item[1]) : null;
      }
    });
    return result;
  }

  public static getDocumentId(documentSelfLink) {
    if (documentSelfLink) {
      return documentSelfLink.substring(documentSelfLink.lastIndexOf('/') + 1);
    }
  }

  public static initializeConfigurationProperties(props) {
    ConfigUtils.initializeConfigurationProperties(props);
  }

  public static getConfigurationProperty(property) {
    return ConfigUtils.getConfigurationProperty(property);
  }

  public static getConfigurationProperties() {
    return ConfigUtils.getConfigurationProperties();
  }

  public static getConfigurationPropertyBoolean(property) {
    return ConfigUtils.getConfigurationPropertyBoolean(property);
  }

  public static existsConfigurationProperty(property) {
    return ConfigUtils.existsConfigurationProperty(property);
  }

  public static isSingleHostCluster(clusterEntity) {
    return clusterEntity && clusterEntity.type === Constants.hosts.type.VCH;
  }

  public static isPksCluster(cluster) {
    return cluster && cluster.type === "KUBERNETES";
  }

  public static getErrorMessage(err) {
    let errorMessage;

    if (err.status === Utils.ERROR_NOT_FOUND) {
      errorMessage = I18n.t('errors.itemNotFound');
    } else {

      let errorResponse = err._body;
      if (errorResponse) {
        if (errorResponse.errors && errorResponse.errors.length > 0) {
          errorMessage = errorResponse.errors[0].systemMessage || errorResponse.errors[0].message;
        } else if (errorResponse.message) {
          errorMessage = errorResponse.message;
        }
      }

      if (!errorMessage) {
        errorMessage = err.message || err.statusText || err.responseText;
      }

    }

    return {
      _generic: errorMessage
    };
  }

  // The following function returns the logarithm of y with base x
  public static getBaseLog(x, y) {
    return Math.log(y) / Math.log(x);
  }

  public static getMagnitude(bytes) {
    if (bytes < 1) {
      return 0;
    }
    return Math.floor(this.getBaseLog(1024, bytes));
  }

  public static magnitudes = ['', 'K', 'M', 'G', 'T', 'P', 'E', 'Z', 'Y'];

  public static formatBytes(bytes, magnitude) {
    if (bytes == 0) {
      return 0;
    }
    var decimals = 1;
    return parseFloat((bytes / Math.pow(1024, magnitude)).toFixed(decimals));
  }

  public static getObjectPropertyValue(obj, propertyName) {
    let value;

    if (obj && propertyName && obj.hasOwnProperty(propertyName)) {
       value = obj[propertyName];
    }

    return value;
  }

  public static getCustomPropertyValue(customProperties, name) {
    if (!customProperties) {
      return null;
    }

    let value = this.getObjectPropertyValue(customProperties, name);

    return (value === '') ? null : value;
  }

  public static sortObjectArrayByField(arr: any[], fieldName: string): any[] {
    return arr && arr.sort((obj1, obj2) => {
      if (!obj1[fieldName] || !obj2[fieldName]) {
        return 0;
      }

      if (obj1[fieldName] > obj2[fieldName]) {
        return 1;
      }

      if (obj1[fieldName] < obj2[fieldName]) {
        return -1;
      }

      return 0;
    });
  }

  public static getURLParts(url) {
    var noProtocol = false;
    if (url.search(/.*:\/\//) !== 0) {
      url = 'http://' + url;
      noProtocol = true;
    }

    var parser = document.createElement('a');
    parser.href = url;

    var protocol = noProtocol ? '' : parser.protocol.replace(':', '');
    var search = parser.search.replace('?', '');

    var port = parser.port;
    if (port === '0') {
      port = undefined;
    }

    return {
      scheme: protocol,
      host: parser.hostname,
      port: port,
      path: parser.pathname,
      query: search,
      fragment: parser.hash
    };
  }

  public static areSystemScopedCredentials(credentials) {
    if (credentials) {
      let scope = this.getCustomPropertyValue(credentials.customProperties, 'scope');
      return 'SYSTEM' == scope;
    }
    return false;
  }

  public static getHostName(host) {
    if (!host) {
      return null;
    }

    if (host.name) {
      return host.name;
    }

    let customProps = host.customProperties;

    if (customProps) {
      let hostAlias = this.getCustomPropertyValue(customProps, '__hostAlias') ;

      if (hostAlias) {
        return hostAlias;
      }

      let name = this.getCustomPropertyValue(customProps, '__Name');

      if (name) {
        return name;
      }
    }

    var urlParts = this.getURLParts(host.address);
    return urlParts.host;
  }

  public static getCpuPercentage(host, shouldRound) {
    let cpuUsage = this.getCustomPropertyValue(host.customProperties, '__CpuUsage');
    if (cpuUsage) {
      return shouldRound ? Math.floor(cpuUsage) : Math.round(cpuUsage * 100) / 100;
    }
    return 0;
  }

  public static getMemoryPercentage(host, shouldRound) {
    let memTotal = this.getCustomPropertyValue(host.customProperties, '__MemTotal');
    let memAvailable = this.getCustomPropertyValue(host.customProperties, '__MemAvailable');
    if (memTotal && memAvailable) {
      var memoryUsage = memTotal - memAvailable;
      var memoryUsagePct = (memoryUsage / memTotal) * 100;
      return shouldRound ? Math.floor(memoryUsagePct) : Math.round(memoryUsagePct * 100) / 100;
    }
    return 0;
  }

  public static toCredentialViewModel(credential) {
    let credentialViewModel:any = {};

    credentialViewModel.documentSelfLink = credential.documentSelfLink;
    credentialViewModel.name = credential.customProperties
                                    ? credential.customProperties.__authCredentialsName : '';
    if (!credentialViewModel.name) {
        credentialViewModel.name = credential.documentId;
    }

    return credentialViewModel;
    }

  public static isLogin(): boolean {
    return location.pathname.indexOf(LOGIN_PATH) > -1;
  }

  public static getHbrContainerImage(registryAddress, repositoryId, tagId): string {
    registryAddress = registryAddress.replace(REGISTRY_SCHEME_REG_EXP, '');
    let harborImageRef = registryAddress + ':*/' + repositoryId + ':' + tagId;

    return harborImageRef;
  }

  public static isAccessAllowed(securityContext, projectSelfLink, roles): boolean {
    if (!roles) {
      throw new Error("Roles not provided!");
    }

    // allow access to everything when no auth
    if (!securityContext) {
        return true;
    }

    // check for system roles
    let hasSystemRole = Utils.hasSystemRole(securityContext, roles);

    if (hasSystemRole) {
      return true;
    }

    // check for project roles
    let securityContextProjects = securityContext.projects;
    if (securityContextProjects) {
      for (var i = 0; i < securityContextProjects.length; i += 1) {
        let project = securityContextProjects[i];
        let projectRoles = project.roles;
        if (project && projectRoles) {
          for (var j = 0; j < projectRoles.length; j += 1) {
            let role = projectRoles[j];
            if (projectSelfLink) {
              if ((project.documentSelfLink  && project.documentSelfLink === projectSelfLink
                      || project.id && project.id === projectSelfLink)
                  && roles.indexOf(role) > -1) {
                return true;
              }
            } else {
              if (roles.indexOf(role) > -1) {
                return true;
              }
            }
          };
        }
      };
    }

    return false;
  }

  public static getClustersViewRefreshInterval() {
    if (this.existsConfigurationProperty('eventual.clusters.refresh.interval.ms')) {

        return parseInt(this.getConfigurationProperty('eventual.clusters.refresh.interval.ms'), 10)
            || Constants.clusters.DEFAULT_VIEW_REFRESH_INTERVAL;
    }

    return Constants.clusters.DEFAULT_VIEW_REFRESH_INTERVAL;
  }

  public static getClusterRescanInterval() {
    if (this.existsConfigurationProperty('eventual.cluster.rescan.interval.ms')) {

      return parseInt(this.getConfigurationProperty('eventual.cluster.rescan.interval.ms'), 10)
               || Constants.clusters.DEFAULT_RESCAN_INTERVAL;
    }

    return Constants.clusters.DEFAULT_RESCAN_INTERVAL;
  }

  public static getClusterRescanRetriesNumber() {
    if (this.existsConfigurationProperty('eventual.cluster.rescan.retries.number')) {

      return parseInt(this.getConfigurationProperty('eventual.cluster.rescan.retries.number'), 10)
               || Constants.clusters.DEFAULT_RESCAN_RETRIES_NUMBER;
      }

    return Constants.clusters.DEFAULT_RESCAN_RETRIES_NUMBER;
  }

  public static repeat(callScope, callback, callbackArgs, numRetries, retryInterval) {
      let currentRetry = 0;
      let intervalID = setInterval(() => {
          callback.apply(callScope, callbackArgs);

          currentRetry++;
          if (currentRetry === numRetries) {
              clearInterval(intervalID);
          }
      }, retryInterval);
  }

  public static getPathUp(url, tabId) {
      const PATH_UP = '../../';
      const PROJECTS_VIEW_URL_PART = 'projects';

      let path: any[] = [PATH_UP];
      if (url.indexOf(PROJECTS_VIEW_URL_PART) > -1) {
          if (url.indexOf(tabId) < 0) {
              // Preselect tab in project details view
              path = [PATH_UP + tabId];
          }
      }

      return path;
  }

  public static serviceUrl(path) {
    let wnd:any = window;
    if (wnd.getBaseServiceUrl) {
      return wnd.getBaseServiceUrl(path);
    }
    return path;
  }

  public static isContainerDeveloper(securityContext) {
    return securityContext && securityContext.indexOf(Roles.VRA_CONTAINER_DEVELOPER) > -1 &&
                securityContext.indexOf(Roles.VRA_CONTAINER_ADMIN) == -1;
  }

  public static hasSystemRole(securityContext, roles) {
    let securityContextRoles = FT.isApplicationEmbedded() ? securityContext : securityContext.roles;

    if (!securityContextRoles || !roles) {
      return false;
    }

    for (var i = 0; i < roles.length; i += 1) {
      let role = roles[i];
      if (securityContextRoles && securityContextRoles.indexOf(role) > -1) {
          return true;
      }
    }

    return false;
  }

  public static subscribeForProjectChange(projectService: ProjectService, callback) {
      let changedProjectLink;

      projectService.activeProject.subscribe((value) => {
        if (value && value.documentSelfLink) {
            changedProjectLink = value.documentSelfLink;
        } else if (value && value.id) {
            changedProjectLink = value.id;
        } else {
            changedProjectLink = undefined;
        }

        callback(changedProjectLink);
      });
  }
}

export class CancelablePromise<T> {
  private wrappedPromise: Promise<T>;
  private isCanceled;

  constructor(promise: Promise<T>) {
    this.wrappedPromise = new Promise((resolve, reject) => {
      promise.then((val) =>
        this.isCanceled ? reject({isCanceled: true}) : resolve(val)
      );
      promise.catch((error) =>
        this.isCanceled ? reject({isCanceled: true}) : reject(error)
      );
    });
  }

  getPromise(): Promise<T> {
    return this.wrappedPromise;
  }

  cancel() {
    this.isCanceled = true;
  }
}
