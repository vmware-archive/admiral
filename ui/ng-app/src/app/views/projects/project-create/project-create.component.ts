import { Component, AfterViewInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-project-create',
  templateUrl: './project-create.component.html',
  styleUrls: ['./project-create.component.scss']
})
export class ProjectCreateComponent implements AfterViewInit {
  private opened: boolean;
  constructor(private router: Router, private route: ActivatedRoute) {}

  ngAfterViewInit() {
    setTimeout(() => {
      this.opened = true;
    });
  }

  private toggleModal(open) {
    this.opened = open;
    if (!open) {
      this.router.navigate(['../'], { relativeTo: this.route });
    }
  }

}
