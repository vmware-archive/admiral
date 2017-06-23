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

        let count = (admins && admins.length) || 0;
        if (members && members.length > 0) {
            if (count > 0) {
                // eliminate duplicates
                members.forEach((member) => {
                    let adminFound = admins.find((admin) => {
                        return admin.email === member.email;
                    });

                    if (!adminFound) {
                      count++;
                    }
                });
            } else {
                count += members.length;
            }
        }

        return count;
    }
}
