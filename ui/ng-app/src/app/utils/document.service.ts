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
import { Links } from './links';
import { URLSearchParams } from '@angular/http';

@Injectable()
export class DocumentService {

  constructor(private ajax: Ajax) { }

  public list(link : string): Promise<Array<any>> {
    return this.ajax.get(link, new URLSearchParams('expand=true')).then(result => {
      return result.documentLinks.map(link => {
        var document = result.documents[link]
        document.documentId = this.getDocumentId(link);
        return document;
      });
    });
  }

  public get(documentSelfLink): Promise<any> {
    return this.ajax.get(documentSelfLink);
  }

   public getById(link: string, documentId: string): Promise<any> {
    var documentSelfLink = link + '/' + documentId
    return this.get(documentSelfLink);
  }


  public getLogs(id, sinceMs) {
    return new Promise((resolve, reject) => {
      var logRequestUriPath = 'id=' + id;
      if (sinceMs) {
        var sinceSeconds = sinceMs / 1000;
        logRequestUriPath += '&since=' + sinceSeconds;
      }

      this.ajax.get(Links.CONTAINER_LOGS, new URLSearchParams(logRequestUriPath)).then((logServiceState) => {
        if (logServiceState && logServiceState.logs) {
          var decodedLogs = atob(logServiceState.logs);
          resolve(decodedLogs);
        } else {
          resolve('');
        }
      }).catch(reject);
    });
  }

  private getDocumentId(documentSelfLink) {
    if (documentSelfLink) {
      return documentSelfLink.substring(documentSelfLink.lastIndexOf('/') + 1);
    }
  }
}