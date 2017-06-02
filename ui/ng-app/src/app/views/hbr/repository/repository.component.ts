import { Component } from '@angular/core';

@Component({
  template: `
    <div class="main-view">
      <div class="title">Project Repositories</div>
      <hbr-repository [projectId]="projectId" [sessionInfo]="sessionInfo" style="margin-top: -50px; display: block;"></hbr-repository>
    </div>
  `
})
export class RepositoryComponent {
  projectId = 1;
  sessionInfo = {};
}