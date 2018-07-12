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

import { Component, Input, SimpleChanges, ViewChild } from "@angular/core";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { DocumentService } from "../../utils/document.service";
import { ErrorService } from "../../utils/error.service";
import { Constants } from "../../utils/constants";
import { Links } from "../../utils/links";
import { Utils } from "../../utils/utils";
import { FT } from "../../utils/ft";
import { MultiCheckboxSelectorComponent } from "../../components/multi-checkbox-selector/multi-checkbox-selector.component";

import * as I18n from 'i18next';

@Component({
    selector: 'app-endpoint-create',
    templateUrl: './endpoint-create.component.html',
    styleUrls: ['./endpoint-create.component.scss']
})
/**
 * View for endpoint creation/update.
 */
export class EndpointCreateComponent {
    @Input() entity: any;

    @ViewChild(MultiCheckboxSelectorComponent) projectsSelector: MultiCheckboxSelectorComponent;

    projectEntries: any[] = [];

    editMode: boolean = false;

    credentials: any[];

    certificate: any;
    showCertificateWarning: boolean = false;
    certificateOrigin: string;

    alertType: any;
    alertMessage: string;

    endpointDetailsForm = new FormGroup({
        name: new FormControl('', Validators.required),
        description: new FormControl(''),
        uaaAddress: new FormControl('', Validators.required),
        uaaCredentials: new FormControl(''),
        pksAddress: new FormControl('', Validators.required)
    });

    isSavingEndpoint: boolean = false;
    isTestingConnection: boolean = false;

    credentialsTitle = I18n.t('dropdownSearchMenu.title', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    credentialsSearchPlaceholder = I18n.t('dropdownSearchMenu.searchPlaceholder', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    constructor(protected route: ActivatedRoute, protected router: Router,
                protected documentService: DocumentService, protected errorService: ErrorService) {
    }

    get projectsSelectorLabel(): string {
        if (FT.isApplicationEmbedded()) {
            return I18n.t('endpoints.details.assignToGroups');
        } else {
            return I18n.t('endpoints.details.assignToProjects');
        }
    }

    ngOnInit() {
        this.populateProjectEntries();
        this.populateCredentials();
    }

    ngOnChanges(changes: SimpleChanges) {
        if (this.entity) {
            this.editMode = true;
            this.endpointDetailsForm.get('name').setValue(this.entity.name);
            this.endpointDetailsForm.get('description').setValue(this.entity.desc);
            this.endpointDetailsForm.get("uaaAddress").setValue(this.entity.uaaEndpoint);
            this.endpointDetailsForm.get("pksAddress").setValue(this.entity.apiEndpoint);
            this.markSelectedProjects();
        }
    }

    populateCredentials() {
        if (this.credentials) {
            return;
        }

        this.documentService.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentials = credentials.documents
            .filter(c => !Utils.areSystemScopedCredentials(c))
            .map(Utils.toCredentialViewModel);

            if (this.entity && this.entity.authCredentialsLink) {
                let credItem = this.credentials && this.credentials.filter((c) =>
                    c.documentSelfLink === this.entity.authCredentialsLink
                );
                if (credItem && credItem.length > 0) {
                    this.endpointDetailsForm.get('uaaCredentials').setValue(credItem[0]);
                }
            }
        }).catch((error) => {
            console.error('Credentials retrieval failed', error);
            this.showErrorMessage(error);
        });
    }

    private populateProjectEntries(): void {
        this.projectEntries = [];
        let embedded = FT.isApplicationEmbedded();

        this.documentService.listProjects().then((result) => {
            let sortField = embedded ? "label" : "name";
            this.projectEntries = Utils.sortObjectArrayByField(result.documents, sortField)
                .map(project => {
                    return {
                        name: embedded ? project.label : project.name,
                        value: project,
                        checked: false
                    };
                });
            this.markSelectedProjects();
        }).catch(error => {
            console.error('Failed to list projects ', error);
            this.errorService.error(Utils.getErrorMessage(error)._generic);
        });
    }

    private markSelectedProjects(): void {
        let selectedProjects = this.entity && this.entity.tenantLinks || [];
        if (this.projectEntries && selectedProjects.length > 0) {
            this.projectEntries.forEach(entry => {
                let projectOrGroupLink = FT.isApplicationEmbedded()
                    ? entry.value.id
                    : entry.value.documentSelfLink;
                if (selectedProjects.indexOf(projectOrGroupLink) >= 0) {
                    entry.checked = true;
                }
            });
        }
    }

    create(certificateAccepted: boolean = false) {
        if (this.endpointDetailsForm.valid) {
            this.isSavingEndpoint = true;
            let createEndpointRequest = this.getEndpointData(certificateAccepted);

            this.documentService.put(Links.PKS_ENDPOINT_CREATE, createEndpointRequest)
                .then((response) => {
                    if (response && response.certificate) {
                        this.promptAcceptCertificate(response);
                    } else {
                        // creation is successful
                        this.isSavingEndpoint = false;
                        this.goBack();
                    }
            }).catch(error => {
                this.isSavingEndpoint = false;

                console.error('Failed to create endpoint', error);
                this.showErrorMessage(error);
            });
        }
    }

    save() {
        if (this.endpointDetailsForm.valid) {
            this.isSavingEndpoint = true;

            this.documentService.patch(this.entity.documentSelfLink, this.getEndpointDataRaw())
                .then(() => {
                    this.isSavingEndpoint = false;
                    this.goBack();
            }).catch(error => {
                this.isSavingEndpoint = false;

                console.error('Failed to save endpoint', error);
                this.showErrorMessage(error);
            });
        }
    }

    testConnection() {
        if (this.endpointDetailsForm.valid) {
            this.testEndpointConnection();
        }
    }

    private promptAcceptCertificate(response) {
        this.certificate = response;
        this.showCertificateWarning = true;
        this.certificateOrigin = this.certificate.origin;
    }

    acceptCertificate() {
        this.showCertificateWarning = false;

        // continue secondary request
        if (this.isSavingEndpoint) {
            this.create(true);
        } else if (this.isTestingConnection) {
            this.testEndpointConnection(true);
        }
    }

    cancelAcceptCertificate() {
        this.showCertificateWarning = false;

        this.isSavingEndpoint = false;
        this.isTestingConnection = false;

        this.showAlertMessage(Constants.alert.type.WARNING,
                                I18n.t('endpoints.details.certificateNotAccepted'));
    }

    cancel() {
        this.goBack();
    }

    goBack() {
        this.router.navigate(['..'], {relativeTo: this.route});
    }

    getEndpointData(certificateAccepted:boolean) {
        let endpointData;

        let endpointDataRaw = this.getEndpointDataRaw();
        if (endpointDataRaw) {
            endpointData = {
                endpoint: endpointDataRaw
            };

            if (certificateAccepted && this.certificateOrigin) {
                endpointData["acceptCertificate"] = true;
                endpointData["acceptCertificateForHost"] = this.certificateOrigin;
            }
        }

        return endpointData;
    }

    getEndpointDataRaw() {
        let endpointDataRaw;

        if (this.endpointDetailsForm.valid) {
            let uaaCredentialsLink = this.endpointDetailsForm.get("uaaCredentials").value
                && this.endpointDetailsForm.get("uaaCredentials").value.documentSelfLink;

            let selectedOptions = this.projectsSelector && this.projectsSelector.selectedOptions || [];
            let projectLinks = selectedOptions.map((option) => {
                if (FT.isApplicationEmbedded()) {
                    return option.value.id;
                } else {
                    return option.value.documentSelfLink;
                }
            });

            endpointDataRaw = {
                name: this.endpointDetailsForm.get("name").value,
                desc: this.endpointDetailsForm.get("description").value,
                uaaEndpoint: this.endpointDetailsForm.get("uaaAddress").value,
                apiEndpoint: this.endpointDetailsForm.get("pksAddress").value,
                authCredentialsLink: uaaCredentialsLink,
                tenantLinks: projectLinks
            };
        }

        return endpointDataRaw;
    }

    testEndpointConnection(certificateAccepted: boolean = false) {
        this.isTestingConnection = true;
        let testConnectionRequest = this.getEndpointData(certificateAccepted);

        this.documentService.put(Links.PKS_ENDPOINT_TEST_CONNECTION, testConnectionRequest)
            .then((response) => {
            if (response && response.certificate) {
                this.promptAcceptCertificate(response);
            } else {
                // Test is successful (status 204)
                this.isTestingConnection = false;
                this.showAlertMessage(Constants.alert.type.SUCCESS,
                                        I18n.t('endpoints.details.connectionVerified'));
            }
        }).catch(error => {
            this.isTestingConnection = false;
            console.error('Connection to endpoint failed', error);
            this.showErrorMessage(error);
        });
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
}
