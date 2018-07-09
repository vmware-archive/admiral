/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { DocumentService } from "../../../../utils/document.service";
import { Utils } from "../../../../utils/utils";

@Component({
    selector: 'app-kubernetes-cluster-nodes',
    templateUrl: './kubernetes-cluster-nodes.component.html',
    styleUrls: ['./kubernetes-cluster-nodes.component.scss']
})
/**
*  A kubernetes/pks cluster nodes view.
*/
export class KubernetesClusterNodesComponent implements OnChanges {
    @Input() cluster: any;

    nodes: any[];

    constructor(service: DocumentService) {
    }

    ngOnChanges(changes: SimpleChanges) {
        if (!this.cluster || !this.cluster.nodeLinks || !this.cluster.nodes) {
            return;
        }

        let firstNode = this.cluster.nodes[this.cluster.nodeLinks[0]];
        let nodesJson = Utils.getCustomPropertyValue(firstNode.customProperties, '__nodes');
        if (!nodesJson) {
            return;
        }

        let nodes = JSON.parse(nodesJson);
        this.nodes = nodes.map(n => ({
            name: n.name,
            cpuCores: n.cpuCores,
            totalMemory: this.formatNumber(n.totalMemory)
        }));
    }

    formatNumber(number) {
        if (!number) {
            return '0';
        }
        let m = Utils.getMagnitude(number);

        return Utils.formatBytes(number, m) + ' ' + Utils.magnitudes[m];
    }
}
