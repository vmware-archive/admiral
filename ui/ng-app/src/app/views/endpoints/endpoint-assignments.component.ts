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

import { Component, Input, OnChanges, OnInit, SimpleChanges } from "@angular/core";
import { FormArray, FormBuilder, FormGroup } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { DocumentService } from "../../utils/document.service";
import { ErrorService } from "../../utils/error.service";
import { Constants } from "../../utils/constants";
import { FT } from "../../utils/ft";
import { Links } from "../../utils/links";
import { Utils } from "../../utils/utils";

import * as I18n from 'i18next';

@Component({
    selector: 'app-endpoint-assignments',
    templateUrl: './endpoint-assignments.component.html',
    styleUrls: ['./endpoint-assignments.component.scss']
})
/**
 * Assign project/business groups to plans for the selected endpoint.
 */
export class EndpointAssignmentsComponent implements OnInit, OnChanges {
    @Input() entity: any;

    groups: any[];
    groupsLoading: boolean = false;
    plans: any[];
    plansLoading: boolean = false;

    assignmentsForm: FormGroup;
    formBuilder: FormBuilder = new FormBuilder();

    isSavingEndpoint: boolean = false;

    alertType: any;
    alertMessage: string;

    get assignments(): FormArray {
        return this.assignmentsForm
            && this.assignmentsForm.get('assignments') as FormArray;
    }

    get groupsTitle(): string {
        return FT.isApplicationEmbedded()
            ? I18n.t('projects.globalSelectLabelEmbedded')
            : I18n.t('projects.globalSelectLabel');
    }

    get groupsSelectorTitle(): string {
        let key = FT.isApplicationEmbedded()  ? 'endpoints.details.groupSelectorTitle'
                    : 'endpoints.details.projectSelectorTitle';

        return I18n.t('dropdownSearchMenu.title', {
            ns: 'base',
            entity: I18n.t(key)
        } as I18n.TranslationOptions);
    }

    get groupsSearchPlaceholder(): string {
        let key = FT.isApplicationEmbedded() ? 'endpoints.details.groupSearchPlaceholder'
                    : 'endpoints.details.projectSearchPlaceholder';

        return I18n.t('dropdownSearchMenu.searchPlaceholder', {
            ns: 'base',
            entity: I18n.t(key)
        } as I18n.TranslationOptions);
    }

    get plansSelectorTitle(): string {
        return I18n.t('dropdownSearchMenu.title', {
            ns: 'base',
            entity: I18n.t('endpoints.details.planSelectorTitle')
        } as I18n.TranslationOptions);
    }

    get plansSearchPlaceholder(): string {
        return I18n.t('dropdownSearchMenu.searchPlaceholder', {
            ns: 'base',
            entity: I18n.t('endpoints.details.planSearchPlaceholder')
        } as I18n.TranslationOptions);
    }

    get isApplicationEmbedded(): boolean {
        return FT.isApplicationEmbedded();
    }

    constructor(protected route: ActivatedRoute, protected router: Router,
        protected documentService: DocumentService, protected errorService: ErrorService) {
        //
    }

    ngOnInit() {
        this.assignmentsForm = this.formBuilder.group({
            assignments: this.formBuilder.array([])
        });
    }

    ngOnChanges(changes: SimpleChanges) {
        if (this.entity) {
            Promise.all([this.populatePlans(), this.populateGroups()]).then(results => {
                this.generateAssignmentRows();

            }).catch(error => {
                console.error('Failed to list pks plans or groups', error);
                this.showErrorMessage(error);
            });
        }
    }

    private populateGroups() {
        this.groups = [];
        this.groupsLoading = true;
        let isEmbedded = this.isApplicationEmbedded;

        return this.documentService.listProjects().then(result => {
            this.groupsLoading = false;

            let sortField = isEmbedded ? "label" : "name";
            this.groups = Utils.sortObjectArrayByField(result.documents, sortField)
                .map(group => {
                    return {
                        name: isEmbedded ? group.label : group.name,
                        value: group
                    }
                });
        }).catch(error => {
            console.error('Failed to list groups ', error);

            this.groupsLoading = false;
            return Promise.reject(error);
        });
    }

    private populatePlans() {
        this.plans = [];
        this.plansLoading = true;

        let queryParams = { endpointLink: this.entity.documentSelfLink };
        return this.documentService.listWithParams(Links.PKS_PLANS, queryParams).then(result => {
                this.plansLoading = false;

                this.plans = Utils.sortObjectArrayByField(result.documents, 'name')
                    .map(plan => {
                        return {
                            name: plan.name,
                            value: plan.name
                        }
                    });
            }).catch(error => {
                console.error('Failed to list pks plans', error);

                this.plansLoading = false;
                return Promise.reject(error);
            });
    }

    generateAssignmentRows() {
        let planAssignments = this.entity && this.entity.planAssignments;

        if (planAssignments) {
            let groupLinks = Object.keys(planAssignments);
            groupLinks.forEach((groupLink) => {
                planAssignments[groupLink].plans.forEach((plan) => {
                    this.addAssignment(groupLink, plan, true);
                });
            });

            if (!groupLinks || groupLinks.length === 0) {
                this.addAssignment('', '', false);
            }
        } else {
            this.addAssignment('', '', false);
        }
    }

    createAssignment(groupValue, planValue, existing) {
        let formGroup = this.formBuilder.group({
            group: '',
            plan: '',
            existing: existing
        });

        if (existing) {
            this.groups.forEach(entry => {
                let projectOrGroupLink = FT.isApplicationEmbedded()
                    ? entry.value.id
                    : entry.value.documentSelfLink;

                if (groupValue === projectOrGroupLink) {
                    formGroup.controls.group.setValue(entry);
                }
            });
            this.plans.forEach(entry => {
                if (planValue === entry.value) {
                    formGroup.controls.plan.setValue(entry);
                }
            });
        }

        return formGroup;
    }

    addAssignment(projectValue, roleValue, existing): void {
        this.assignments.push(this.createAssignment(projectValue, roleValue, existing));
    }

    addEmptyAssignment($event): void {
        $event.preventDefault();
        $event.stopPropagation();

        this.addAssignment('', '', false);
    }

    removeAssignment($event, index): void {
        $event.preventDefault();
        $event.stopPropagation();

        this.assignments.removeAt(index);

        if (this.assignments.length === 0) {
            // all assignments have been removed - add an empty one
            this.addAssignment('', '', false);
        }
    }

    cancel() {
        this.goBack();
    }

    save() {
        let endpointData = this.getEndpointData();

        if (!this.hasChanges(endpointData)) {
            // Nothing changed
            this.goBack();
        }

        this.isSavingEndpoint = true;

        this.documentService.patch(this.entity.documentSelfLink, endpointData)
            .then(() => {
                this.isSavingEndpoint = false;
                this.goBack();
            }).catch(error => {
                this.isSavingEndpoint = false;

                console.error('Failed to save endpoint', error);
                this.showErrorMessage(error);
            });
    }

    private hasChanges(endpointData): boolean {
        if (!this.entity.planAssignments && endpointData
            || this.entity.planAssignments && !endpointData) {
            return true;
        }

        let hasChanges: boolean = false;

        Object.keys(this.entity.planAssignments).forEach((group) => {
            if (!endpointData.planAssignments[group]) {
                hasChanges = true;
                return;
            }

            let updatedPlans = endpointData.planAssignments[group].plans;
            let oldPlans = this.entity.planAssignments[group].plans;
            if (updatedPlans.length !== oldPlans.length) {
                hasChanges = true;
                return;
            }

            let newPlan = updatedPlans.find(plan => oldPlans.indexOf(plan) === -1);
            let oldPlan = oldPlans.find(plan => updatedPlans.indexOf(plan) === -1);

            if (newPlan || oldPlan) {
                hasChanges = true;
                return;
            }
        });

        return hasChanges;
    }

    private getEndpointData(): any {
        let groupLinks = [];
        let planAssignments = {};

        // Remove duplicate entries
        let assignmentValues = this.assignments.value.filter(function (item, index, self) {
            return index === self.findIndex(function (otherItem) {
                return otherItem['group'] === item['group'] && otherItem['plan'] === item['plan']
            });
        });

        assignmentValues.forEach((value) => {
            let groupOption = value.group;
            let planOption = value.plan;

            if (groupOption && groupOption.value && planOption && planOption.value) {
                // Group
                let groupLink = this.isApplicationEmbedded
                    ? groupOption.value.id : groupOption.value.documentSelfLink;
                if (groupLinks.indexOf(groupLink) === -1) {
                    groupLinks.push(groupLink);
                }

                let plansObj = planAssignments[groupLink];
                if (!plansObj) {
                    plansObj = {
                        plans: []
                    };
                    planAssignments[groupLink] = plansObj;
                }

                if (plansObj.plans.indexOf(planOption.value) === -1) {
                    plansObj.plans.push(planOption.value);
                }
            }
        });

        if (groupLinks.length === 0) {
            return {
                tenantLinks: [],
                planAssignments: {}
            };
        }

        return {
            tenantLinks: groupLinks,
            planAssignments: planAssignments
        };
    }

    private showErrorMessage(error) {
        this.showAlertMessage(Constants.alert.type.DANGER, Utils.getErrorMessage(error)._generic);
    }

    private showAlertMessage(messageType, message) {
        this.alertType = messageType;
        this.alertMessage = message;
    }

    resetAlert() {
        this.alertType = null;
        this.alertMessage = null;
    }

    goBack() {
        this.router.navigate(['..'], { relativeTo: this.route });
    }
}
