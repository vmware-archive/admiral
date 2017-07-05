import { TagClickEvent } from 'harbor-ui';
import { Router, ActivatedRoute } from '@angular/router';
import { Component } from '@angular/core';

@Component({
  template: `
    <div class="main-view">
      <div class="title">Project Repositories</div>
      <hbr-repository-stackview [projectId]="projectId" [hasSignedIn]="true"
        [hasProjectAdminRole]="true" (tagClickEvent)="watchTagClickEvent($event)"
        style="display: block;"></hbr-repository-stackview>

      <navigation-container>
        <router-outlet></router-outlet>
      </navigation-container>
    </div>
  `
})
export class RepositoryComponent {
  projectId = 1;
  sessionInfo = {};

  constructor(private router: Router, private route: ActivatedRoute) {}

  watchTagClickEvent(tag: TagClickEvent) {
    this.router.navigate(['repositories', tag.repository_name, 'tags', tag.tag_name], {relativeTo: this.route});
  }
}