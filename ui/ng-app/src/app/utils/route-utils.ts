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

export class RouteUtils {
  public static toFormerViewPath(path, formerViewPaths: Array<FormerViewPathBridge>): string {
    let pathBridge:FormerViewPathBridge = formerViewPaths.find((v) => {
      return v.matchesPath(path);
    });

    if (pathBridge) {
      return pathBridge.toFormerViewPath(path);
    }
  }

  public static fromFormerViewPath(formerPath, formerViewPaths: Array<FormerViewPathBridge>): string {
    let pathBridge:FormerViewPathBridge = formerViewPaths.find((v) => {
      return v.matchesFormerPath(formerPath);
    });

    if (pathBridge) {
      return pathBridge.toPath(formerPath);
    }
  }
}

export class FormerViewPathBridge {
  private _path: string;
  private _formerPath: string;
  private _formerQuery: string;

  constructor(path: string, formerPath: string, formerQuery?: string) {
    this._path = path;
    this._formerPath = formerPath;
    this._formerQuery = formerQuery;
  }

  get path():string {
    return this._path;
  }

  get formerPath():string {
    return this._formerPath;
  }

  get formerQuery():string {
    return this._formerQuery;
  }

  matchesPath(path):boolean {
    return path.startsWith(this.path);
  }

  matchesFormerPath(formerViewPath):boolean {
    return formerViewPath.startsWith(this.formerPath) &&
      (!this.formerQuery || formerViewPath.indexOf(this.formerQuery) != -1);
  }

  toFormerViewPath(path) {
    let formerViewPath = path.replace(this.path, this.formerPath);
    if (this.formerQuery) {
      if (formerViewPath.indexOf('?') != -1) {
        formerViewPath += '&';
      } else {
        formerViewPath += '?'
      }
      formerViewPath += this.formerQuery;
    }
    return formerViewPath;
  }

  toPath(formerViewPath) {
    let path:string = formerViewPath.replace(this.formerPath, this.path);

    let queryIndex = path.indexOf('?');
    if (this.formerQuery && queryIndex != -1) {
      var queryPart = path.substring(queryIndex + 1);
      queryPart = queryPart.replace(this.formerQuery, '');
      if (queryPart.startsWith('&')) {
        queryPart = queryPart.substring(1);
      }

      path = path.substring(0, queryIndex);
      if (queryPart.length > 0) {
        path += '?' + queryPart;
      }
    }
    return path;
  }
}

