import { Component, Input } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'pod-details-properties',
  templateUrl: './pod-details-properties.component.html'
})
export class PodDetailsPropertiesComponent {

  @Input()
  pod;
}
