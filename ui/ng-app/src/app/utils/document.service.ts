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

import { Headers } from '@angular/http';
import { Injectable } from '@angular/core';
import { Ajax } from './ajax.service';
import { ProjectService } from './project.service';
import { FT } from './ft';
import { Utils } from './utils';
import { Links } from './links';
import { URLSearchParams } from '@angular/http';
import { searchConstants, serviceUtils } from 'admiral-ui-common';

const FILTER_VALUE_ALL_FIELDS = 'ALL_FIELDS';
const HEADER_PROJECT = "x-project";

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

  var wildCard = queryOptions._strictFilter ? '' : '*';
  delete queryOptions._strictFilter;

  var anyArray = toArrayIfDefined(queryOptions.any);
  if (anyArray) {
    newQueryOptions[FILTER_VALUE_ALL_FIELDS] = [];
    for (let i = 0; i < anyArray.length; i++) {
      newQueryOptions[FILTER_VALUE_ALL_FIELDS].push({
        val: wildCard + anyArray[i].toLowerCase() + wildCard,
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
            val: wildCard + valArray[i].toLowerCase() + wildCard,
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

  constructor(public ajax: Ajax, private projectService: ProjectService) {
  }

  public list(factoryLink: string, queryOptions: any,
              projectLink?: string): Promise<DocumentListResult> {
    let params = new URLSearchParams();
    // params.set('$limit', serviceUtils.calculateLimit().toString());
    params.set('$limit', PAGE_LIMIT);
    params.set('expand', 'true');
    params.set('documentType', 'true');
    params.set('$count', 'true');

    if (queryOptions) {
      let filter = getFilter(queryOptions);
      if(filter){
        params.set('$filter', filter);
      }
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
      op = this.ajax.get(factoryLink, params, undefined, this.buildHeaders(projectLink));
    }

    return op.then(result => {
      let documents = result.documentLinks.map(link => {
        let document = result.documents[link];
        document.documentId = Utils.getDocumentId(link);

        return document;
      });

      return new DocumentListResult(documents, result.nextPageLink, result.totalCount);

    }).then(result => slowPromise(result));
  }

  public loadNextPage(nextPageLink, projectLink?: string): Promise<DocumentListResult> {

    return this.ajax.get(nextPageLink, undefined, undefined, this.buildHeaders(projectLink))
      .then(result => {

        let documents = result.documentLinks.map(link => {
          let document = result.documents[link];
          document.documentId = Utils.getDocumentId(link);

          return document;
        });

        return new DocumentListResult(documents, result.nextPageLink, result.totalCount);

      }).then(result => slowPromise(result));
  }

  public get(documentSelfLink, expand: boolean = false, projectLink?: string): Promise<any> {
    if (expand) {
      let params = new URLSearchParams();
      params.set('expand', 'true');

      return this.ajax.get(documentSelfLink, params, undefined, this.buildHeaders(projectLink));
    }

    return this.ajax.get(documentSelfLink, undefined, undefined, this.buildHeaders(projectLink));
  }

   public getById(factoryLink: string, documentId: string, projectLink?: string): Promise<any> {
    let documentSelfLink = factoryLink + '/' + documentId;

    return this.get(documentSelfLink, true, projectLink);
  }

  public getByCriteria(factoryLink: string, searchParams: URLSearchParams): Promise<any> {
    return this.ajax.get(factoryLink, searchParams);
  }


  public getLogs(logsServiceLink, id, sinceMs) {
    return new Promise((resolve, reject) => {
      let logRequestUriPath = 'id=' + id;
      if (sinceMs) {
        let sinceSeconds = sinceMs / 1000;
        logRequestUriPath += '&since=' + sinceSeconds;
      }

      this.ajax.get(logsServiceLink, new URLSearchParams(logRequestUriPath), undefined, this.buildHeaders())
        .then((logServiceState) => {
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

  public patch(documentSelfLink, patchBody, projectLink?: string): Promise<any> {
    return this.ajax.patch(documentSelfLink, undefined, patchBody, this.buildHeaders(projectLink));
  }

  public post(factoryLink, postBody, projectLink?: string): Promise<any> {
    return this.ajax.post(factoryLink, undefined, postBody, this.buildHeaders(projectLink));
  }

  public postWithHeader(factoryLink, postBody, headers: Headers): Promise<any> {
    return this.ajax.post(factoryLink, null, postBody, headers);
  }

  public put(documentSelfLink, putBody, projectLink?: string): Promise<any> {
    return this.ajax.put(documentSelfLink, undefined, putBody, this.buildHeaders(projectLink));
  }

  public delete(documentSelfLink, projectLink?: string): Promise<any> {
    return this.ajax.delete(documentSelfLink, undefined, undefined, this.buildHeaders(projectLink));
  }

  public listProjects() {
    if (FT.isApplicationEmbedded()) {
      return this.ajax.get(Links.GROUPS, null).then(result => {
        let documents = result || [];
        return new DocumentListResult(documents, result.nextPageLink, result.totalCount);
      });
    } else {
      return this.list(Links.PROJECTS, null);
    }
  }

  public listPksClusters(params) {
      return this.ajax.get(Links.PKS_CLUSTERS, params).then(result => {
          let documents = result || [];

          return new DocumentListResult(documents, result.nextPageLink, result.totalCount);
      });
  }

  private buildHeaders(projectLink?: string): Headers {
    if (!this.projectService && !projectLink) {
      return undefined;
    }

    let selectedProject = this.projectService.getSelectedProject();

    let calculateHeaders = function(projectId) {
      if (!projectId || /^\s*$/.test(projectId)) {
        return undefined;
      }

      let headers = new Headers();
      headers.append(HEADER_PROJECT, projectId);

      return headers;
    };

    if (FT.isApplicationEmbedded()) {
      if (!selectedProject) {
        return undefined;
      }
      return calculateHeaders(selectedProject.id);
    } else {
      if (!projectLink) {
        if (selectedProject) {
          projectLink = selectedProject.documentSelfLink;
        } else {
          return undefined;
        }
      }

      return calculateHeaders(projectLink);
    }
  }
}

export class DocumentListResult {

  constructor(public documents : Array<any>,
              public nextPageLink: string,
              public totalCount: number) {

  }
}
