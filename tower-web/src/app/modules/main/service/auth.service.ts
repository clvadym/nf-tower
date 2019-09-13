/*
 * Copyright (c) 2019, Seqera Labs.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
import { Injectable } from '@angular/core';
import {BehaviorSubject, Observable, of, Subject} from "rxjs";
import {map, mergeMap, tap} from "rxjs/operators";
import {User} from "../entity/user/user";
import {HttpClient} from "@angular/common/http";
import {environment} from "src/environments/environment";
import {UserData} from "../entity/user/user-data";
import {AccessGateResponse} from "../entity/gate";
import {Router} from "@angular/router";

const authEndpointUrl: string = `${environment.apiUrl}/login`;
const userEndpointUrl: string = `${environment.apiUrl}/user`;
const gateEndpointUrl: string = `${environment.apiUrl}/gate`;

const jwtCookieName: string = 'JWT';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  user$: Observable<User>;

  private userSubject: BehaviorSubject<User>;

  constructor(private http: HttpClient,
              private router: Router) {
    this.userSubject = new BehaviorSubject(this.getPersistedUser());
    this.user$ = this.userSubject.asObservable();
  }

  get isUserAuthenticated(): boolean {
    return (this.currentUser != null);
  }

  get currentUser(): User {
    return this.userSubject.value
  }

  get authEndpointUrl(): string {
    return authEndpointUrl;
  }

  retrieveUser(): Observable<User> {
    return this.requestUserProfileInfo().pipe(
      tap((user: User) => this.setAuthUser(user))
    );
  }

  private requestUserProfileInfo(): Observable<User> {
    return this.http.get(`${userEndpointUrl}/`).pipe(
      map((data: any) => new User(data.user))
    );
  }

  private setAuthUser(user: User): void {
    this.persistUser(user);
    this.userSubject.next(user);
  }

  access(email: string): Observable<AccessGateResponse> {
    return this.http.post<AccessGateResponse>(`${gateEndpointUrl}/access`, {email: email})
  }

  update(user: User): Observable<string> {
    return this.http.post(`${userEndpointUrl}/update`, user.data, {responseType: "text"}).pipe(
      map((message: string) => message),
      tap( () => this.setAuthUser(user)),
    );
  }

  delete(): Observable<string> {
    return this.http.delete(`${userEndpointUrl}/delete`, {responseType: "text"}).pipe(
      map((message: string) => message)
    );
  }

  logoutAndGoHome() {
    this.logout();
    this.router.navigate(['/']);
  }

  logout(): void {
    this.removeUser();
    this.deleteCookie(jwtCookieName);
    this.userSubject.next(null);
  }

  private getCookieValue(cookieName: string) {
    const allCookies: string[] = document.cookie.split(';');
    const cookiePair: string[] = allCookies.map((cookie: string) => cookie.split('='))
                     .find((cookiePair: string[]) => {
                       return cookiePair[0] == cookieName;
                     });

    return cookiePair[1];
  }

  private deleteCookie(cookieName: string) {
    this.setCookieExpireDate(cookieName, new Date(0));
  }

  private setCookieExpireDate(cookieName: string, expireDate: Date) {
    document.cookie = `${cookieName}=${this.getCookieValue(cookieName)}=;expires=${expireDate.toString()};`
  }

  private persistUser(user: User): void {
    localStorage.setItem('user', JSON.stringify(user.data));
  }

  private getPersistedUser(): User {
    const userData: UserData = <UserData> JSON.parse(localStorage.getItem('user'));

    return (userData ? new User(userData) : null);
  }

  private removeUser(): void {
    localStorage.removeItem('user');
  }
}
