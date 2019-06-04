import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import {WorkflowDetailComponent} from "./component/workflow-detail/workflow-detail.component";
import {WelcomeComponent} from "./component/welcome/welcome.component";
import {LoginComponent} from "./component/login/login.component";
import {AuthGuard} from "./guard/auth.guard";
import {AuthComponent} from "./component/auth/auth.component";
import {LogoutComponent} from "./component/logout/logout.component";
import {UserProfileComponent} from "./component/user-profile/user-profile.component";
import {MainComponent} from "./main.component";

const routes: Routes = [
  {path: 'login',   component: LoginComponent},
  {path: 'auth',    component: AuthComponent},
  {path: 'logout',  component: LogoutComponent},
  {path: 'workflow/:id', component: WorkflowDetailComponent, canActivate: [AuthGuard]},
  {path: 'profile', component: UserProfileComponent, canActivate: [AuthGuard]},

  {path: '**', redirectTo: ''}
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class MainRoutingModule { }
