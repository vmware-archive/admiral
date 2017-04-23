import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'volume-box',
  templateUrl: './volume-box.component.html',
  styleUrls: ['./volume-box.component.scss']
})
export class VolumeBoxComponent implements OnInit {

  @Input() volume: any;

  constructor() { }

  ngOnInit() {
  }

}
