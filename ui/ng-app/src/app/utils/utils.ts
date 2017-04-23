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

export class Utils {
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