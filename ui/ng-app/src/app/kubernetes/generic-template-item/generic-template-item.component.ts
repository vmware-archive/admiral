import { Component, OnInit, Input, ViewEncapsulation } from '@angular/core';

@Component({
  selector: 'kubernetes-generic-template-item',
  templateUrl: './generic-template-item.component.html',
  styleUrls: ['./generic-template-item.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class GenericTemplateItemComponent implements OnInit {

  @Input() entity: any;

  constructor() { }

  ngOnInit() {
  }

}
