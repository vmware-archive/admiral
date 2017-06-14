import { Headers } from '@angular/http';
import { Links } from './links';
import { Injectable } from '@angular/core';
import { Ajax } from './ajax.service';

@Injectable()
export class AuthService {

  constructor(private ajax:Ajax) {}

  public login(username, password) {
    var data = JSON.stringify({
      requestType: 'LOGIN'
    });

    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa(username + ':' + password));

    return this.ajax.post(Links.BASIC_AUTH, null, data, headers);
  }

  public logout() {
    var data = {
      requestType: 'LOGOUT'
    };

    return this.ajax.post(Links.BASIC_AUTH, null, data);
  }
}

