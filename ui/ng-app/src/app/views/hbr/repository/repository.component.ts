import { Component } from '@angular/core';

@Component({
  template: '<hbr-repository [projectId]="projectId" [sessionInfo]="sessionInfo" style="width: 100%"></hbr-repository>'
})
export class RepositoryComponent {
  projectId = 1;
  sessionInfo = {};
}