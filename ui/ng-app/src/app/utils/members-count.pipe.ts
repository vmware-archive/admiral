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

import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'membersCount' })
/**
 * Pipe calculating the number of members of a project.
 */
export class ProjectMembersCountPipe implements PipeTransform {

    public transform(project: any): number {
        let admins = project.administrators;
        let members = project.members;
        let viewers = project.viewers;

        let mergedCollection = this.mergeCollections(admins, members, viewers);

        return mergedCollection.length;
    }

    private mergeCollections(collection1: any, collection2: any, collection3: any): any {
        let resultCollection = [];

        if (collection1) {
            this.transferPrincipals(collection1, resultCollection);
        }

        if (collection2) {
            this.transferPrincipals(collection2, resultCollection);
        }

        if (collection3) {
            this.transferPrincipals(collection3, resultCollection);
        }

        return resultCollection;
    }

    private transferPrincipals(srcCol: any, dstCol: any) {
        if (!srcCol || !dstCol) {
            return;
        }
        srcCol.forEach(p => {
            let alreadyAdded = dstCol.find(x => {
                return x.id === p.id;
            });
            if (!alreadyAdded) {
                dstCol.push(p);
            }
        });
    }
}
