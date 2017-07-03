import { Component } from '@angular/core';

@Component({
  template: `
    <div class="main-view">
      <div class="title">Project Repositories</div>
      <hbr-repository-stackview [projectId]="projectId" [hasSignedIn]="true"
        [hasProjectAdminRole]="true"
        style="display: block;"></hbr-repository-stackview>
    </div>
  `
})
export class RepositoryComponent {
  projectId = 1;
  sessionInfo = {};
}