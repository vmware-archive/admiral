/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { DocumentService } from '../../../utils/document.service';
import { ErrorService } from '../../../utils/error.service';
import { ProjectService } from '../../../utils/project.service';
import { Links } from '../../../utils/links';

let getPortLinkDisplayText = function(hostIp, portObj) {
    let linkDisplayName = '';

      // Used backend's com.vmware.vcac.container.domain.PortBinding.toString() to format the
      // ports string
      if (hostIp) {
            linkDisplayName += hostIp;
      }

    if (portObj.nodePort) {
        if (linkDisplayName.length > 0) {
            linkDisplayName += ':';
        }
        linkDisplayName += portObj.nodePort;
    }

    if (linkDisplayName.length > 0) {
        linkDisplayName += ':';
    }
    linkDisplayName += portObj.port;

    if (portObj.protocol) {
        linkDisplayName += '/' + portObj.protocol;
    }

    return linkDisplayName;
};

let getPortLinks = function(hostIp, ports) {
    var portLinks = [];

    if (ports) {
        for (let i = 0; i < ports.length; i++) {
        let portObj = ports[i];

        let linkDisplayName = getPortLinkDisplayText(hostIp, portObj);

        let linkAddress = hostIp ? ('http://' + hostIp + ':' + portObj.nodePort) : null;

        portLinks[i] = {
            link: linkAddress,
            name: linkDisplayName
        };
        }
    }

    return portLinks;
};

@Component({
    selector: 'service-details',
    templateUrl: './service-details.component.html',
    styleUrls: ['./service-details.component.scss'],
    encapsulation: ViewEncapsulation.None
})
/**
 * Kubernetes service details view.
 */
export class ServiceDetailsComponent extends BaseDetailsComponent {
    portLinks: Array<any>;

    constructor(route: ActivatedRoute, router: Router, documentService: DocumentService,
              projectService: ProjectService, errorService: ErrorService) {

        super(Links.SERVICES, route, router, documentService, projectService, errorService);
    }

    entityInitialized() {
        this.calculatePortLinks();
    }

    protected onProjectChange() {
        this.router.navigate(['../'], {relativeTo: this.route});
    }

    calculatePortLinks() {
        let result = [];

        if (this.entity && this.entity.service && this.entity.service.spec) {
            let ports = this.entity.service.spec.ports;
            let externalIPs = this.entity.service.spec.externalIPs;

            if (externalIPs && externalIPs.length > 0) {
                externalIPs.forEach(externalIP => {
                    result = result.concat(getPortLinks(externalIP, ports));
                });
            } else {
                result = getPortLinks(null, ports);
            }
        }

        this.portLinks = result;
    }
}
