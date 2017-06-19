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

import { Component, Input, OnChanges } from '@angular/core';
import { DocumentService } from "../../../utils/document.service";
import * as I18n from 'i18next';
import { Utils } from "../../../utils/utils";

@Component({
    selector: 'app-cluster-resources',
    templateUrl: './cluster-resources.component.html',
    styleUrls: ['./cluster-resources.component.scss']
})
/**
 *  A cluster's resources view.
 */
export class ClusterResourcesComponent implements OnChanges {

    @Input() cluster: any;

    constructor(protected service: DocumentService) { }

    ngOnChanges() {

    }
}
