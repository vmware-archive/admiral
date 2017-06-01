import { Component, OnInit } from '@angular/core';
import { Links } from '../../utils/links';
import { ProjectCreateComponent } from './project-create/project-create.component';
import { ProjectDetailsComponent } from './project-details/project-details.component';
import { NavigationContainerType } from '../../components/navigation-container/navigation-container.component';

@Component({
  selector: 'app-projects',
  templateUrl: './projects.component.html',
  styleUrls: ['./projects.component.scss']
})
export class ProjectsComponent {
  serviceEndpoint = Links.PROJECTS;
}
