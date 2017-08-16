import { Utils } from './../../../utils/utils';
import { ProjectService } from './../../../utils/project.service';
import { TagClickEvent } from 'harbor-ui';
import { Router, ActivatedRoute } from '@angular/router';
import { Component } from '@angular/core';

@Component({
  template: `
    <div class="main-view">
      <div class="title">Project Repositories</div>
      <hbr-repository-stackview [projectId]="projectId" [projectName]="projectName" [hasSignedIn]="true"
        [hasProjectAdminRole]="true" (tagClickEvent)="watchTagClickEvent($event)"
        style="display: block;"></hbr-repository-stackview>

      <navigation-container>
        <router-outlet></router-outlet>
      </navigation-container>
    </div>
  `
})
export class RepositoryComponent {

  private static readonly HBR_DEFAULT_PROJECT_INDEX: Number = 1;
  private static readonly CUSTOM_PROP_PROJECT_INDEX: string = '__projectIndex';

  sessionInfo = {};

  constructor(private router: Router, private route: ActivatedRoute, private ps: ProjectService) {}

  get projectId(): Number {
    let selectedProject = this.getSelectedProject();
    let projectIndex = selectedProject && Utils.getCustomPropertyValue(
      selectedProject.customProperties,
      RepositoryComponent.CUSTOM_PROP_PROJECT_INDEX);
    return (projectIndex && Number(projectIndex)) || RepositoryComponent.HBR_DEFAULT_PROJECT_INDEX;
  }

  get projectName(): string {
    let selectedProject = this.getSelectedProject();
    return (selectedProject && selectedProject.name) || 'unknown';
  }

  private getSelectedProject(): any {
    return this.ps && this.ps.getSelectedProject();
  }

  watchTagClickEvent(tag: TagClickEvent) {
    this.router.navigate(['repositories', tag.repository_name, 'tags', tag.tag_name], {relativeTo: this.route});
  }
}