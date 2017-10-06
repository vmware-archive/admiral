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
import { constants } from './constants';
import { FT } from './ft';

const LOGIN_PATH="/login/"

const REGISTRY_SCHEME_REG_EXP = /^(https?):\/\//;

export class Utils {
  public static ERROR_NOT_FOUND = 404;

  private static configurationProperties;

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
    if (this.configurationProperties) {
      throw new Error('Properties already set');
    }
    this.configurationProperties = props;
  }

  public static getConfigurationProperty(property) {
    return this.configurationProperties && this.configurationProperties[property];
  }

  public static getConfigurationProperties() {
    return this.configurationProperties;
  }

  public static getConfigurationPropertyBoolean(property) {
    return this.configurationProperties && this.configurationProperties[property] === 'true';
  }

  public static existsConfigurationProperty(property) {
    return this.configurationProperties.hasOwnProperty(property);
  }

  public static isSingleHostCluster(clusterEntity) {
    return clusterEntity && clusterEntity.type === constants.hosts.type.VCH;
  }

  public static isCompute() {
    var locationHash = window.location.hash;
    return locationHash.startsWith('#/compute');
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

  public static isLogin(): boolean {
    return location.pathname.indexOf(LOGIN_PATH) > -1;
  }

  public static getHbrContainerImage(registryAddress, repositoryId, tagId): string {
    registryAddress = registryAddress.replace(REGISTRY_SCHEME_REG_EXP, '');
    let harborImageRef = registryAddress + ':*/' + repositoryId + ':' + tagId;

    return harborImageRef;
  }

  public static isAccessAllowed(securityContext, projectSelfLink, roles): boolean {
    if (FT.isApplicationEmbedded()) {
      return true;
    }

    if (!roles) {
      throw new Error("Roles not provided!");
    }

    if (!securityContext) {
        return false;
    }

    // check for system roles
    if (securityContext.roles) {

      securityContext.roles.forEach(role => {
        if (roles.indexOf(role) > -1) {
          return true;
        }
      });
    }

    // check for project roles
    if (securityContext.projects) {

      securityContext.projects.forEach(project => {
        if (project && project.roles) {

          project.roles.forEach(role => {
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
          });
        }
      });
    }

    return false;
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
