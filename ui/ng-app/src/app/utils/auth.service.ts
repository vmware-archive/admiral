import { DocumentService } from './document.service';
import { Headers, URLSearchParams, RequestOptions, ResponseContentType } from '@angular/http';
import { Links } from './links';
import { Injectable } from '@angular/core';

@Injectable()
export class AuthService {

  private _cachedSessionContext: any;
  private _initialSessionPromise: Promise<any>;

  constructor(private documentService: DocumentService) {}

  public login(username, password) {
    var data = JSON.stringify({
      requestType: 'LOGIN'
    });

    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa(username + ':' + password));

    return this.documentService.postWithHeader(Links.BASIC_AUTH, data, headers);
  }

  public logout(): Promise<any> {
    let requestOptions = new RequestOptions({
      url: Links.AUTH_LOGOUT,
      method: 'GET',
      responseType: ResponseContentType.Text,
      withCredentials: true
    });

    return this.documentService.ajax.ajaxRaw(requestOptions)
      .then(result => {
        return result.headers.get('location');
      });
  }

  public loadCurrentUserSecurityContext(): Promise<any> {
    return this.documentService.get(Links.USER_SESSION);
  }

  public getCachedSecurityContext(): Promise<any> {
    if (!this._initialSessionPromise) {
      this._initialSessionPromise = new Promise((resolve) => {
        this.loadCurrentUserSecurityContext().then((securityContext) => {
          this._cachedSessionContext = securityContext;
          resolve(this._cachedSessionContext);
        });
      });
    }

    if (!this._cachedSessionContext) {
      return this._initialSessionPromise;
    } else {
      return new Promise((resolve, reject) => {
        resolve(this._cachedSessionContext);
      });
    }
  }

  public getPrincipalById(principalId): Promise<any> {
    return this.documentService.getById(Links.AUTH_PRINCIPALS, principalId)
  }

  public findPrincipals(searchString, includeRoles): Promise<any> {
      return new Promise((resolve, reject) => {
          let searchParams = new URLSearchParams();
          searchParams.append('criteria', searchString);
          if (includeRoles) {
              searchParams.append('roles', 'all');
          }

          this.documentService.getByCriteria(Links.AUTH_PRINCIPALS, searchParams).then((principalsResult) => {
              resolve(principalsResult);
          }).catch(reject);
      });
  }

  public makeCloudAdmin(principalId) {
      let link = Links.AUTH_PRINCIPALS + '/' + principalId + '/roles';
      let patchValue = {
          'add': ['CLOUD_ADMIN']
      };

      return this.documentService.patch(link, patchValue);
  }
}
