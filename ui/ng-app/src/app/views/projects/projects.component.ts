import { Component, OnInit } from '@angular/core';
import { Links } from '../../utils/links';
import { ViewExpandRequestService } from '../../services/view-expand-request.service';
import { ProjectCreateComponent } from './project-create/project-create.component';
import { ProjectDetailsComponent } from './project-details/project-details.component';

@Component({
  selector: 'app-projects',
  templateUrl: './projects.component.html',
  styleUrls: ['./projects.component.scss']
})
export class ProjectsComponent {
  private serviceEndpoint = Links.PROJECTS;
  private navigationContainerType = 'none';

  constructor(private viewExpandRequestor: ViewExpandRequestService) {}

  private onRouteActivationChange(component) {
    if (component === ProjectDetailsComponent) {
      this.navigationContainerType = 'fullScreenSlide';
      this.viewExpandRequestor.request(true);
    } else if (component === ProjectCreateComponent) {
      this.navigationContainerType = 'default';
      this.viewExpandRequestor.request(false);
    } else {
      this.navigationContainerType = 'none';
      this.viewExpandRequestor.request(false);
    }
  }
}
