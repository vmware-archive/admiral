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

import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DocumentService } from '../../../../utils/document.service';
import { ErrorService } from '../../../../utils/error.service';
import { ProjectService } from '../../../../utils/project.service';

@Component({
    selector: 'app-kubernetes-cluster-new',
    templateUrl: './kubernetes-cluster-new.component.html',
    styleUrls: ['./kubernetes-cluster-new.component.scss']
})
/**
 * New kubernetes cluster view.
 */
export class KubernetesClusterNewComponent {

    constructor(protected route: ActivatedRoute, protected documentService: DocumentService,
                protected router: Router, protected projectService: ProjectService,
                protected errorService: ErrorService) {
    }
}
