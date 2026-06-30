import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';
import {Router} from '@angular/router';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class KeycloakService {

  private _keycloak: Keycloak | undefined;

  constructor(
    private router: Router,
    private http: HttpClient
  ) {
  }

  get keycloak() {
    if (!this._keycloak) {
      this._keycloak = new Keycloak({
        url: environment.keycloakUrl,
        realm: 'wacchat',
        clientId: 'wacchat-app'
      });
    }
    return this._keycloak;
  }

  async init() {
    const authenticated = await this.keycloak.init({
      onLoad: 'login-required',
      checkLoginIframe: false,
    });
  }

  async login() {
    await this.keycloak.login();
  }

  get userId(): string {
    return this.keycloak?.tokenParsed?.sub as string;
  }

  get isTokenValid(): boolean {
    return !this.keycloak.isTokenExpired();
  }

  get fullName(): string {
    return this.keycloak.tokenParsed?.['name'] as string;
  }

  async logout() {
    try {
      await firstValueFrom(this.http.delete<void>('/api/v1/users/me/session'));
    } catch {
      // best-effort: proceed with logout even if the backend call fails
    }
    return this.keycloak.logout({redirectUri: environment.appUrl});
  }

  accountManagement() {
    return this.keycloak.accountManagement();
  }
}
