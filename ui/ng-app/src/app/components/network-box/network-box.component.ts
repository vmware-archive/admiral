import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'network-box',
  templateUrl: './network-box.component.html',
  styleUrls: ['./network-box.component.scss']
})
export class NetworkBoxComponent implements OnInit {

  @Input() network: any;

  constructor() { }

  ngOnInit() {
  }

}
