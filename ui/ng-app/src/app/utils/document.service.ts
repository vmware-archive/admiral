/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Injectable } from '@angular/core';
import { AjaxService } from './ajax.service';
import { ProjectService } from './project.service';
import { Constants } from './constants';
import { FT } from './ft';
import { Links } from './links';
import { Utils } from './utils';

const FILTER_VALUE_ALL_FIELDS = 'ALL_FIELDS';

let toArrayIfDefined = function(obj) {
    if (!obj) {
        return null;
    }
    if (obj.constructor === Array) {
        return obj;
    }
    if (obj) {
        return [obj];
    }
};

let getFilter = function(queryOptions: any): string {
    let newQueryOptions = {};
    newQueryOptions[Constants.SEARCH.OCCURRENCE.PARAM] =
                    queryOptions[Constants.SEARCH.OCCURRENCE.PARAM];

    let wildCard = queryOptions._strictFilter ? '' : '*';
    delete queryOptions._strictFilter;

    let anyArray = toArrayIfDefined(queryOptions.any);
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
        if (queryOptions.hasOwnProperty(key)) {

            if (key !== Constants.SEARCH.OCCURRENCE.PARAM
                && key !== Constants.SEARCH.OCCURRENCE.ANY) {
                let valArray = toArrayIfDefined(queryOptions[key]);

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
    }

    return buildOdataQuery(newQueryOptions);
};

const DEFAULT_LIMIT = 10;

let calculateLimit = function() {
    let averageSize = 250;
    let body = document.body;
    let html = document.body;

    let h = Math.max(body.scrollHeight, body.offsetHeight,
        html.clientHeight, html.scrollHeight, html.offsetHeight);
    let w = Math.max(body.scrollWidth, body.offsetWidth,
        html.clientHeight, html.scrollWidth, html.offsetWidth);

    return Math.ceil(w / averageSize * h / averageSize) || DEFAULT_LIMIT;
};

let encodeQuotes = function(value) {
    return value.replace(/\'/g, '%2527');
};

/**
 * Simple ODATA query builder.
 */
let buildOdataQuery = function(queryOptions) {
    let result = '';
    if (queryOptions) {
        let occurrence = queryOptions[Constants.SEARCH.OCCURRENCE.PARAM];
        delete queryOptions[Constants.SEARCH.OCCURRENCE.PARAM];

        let operator = occurrence === Constants.SEARCH.OCCURRENCE.ANY ? 'or' : 'and';

        for (let key in queryOptions) {
            if (queryOptions.hasOwnProperty(key)) {
                let query = queryOptions[key];

                if (query) {
                    for (let i = 0; i < query.length; i++) {
                        if (result.length > 0) {
                            result += ' ' + operator + ' ';
                        }

                        result += key + ' ' + query[i].op + ' \'' + encodeQuotes(query[i].val) + '\'';
                    }
                }
            }
        }
    }
    return result;
};

let slowPromise = function<T>(result: T): Promise<T> {
    return new Promise<T>((resolve, reject) => {
        resolve(result);
        // setTimeout(() => {
        //   resolve(result);
        // }, 1000);
    });
};

const PAGE_LIMIT: string = '50';

/**
 * The document service provides data manipulation and retrieval methods.
 */
@Injectable()
export class DocumentService {

    constructor(public ajax: AjaxService, private projectService: ProjectService) {
        //
    }

    public list(factoryLink: string, queryOptions: any, projectLink?: string,
                skipProjectHeader: boolean = false): Promise<DocumentListResult> {
        let params = {
            '$limit': [calculateLimit().toString(), PAGE_LIMIT],
            'expand': 'true',
            'documentType': 'true',
            '$count': 'true'
        };

        if (queryOptions) {
            let filter = getFilter(queryOptions);
            if (filter) {
                params['$filter'] = filter;
            }
        }

        let op;
        // 2000 is th max url length. Here we don't know the hostname, so 50 chars extra
        if (params.toString().length + factoryLink.length > 1950) {
            let data = {
                uri: factoryLink + '?' + params.toString()
            };
            factoryLink = Links.LONG_URI_GET;

            op = this.post(factoryLink, data, undefined, skipProjectHeader);
        } else {
            op = this.ajax.get(factoryLink, params, undefined,
                        !skipProjectHeader ? this.buildProjectHeader(projectLink): undefined);
        }

        return op.then(result => {
            let documents = result.documentLinks && result.documentLinks.map(link => {
                let document = result.documents[link];
                document.documentId = Utils.getDocumentId(link);

                return document;
            });

            return new DocumentListResult(documents, result.nextPageLink, result.totalCount,
                result.documentCount);

        }).then(result => slowPromise(result));
    }

    public loadNextPage(nextPageLink, projectLink?: string): Promise<DocumentListResult> {
        return this.ajax.get(nextPageLink, undefined, undefined,
            this.buildProjectHeader(projectLink))
        .then(result => {

            let documents = result.documentLinks.map(link => {
                let document = result.documents[link];
                document.documentId = Utils.getDocumentId(link);

                return document;
            });

            return new DocumentListResult(documents, result.nextPageLink, result.totalCount,
                result.documentCount);

        }).then(result => slowPromise(result));
    }

    public get(documentSelfLink, expand: boolean = false, projectLink?: string): Promise<any> {
        if (expand) {
            let params = {'expand': 'true'};

            return this.ajax.get(documentSelfLink, params, undefined,
                                this.buildProjectHeader(projectLink));
        }

        return this.ajax.get(documentSelfLink, undefined, undefined,
                            this.buildProjectHeader(projectLink));
    }

    public getById(factoryLink: string, documentId: string, projectLink?: string): Promise<any> {
        let documentSelfLink = factoryLink + '/' + documentId;

        return this.get(documentSelfLink, true, projectLink);
    }

    public getByCriteria(factoryLink: string, searchParams: any): Promise<any> {
        return this.ajax.get(factoryLink, searchParams);
    }

    public getLogs(logsServiceLink, id, sinceMs) {
        return new Promise((resolve, reject) => {

            let params = {'id': id};
            if (sinceMs) {
                let sinceSeconds = sinceMs / 1000;
                params['since'] = '' + sinceSeconds;
            }

            this.ajax.get(logsServiceLink, params, undefined, this.buildProjectHeader())
            .then((logServiceState) => {
                if (logServiceState) {

                    if (logServiceState.logs) {
                        let decodedLogs = atob(logServiceState.logs);

                        resolve(decodedLogs);
                    } else {
                        for (let component in logServiceState) {
                            if (logServiceState.hasOwnProperty(component)) {
                                logServiceState[component] = atob(logServiceState[component].logs);
                            }
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
        return this.ajax.patch(documentSelfLink, undefined, patchBody,
                                this.buildProjectHeader(projectLink));
    }

    public post(factoryLink, postBody, projectLink?: string,
                skipProjectHeader: boolean = false): Promise<any> {
        return this.ajax.post(factoryLink, undefined, postBody,
                        !skipProjectHeader
                                ? this.buildProjectHeader(projectLink) : undefined);
    }

    public postWithHeader(factoryLink, postBody, headers: any): Promise<any> {
        return this.ajax.post(factoryLink, null, postBody, headers);
    }

    public put(documentSelfLink, putBody, projectLink?: string): Promise<any> {
        return this.ajax.put(documentSelfLink, undefined, putBody,
            this.buildProjectHeader(projectLink));
    }

    public delete(documentSelfLink, projectLink?: string): Promise<any> {
        return this.ajax.delete(documentSelfLink, undefined, undefined,
            this.buildProjectHeader(projectLink));
    }

    public listProjects() {
        if (FT.isVra()) {
            return this.ajax.get(Links.GROUPS, null).then(result => {
                let documents = result || [];

                return new DocumentListResult(documents, result.nextPageLink, result.totalCount,
                    result.documentCount);
            });
        } else {
            return this.list(Links.PROJECTS, null);
        }
    }

    public listWithParams(factoryLink, params, projectLink?: string) {
        return this.ajax.get(factoryLink, params, undefined, this.buildProjectHeader(projectLink))
            .then(result => {
                let documents = result || [];

                return new DocumentListResult(documents, result.nextPageLink, result.totalCount,
                    result.documentCount);
            });
    }

    private buildProjectHeader(projectLink?: string): any {
        if (!this.projectService && !projectLink) {
            return undefined;
        }

        let selectedProject = this.projectService.getSelectedProject();

        let calculateHeaders = function(projectId) {
            if (!projectId || /^\s*$/.test(projectId)) {
                return undefined;
            }

            return { 'x-project': projectId };
        };

        if (FT.isVra()) {
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

/**
 * Paged result data.
 */
export class DocumentListResult {
    constructor(public documents: Array<any>, public nextPageLink: string,
                public totalCount: number, public documentCount: number) {
        //
    }
}
