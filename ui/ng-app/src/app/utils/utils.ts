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

  public static getConfigurationPropertyBoolean(property) {
    return this.configurationProperties && this.configurationProperties[property] === 'true';
  }

  public static existsConfigurationProperty(property) {
    return this.configurationProperties.hasOwnProperty(property);
  }

  public static getErrorMessage(err) {
    let errorMessage;

    if (err.status === Utils.ERROR_NOT_FOUND) {
      errorMessage = I18n.t('errors.itemNotFound');
    } else {

      let errorResponse = err._body;
      if (errorResponse) {
        if (errorResponse.errors && errorResponse.errors.length > 0) {
          errorMessage = errorResponse.errors[0].systemMessage;
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
    var decimals = 2;
    return parseFloat((bytes / Math.pow(1024, magnitude)).toFixed(decimals));
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