import { Links } from './../../../utils/links';
import { DocumentService } from './../../../utils/document.service';
import { Component, AfterViewInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormGroup, FormControl, Validators } from "@angular/forms";

@Component({
  selector: 'app-project-create',
  templateUrl: './project-create.component.html',
  styleUrls: ['./project-create.component.scss']
})
export class ProjectCreateComponent implements AfterViewInit {
  opened: boolean;

  projectForm = new FormGroup({
      name: new FormControl('', Validators.required),
      description: new FormControl(''),
      icon: new FormControl('')
  });

  constructor(private router: Router, private route: ActivatedRoute, private ds: DocumentService) {}

  ngAfterViewInit() {
    setTimeout(() => {
      this.opened = true;
    });
  }

  toggleModal(open) {
    this.opened = open;
    if (!open) {
      this.router.navigate(['../'], { relativeTo: this.route });
    }
  }

  saveProject() {
    if (this.projectForm.valid) {
      this.ds.post(Links.PROJECTS, this.projectForm.value).then(() => {
        this.toggleModal(false);
      });
    }
  }
}
