import { Component, OnInit } from '@angular/core';
import { Links } from '../../utils/links';
import { ViewExpandRequestService } from '../../services/view-expand-request.service';
import { TemplateEditComponent } from './template-edit/template-edit.component'
import { TemplateImporterComponent } from './template-importer/template-importer.component';

@Component({
  selector: 'app-templates',
  templateUrl: './templates.component.html',
  styleUrls: ['./templates.component.scss']
})
export class TemplatesComponent implements OnInit {
  private serviceEndpoint = Links.COMPOSITE_DESCRIPTIONS;
  private navigationContainerType = 'none';

  constructor(private viewExpandRequestor: ViewExpandRequestService) {}

  ngOnInit() {
  }

  private onRouteActivationChange(component) {
    if (component === TemplateEditComponent ||
      component === TemplateImporterComponent) {
      this.navigationContainerType = 'fullScreenSlide';
      this.viewExpandRequestor.request(true);
    } else {
      this.navigationContainerType = 'none';
      this.viewExpandRequestor.request(false);
    }
  }
}
