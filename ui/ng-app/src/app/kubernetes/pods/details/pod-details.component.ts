/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { DocumentService } from '../../../utils/document.service';
import { Links } from '../../../utils/links';


const FLOGS = `[INFO]
[INFO] > node-sass@4.5.0 install /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app/node_modules/node-sass
[INFO] > node scripts/install.js
[INFO]
[INFO] Cached binary found at /Users/tgeorgiev/.npm/node-sass/4.5.0/darwin-x64-48_binding.node
[INFO]
[INFO] > node-sass@4.5.0 postinstall /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app/node_modules/node-sass
[INFO] > node scripts/build.js
[INFO]
[INFO] Binary found at /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app/node_modules/node-sass/vendor/darwin-x64-48/binding.node
[INFO] Testing binary
[INFO] Binary is fine
[INFO] admiral-ui-ng@0.9.5-SNAPSHOT /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app
[INFO] ├─┬ @angular/cli@1.0.0-rc.0
[INFO] │ └── node-sass@4.5.0
[INFO] ├── UNMET PEER DEPENDENCY @angular/compiler@2.4.8
[INFO] ├── UNMET PEER DEPENDENCY @angular/core@2.4.8
[INFO] └── admiral-ui-common@0.9.5-SNAPSHOT
[INFO]
[WARNING] npm WARN @angular/compiler-cli@2.4.9 requires a peer of @angular/compiler@2.4.9 but none was installed.
[WARNING] npm WARN @angular/compiler-cli@2.4.9 requires a peer of @angular/core@2.4.9 but none was installed.
[INFO]
[INFO] --- frontend-maven-plugin:1.3:npm (npm install) @ admiral-ui-ng-app ---
[INFO] Running 'npm install --ignore-scripts' in /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app
[WARNING] npm WARN @angular/compiler-cli@2.4.9 requires a peer of @angular/compiler@2.4.9 but none was installed.
[WARNING] npm WARN @angular/compiler-cli@2.4.9 requires a peer of @angular/core@2.4.9 but none was installed.
[INFO]
[INFO] --- frontend-maven-plugin:1.3:npm (npm rebuild) @ admiral-ui-ng-app ---
[INFO] Running 'npm rebuild node-sass' in /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app
[INFO]
[INFO] > node-sass@4.5.0 install /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app/node_modules/node-sass
[INFO] > node scripts/install.js
[INFO]
[INFO] node-sass build Binary found at /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app/node_modules/node-sass/vendor/darwin-x64-48/binding.node
[INFO]
[INFO] > node-sass@4.5.0 postinstall /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app/node_modules/node-sass
[INFO] > node scripts/build.js
[INFO]
[INFO] Binary found at /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app/node_modules/node-sass/vendor/darwin-x64-48/binding.node
[INFO] Testing binary
[INFO] Binary is fine
[INFO] node-sass@4.5.0 /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app/node_modules/node-sass
[INFO]
[INFO] --- frontend-maven-plugin:1.3:npm (npm build) @ admiral-ui-ng-app ---
[INFO] Running 'npm run build' in /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app
[INFO]
[INFO] > admiral-ui-ng@0.9.5-SNAPSHOT build /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app
[INFO] > ng build --prod
[INFO]
[INFO] Hash: a1ef07075e00abfb7863
[INFO] Time: 38871ms
[INFO] chunk    {0} main.69c5de8c99d90f42fa2c.bundle.js (main) 485 kB {3} [initial] [rendered]
[INFO] chunk    {1} scripts.5c37b7533fb14663824a.bundle.js (scripts) 516 kB {4} [initial] [rendered]
[INFO] chunk    {2} styles.b2d073247234e1aeaa7d.bundle.css (styles) 387 bytes {4} [initial] [rendered]
[INFO] chunk    {3} vendor.1bccf27c90236d67595c.bundle.js (vendor) 2.75 MB [initial] [rendered]
[INFO] chunk    {4} inline.7ba647929d6a51c2e58a.bundle.js (inline) 0 bytes [entry] [rendered]
[ERROR]
[INFO]
[INFO] --- maven-resources-plugin:2.7:resources (default-resources) @ admiral-ui-ng-app ---
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] Copying 12 resources
[INFO]
[INFO] --- maven-compiler-plugin:3.5.1:compile (default-compile) @ admiral-ui-ng-app ---
[INFO] No sources to compile
[INFO]
[INFO] --- maven-resources-plugin:2.7:testResources (default-testResources) @ admiral-ui-ng-app ---
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] skip non existing resourceDirectory /Users/tgeorgiev/development/projects/bellevue/admiral/ui/ng-app/src/test/resources
[INFO]
[INFO] --- maven-compiler-plugin:3.5.1:testCompile (default-testCompile) @ admiral-ui-ng-app ---
[INFO] No sources to compile
[INFO]
[INFO] --- maven-surefire-plugin:2.12.4:test (default-test) @ admiral-ui-ng-app ---`;

@Component({
  selector: 'pod-details',
  templateUrl: './pod-details.component.html',
  styleUrls: ['./pod-details.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class PodDetailsComponent extends BaseDetailsComponent {
  private logsTimeout;
  private logs;

  private statsTimeout;

  private loadingStats = true;
  private loadingLogs = true;

  constructor(route: ActivatedRoute, service: DocumentService) {
    super(route, service, Links.PODS);
  }

  protected entityInitialized() {
    setTimeout(() => {
      this.getLogs();

      clearInterval(this.logsTimeout);
      this.logsTimeout = setInterval(() => {
        this.getLogs();
      }, 10000);
    }, 500);
  }

  ngOnDestroy() {
    clearInterval(this.logsTimeout);
  }

  getLogs() {
    this.service.getLogs(Links.POD_LOGS, this.id, 10000).then(logs => {
      this.loadingLogs = false;
      this.loadingStats = false;
      this.logs = FLOGS;
    }).catch(() =>{
      this.loadingLogs = false;
      this.loadingStats = false;
      this.logs = FLOGS;
    });
  }
}
