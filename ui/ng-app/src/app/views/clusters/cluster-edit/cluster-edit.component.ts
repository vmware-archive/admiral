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

import { Component } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { DocumentService } from '../../../utils/document.service';
import { ErrorService } from '../../../utils/error.service';
import { ProjectService } from '../../../utils/project.service';
import { Constants } from '../../../utils/constants';
import { FT } from "../../../utils/ft";
import { Links } from '../../../utils/links';
import { Utils } from "../../../utils/utils";

import * as I18n from 'i18next';

@Component({
  selector: 'app-cluster-edit',
  templateUrl: './cluster-edit.component.html',
  styleUrls: ['./cluster-edit.component.scss']
})
/**
 * Edit (Docker, VCH) cluster view.
 */
export class ClusterEditComponent extends BaseDetailsComponent {
    credentials: any[];
    // certificate
    showCertificateWarning: boolean;
    certificate: any;
    // alert
    alertMessage: string;
    alertType: string;
    // actions
    isVerifying: boolean;
    isVerified: boolean;
    isUpdating: boolean;
    // private
    private isSingleHostCluster: boolean = false;

    clusterForm = new FormGroup({
        name: new FormControl('', Validators.required),
        description: new FormControl(''),
        publicAddress: new FormControl(''),
        credentials: new FormControl('')
    });

    credentialsTitle = I18n.t('dropdownSearchMenu.title', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    credentialsSearchPlaceholder = I18n.t('dropdownSearchMenu.searchPlaceholder', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    constructor(router: Router, route: ActivatedRoute, documentService: DocumentService,
                projectService: ProjectService, errorService: ErrorService) {

        super(Links.CLUSTERS, route, router, documentService, projectService, errorService);

        this.populateCredentials();
    }

    get title() {
        return FT.isVic() ? 'clusters.edit.titleEditVic' : 'clusters.edit.titleEdit';
    }

    get showPublicAddressField(): boolean {
        return FT.isHostPublicUriEnabled() && this.isSingleHostCluster;
    }

    get clusterUrl(): string {
        return this.entity && this.entity.address;
    }

    get isVch(): boolean {
        return this.entity && this.entity.type === 'VCH';
    }

    entityInitialized() {
        this.isSingleHostCluster = Utils.isSingleHostCluster(this.entity);
        // Name
        this.clusterForm.get('name').setValue(this.entity.name);
        // Description
        if (this.entity.details) {
            this.clusterForm.get('description').setValue(this.entity.details);
        }

        // populate the credentials if the edited cluster is of type VCH
        if (this.isVch && this.entity.nodeLinks && this.entity.nodeLinks.length > 0) {
            var vchHost = this.entity.nodes[this.entity.nodeLinks[0]];

            let authCredentialsLink =
                Utils.getCustomPropertyValue(vchHost.customProperties, '__authCredentialsLink');
            if (authCredentialsLink) {
                var credItem = this.credentials
                            .filter((c) => c.documentSelfLink === authCredentialsLink);
                if (credItem.length > 0) {
                    this.clusterForm.get('credentials').setValue(credItem[0]);
                }
            }
        }

        if (this.isSingleHostCluster) {
            let publicAddress = this.entity.publicAddress || '';
            this.clusterForm.get('publicAddress').setValue(publicAddress);
        }
    }

    onProjectChange() {
        // show clusters view, if project/business group selection has been changed.
        this.router.navigate(['../../../'], {relativeTo: this.route});
    }

    verifyCluster() {
        if (this.clusterForm.valid) {
            this.isVerifying = true;
            this.isVerified = false;

            let host = this.getVchClusterInputData();
            let hostSpec = {
                'hostState': host
            };

            this.service.put(Links.CONTAINER_HOSTS + '?validate=true', hostSpec)
                .then((response) => {
                this.isVerifying = false;
                this.isVerified = true;

                this.showAlertMessage(Constants.alert.type.SUCCESS, I18n.t('hosts.verified'));
            }).catch(error => {
                this.isVerifying = false;

                this.showErrorMessage(error);
            });
        }
    }

    updateDockerCluster() {
        if (this.clusterForm.valid) {
            let name = this.clusterForm.value.name;
            let description = this.clusterForm.value.description;

            let clusterDtoPatch = {
                'name': name,
                'details':  description
            };

            // TODO check if the backend will handle this
            if (this.isSingleHostCluster) {
                // allow overwriting with empty value
                clusterDtoPatch[Constants.clusters.properties.publicAddress] =
                                                        this.clusterForm.value.publicAddress || '';
            }

            this.isUpdating = true;
            this.service.patch(this.entity.documentSelfLink, clusterDtoPatch).then(() => {
                this.isUpdating = false;
                this.goBack();
            }).catch(error => {
                this.isUpdating = false;
                this.showErrorMessage(error);
            });
        }
    }

    updateVchCluster() {
        if (this.clusterForm.valid) {
            let name = this.clusterForm.value.name;
            let description = this.clusterForm.value.description;

            let clusterDtoPatch = {
                'name': name,
                'details':  description
            };

            this.isUpdating = true;
            this.service.patch(this.entity.documentSelfLink, clusterDtoPatch).then(() => {
                this.updateVchClusterCredentials();
            }).catch(error => {
                this.isUpdating = false;
                this.showErrorMessage(error);
            });
        }
    }

    acceptCertificate() {
        this.showCertificateWarning = false;
    }

    declineCertificate() {
        this.showCertificateWarning = false;
        this.isUpdating = false;
    }

    cancel() {
        this.showCertificateWarning = false;
        this.isUpdating = false;

        this.goBack();
    }

    goBack() {
        this.router.navigate(['..'], { relativeTo: this.route });
    }

    populateCredentials() {
        if (this.credentials) {
            return;
        }

        this.service.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentials = credentials.documents
            .filter(c => !Utils.areSystemScopedCredentials(c))
            .map(Utils.toCredentialViewModel);
        }).catch((e) => {
            console.log('Credentials retrieval failed', e);
        });
    }

    getVchClusterInputData() {
        var vchHost = this.entity.nodes[this.entity.nodeLinks[0]];
        var hostCopy = Object.assign({}, vchHost);
        hostCopy.customProperties = Object.assign({}, vchHost.customProperties);

        let formInput = this.clusterForm.value;

        if (formInput.name) {
            hostCopy.customProperties['__hostAlias'] = formInput.name;
        }

        if (formInput.description) {
            // TODO
        }

        if (formInput.credentials) {
            hostCopy.customProperties['__authCredentialsLink'] =
                formInput.credentials.documentSelfLink;
        }
        hostCopy.customProperties['__adapterDockerType'] = 'API';

        // allow overwriting with empty value
        hostCopy.customProperties[Constants.hosts.customProperties.publicAddress] =
            formInput.publicAddress || "";

        if (formInput.deploymentPolicy) {
            hostCopy.customProperties[Constants.hosts.customProperties.deploymentPolicyLink] =
                formInput.deploymentPolicy.documentSelfLink;
        } else {
            delete hostCopy.customProperties[Constants.hosts.customProperties.deploymentPolicyLink];
        }

        return hostCopy;
    }

    private updateVchClusterCredentials() {
        let credentialsLink =
            this.entity.nodes[this.entity.nodeLinks[0]].customProperties['__authCredentialsLink'];
        let updatedCredentialsLink = this.clusterForm.value.credentials
                                ? this.clusterForm.value.credentials.documentSelfLink : "";

        if (credentialsLink !== updatedCredentialsLink) {
            // credentials have been changed
            let hostState = this.getVchClusterInputData();

            let hostSpec = {
                'hostState': hostState,
                'isUpdateOperation': true
            };

            this.service.put(Links.CONTAINER_HOSTS, hostSpec).then((response) => {
                this.isUpdating = false;
                this.goBack();
            }).catch(error => {
                this.isUpdating = false;
                this.showErrorMessage(error);
            });
        } else {
            this.isUpdating = false;
            this.goBack();
        }
    }

    private showErrorMessage(error) {
        this.showAlertMessage(Constants.alert.type.DANGER, Utils.getErrorMessage(error)._generic);
    }

    private showAlertMessage(type, text) {
        this.alertType = type;
        this.alertMessage = text;
    }

    resetAlert() {
        this.alertType = null;
        this.alertMessage = null;
    }
}
