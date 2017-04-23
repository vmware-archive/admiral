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
}

let slowPromise = function<T>(result: T):Promise<T> {
  return new Promise<T>((resolve, reject) => {
    setTimeout(() => {
      resolve(result);
    }, 1000);
  });
}

@Injectable()
export class DocumentService {

  constructor(private ajax: Ajax) { }

  public list(factoryLink: string, queryOptions: any): Promise<DocumentListResult> {
    let params = new URLSearchParams();
    // params.set('$limit', serviceUtils.calculateLimit().toString());
    params.set('$limit', '10');
    params.set('expand', 'true');
    params.set('$count', 'true');

    if (queryOptions) {
      params.set('$filter', getFilter(queryOptions));
    }

    return this.ajax.get(factoryLink, params).then(result => {
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
        let document = result.documents[link]
        document.documentId = Utils.getDocumentId(link);
        return document;
      });
      return new DocumentListResult(documents, result.nextPageLink, result.totalCount);
    }).then(result => slowPromise(result));
  }

  public get(documentSelfLink): Promise<any> {
    return this.ajax.get(documentSelfLink);
  }

   public getById(factoryLink: string, documentId: string): Promise<any> {
    let documentSelfLink = factoryLink + '/' + documentId
    return this.get(documentSelfLink);
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
}

export class DocumentListResult {
  constructor(public documents : Array<any>, public nextPageLink: string, public totalCount: number) {}
}