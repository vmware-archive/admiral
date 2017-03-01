import { Component, AfterViewInit, OnChanges, Input, ViewChild, Pipe, PipeTransform, ViewEncapsulation } from '@angular/core';
import { RadialProgress } from 'admiral-ui-common';
import { NetworkTrafficVisualization } from 'admiral-ui-common';
import { formatUtils } from 'admiral-ui-common';
import * as I18n from 'i18next';

const NA = 'N/A';

@Component({
  selector: 'compute-stats',
  templateUrl: './stats.component.html',
  styleUrls: ['./stats.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class StatsComponent implements AfterViewInit, OnChanges {

  @Input()
  cpuUsage: number;

  @Input()
  memUsage: number;

  @Input()
  memLimit: number;

  @Input()
  networkIn: number;

  @Input()
  networkOut: number;

  @ViewChild('cpuStats') cpuStatsEl;
  @ViewChild('memoryStats') memoryStatsEl;
  @ViewChild('networkStats') networkStatsEl;


  private cpuStats: RadialProgress;
  private memoryStats: RadialProgress;
  private networkStats: NetworkTrafficVisualization;

  constructor() { }

  public ngAfterViewInit() {
    this.cpuStats = new RadialProgress(this.cpuStatsEl.nativeElement);
    this.cpuStats.diameter(150).label('CPU');

    this.memoryStats = new RadialProgress(this.memoryStatsEl.nativeElement);
    this.memoryStats.diameter(150).label('Memory');


    this.networkStats = new NetworkTrafficVisualization(this.networkStatsEl.nativeElement, I18n);

    this.setCpuUsage();
    this.setMemoryUsage();
    this.setNetworkUsage();
  }

  public ngOnChanges(changes) {
    this.setCpuUsage();
    this.setMemoryUsage();
    this.setNetworkUsage();
  }

  private setCpuUsage() {
    if (!this.cpuStats) {
      return;
    }
    if (this.cpuUsage) {
      this.cpuStats.value(this.cpuUsage).majorTitle(null).render();
    } else {
      this.cpuStats.value(0).majorTitle(NA).render();
    }
  }

  private setMemoryUsage() {
    if (!this.memoryStats) {
      return;
    }
    var memoryPercentage;
    if (!this.memUsage || !this.memLimit) {
      memoryPercentage = 0;
    } else {
      memoryPercentage = (this.memUsage / this.memLimit) * 100;
    }

    if (this.memUsage || this.memLimit) {
      var memoryUsage = formatUtils.formatBytes(this.memUsage);
      var memoryLimit = formatUtils.formatBytes(this.memLimit);

      this.memoryStats.majorTitle(memoryUsage).minorTitle(memoryLimit).value(memoryPercentage)
        .render();
    } else {
      this.memoryStats.majorTitle(NA).minorTitle(NA).value(0).render();
    }
  }

  private setNetworkUsage() {
    if (!this.networkStats) {
      return;
    }

    if (this.networkIn || this.networkOut) {
      this.networkStats.setData(this.networkIn, this.networkOut);
    } else {
      this.networkStats.reset(NA);
    }
  }
}


