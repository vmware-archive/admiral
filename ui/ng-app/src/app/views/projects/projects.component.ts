import { Component, OnInit } from '@angular/core';
import { BaseListComponent } from '../../components/base/base-list.component';
import { Links } from '../../utils/links';
import { DocumentService } from '../../utils/document.service';
import { Router } from '@angular/router';
import { ViewExpandRequestService } from '../../services/view-expand-request.service';

@Component({
  selector: 'app-projects',
  templateUrl: './projects.component.html',
  styleUrls: ['./projects.component.scss']
})
export class ProjectsComponent {
  private serviceEndpoint = Links.PROJECTS;
  private selfComponentName = ProjectsComponent.name;

  constructor(private viewExpandRequestor: ViewExpandRequestService) {}

  private onRouteActivationChange(isActive) {
    this.viewExpandRequestor.request(isActive);
  }
}
