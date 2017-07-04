import { RoutesRestriction } from './../../utils/routes-restriction';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-administration',
  templateUrl: './administration.component.html',
  styleUrls: ['./administration.component.scss']
})
export class AdministrationComponent implements OnInit {

  constructor() { }

  ngOnInit() {
  }

  get identityManagementRouteRestriction() {
    return RoutesRestriction.IDENTITY_MANAGEMENT;
  }

  get projectsRouteRestriction() {
    return RoutesRestriction.PROJECTS;
  }

  get registriesRouteRestriction() {
    return RoutesRestriction.REGISTRIES;
  }

  get configurationRouteRestriction() {
    return RoutesRestriction.CONFIGURATION;
  }

  get logsRouteRestriction() {
    return RoutesRestriction.LOGS;
  }
}
