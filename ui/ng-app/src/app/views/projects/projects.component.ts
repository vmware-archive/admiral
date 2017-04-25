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
      this.viewExpandRequestor.requestFullScreen(true);
    } else if (component === ProjectCreateComponent) {
      this.navigationContainerType = 'default';
      this.viewExpandRequestor.requestFullScreen(false);
    } else {
      this.navigationContainerType = 'none';
      this.viewExpandRequestor.requestFullScreen(false);
    }
  }
}
