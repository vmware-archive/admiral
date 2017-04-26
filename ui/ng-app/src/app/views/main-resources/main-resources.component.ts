import { FT } from './../../utils/ft';
import { Component, OnInit, ViewEncapsulation } from '@angular/core';


@Component({
  selector: 'app-main-resources',
  templateUrl: './main-resources.component.html',
  styleUrls: ['./main-resources.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class MainResourcesComponent implements OnInit {

  kubernetesEnabled = FT.isKubernetesHostOptionEnabled();

  constructor() { }

  ngOnInit() {
  }

}
