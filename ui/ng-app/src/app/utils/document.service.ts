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

import { Injectable } from '@angular/core';
import { Ajax } from './ajax.service';
import { Utils } from './utils';
import { Links } from './links';
import { URLSearchParams } from '@angular/http';
import { searchConstants, serviceUtils} from 'admiral-ui-common';

const FILTER_VALUE_ALL_FIELDS = 'ALL_FIELDS';

let toArrayIfDefined = function(obj) {
  if (!obj) {
    return null;
  }
  if (obj.constructor === Array) {
    return obj;
  } if (obj) {
    return [obj];
  }
};

let getFilter = function(queryOptions: any): string {
  let newQueryOptions = {};
  newQueryOptions[searchConstants.SEARCH_OCCURRENCE.PARAM] = queryOptions[searchConstants.SEARCH_OCCURRENCE.PARAM];

  var anyArray = toArrayIfDefined(queryOptions.any);
  if (anyArray) {
    newQueryOptions[FILTER_VALUE_ALL_FIELDS] = [];
    for (let i = 0; i < anyArray.length; i++) {
      newQueryOptions[FILTER_VALUE_ALL_FIELDS].push({
        val: '*' + anyArray[i].toLowerCase() + '*',
        op: 'eq'
      });
    }
  }

  for (let key in queryOptions) {
    if (key !== searchConstants.SEARCH_OCCURRENCE.PARAM &&
      key !== 'any') {
      var valArray = toArrayIfDefined(queryOptions[key]);
      if (valArray) {
        newQueryOptions[key] = [];
        for (let i = 0; i < valArray.length; i++) {
          newQueryOptions[key].push({
            val: '*' + valArray[i].toLowerCase() + '*',
            op: 'eq'
          });
        }
      }
    }
  }

  return serviceUtils.buildOdataQuery(newQueryOptions);
};

let slowPromise = function<T>(result: T):Promise<T> {
  return new Promise<T>((resolve, reject) => {
    resolve(result);
    // setTimeout(() => {
    //   resolve(result);
    // }, 1000);
  });
};

const PAGE_LIMIT : string = '50';

@Injectable()
export class DocumentService {

  constructor(private ajax: Ajax) { }

  public list(factoryLink: string, queryOptions: any): Promise<DocumentListResult> {
    let params = new URLSearchParams();
    // params.set('$limit', serviceUtils.calculateLimit().toString());
    params.set('$limit', PAGE_LIMIT);
    params.set('expand', 'true');
    params.set('documentType', 'true');
    params.set('$count', 'true');

    if (queryOptions) {
      params.set('$filter', getFilter(queryOptions));
    }
    let op;
    // 2000 is th max url length. Here we don't know the hostname, so 50 chars extra
    if (params.toString().length + factoryLink.length > 1950) {
      let data = {
        uri: factoryLink + '?' + params.toString()
      };
      factoryLink = Links.LONG_URI_GET;

      op = this.post(factoryLink, data);
    } else {
      op = this.ajax.get(factoryLink, params);
    }

    return op.then(result => {
      let documents = result.documentLinks.map(link => {
        let document = result.documents[link]
        document.documentId = Utils.getDocumentId(link);
        return document;
      });
      return new DocumentListResult(documents, result.nextPageLink, result.totalCount);
    }).then(result => slowPromise(result));
  }

  public loadNextPage(nextPageLink): Promise<DocumentListResult> {
    return this.ajax.get(nextPageLink).then(result => {
      let documents = result.documentLinks.map(link => {
        let document = result.documents[link];
        document.documentId = Utils.getDocumentId(link);
        return document;
      });
      return new DocumentListResult(documents, result.nextPageLink, result.totalCount);
    }).then(result => slowPromise(result));
  }

  public get(documentSelfLink, expand: boolean = false): Promise<any> {
    if (expand) {
      let params = new URLSearchParams();
      params.set('expand', 'true');

      return this.ajax.get(documentSelfLink, params);
    }

    return this.ajax.get(documentSelfLink);
  }

   public getById(factoryLink: string, documentId: string): Promise<any> {
    let documentSelfLink = factoryLink + '/' + documentId
    return this.get(documentSelfLink, true);
  }


  public getLogs(logsServiceLink, id, sinceMs) {
    return new Promise((resolve, reject) => {
      let logRequestUriPath = 'id=' + id;
      if (sinceMs) {
        let sinceSeconds = sinceMs / 1000;
        logRequestUriPath += '&since=' + sinceSeconds;
      }

      this.ajax.get(logsServiceLink, new URLSearchParams(logRequestUriPath)).then((logServiceState) => {
        if (logServiceState) {
          if (logServiceState.logs) {
            let decodedLogs = atob(logServiceState.logs);
            resolve(decodedLogs);
          } else {
            for (let component in logServiceState) {
              logServiceState[component] = atob(logServiceState[component].logs);
            }
            resolve(logServiceState);
          }
        } else {
          resolve('');
        }
      }).catch(reject);
    });
  }

  public patch(documentSelfLink, patchBody): Promise<any> {
    return this.ajax.patch(documentSelfLink, null, patchBody);
  }

  public post(factoryLink, postBody): Promise<any> {
    return this.ajax.post(factoryLink, null, postBody);
  }

  public put(documentSelfLink, putBody): Promise<any> {
    return this.ajax.put(documentSelfLink, null, putBody);
  }

  public delete(documentSelfLink): Promise<any> {
    return this.ajax.delete(documentSelfLink);
  }

  public loadCurrentUserSecurityContext(): Promise<any> {
    return this.ajax.get(Links.USER_SESSION);
  }

  public getPrincipalById(principalId): Promise<any> {
    return this.getById(Links.AUTH_PRINCIPALS, principalId)
  }

  public findPrincipals(searchString, includeRoles): Promise<any> {
      return new Promise((resolve, reject) => {
          let searchParams = new URLSearchParams();
          searchParams.append('criteria', searchString);
          if (includeRoles) {
              searchParams.append('roles', 'all');
          }

          this.ajax.get(Links.AUTH_PRINCIPALS, searchParams).then((principalsResult) => {
              resolve(principalsResult);
          }).catch(reject);
      });
  }
}

export class DocumentListResult {

  constructor(public documents : Array<any>,
              public nextPageLink: string,
              public totalCount: number) {

  }
}
