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

import { Component, OnInit, OnDestroy, AfterViewInit, ViewEncapsulation } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { Constants } from '../../../utils/constants';
import { DocumentService } from '../../../utils/document.service';
import { FT } from "../../../utils/ft";
import { Links } from '../../../utils/links';
import { Utils } from "../../../utils/utils";
import * as I18n from 'i18next';

@Component({
  selector: 'app-cluster-edit',
  templateUrl: './cluster-edit.component.html',
  styleUrls: ['./cluster-edit.component.scss'],
  encapsulation: ViewEncapsulation.None,
})
/**
 * Modal for editing clusters.
 */
export class ClusterEditComponent extends BaseDetailsComponent
                                    implements AfterViewInit, OnInit, OnDestroy {
    opened: boolean = false;
    credentials: any[];
    // certificate
    showCertificateWarning: boolean;
    certificate: any;
    // alert
    alertMessage: string;
    alertType: string;
    // actions
    isVerifyingHost: boolean;
    isHostVerified: boolean;
    isSavingHost: boolean;
    // private
    private sub: any;
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

    constructor(private router: Router, route: ActivatedRoute, service: DocumentService) {
        super(route, service, Links.CLUSTERS);
    }

    get title() {
        return FT.isVic() ? 'clusters.edit.titleEditVic' : 'clusters.edit.titleEdit';
    }

    get showPublicAddressField(): boolean {
        return FT.isHostPublicUriEnabled() && this.isSingleHostCluster;
    }

    get isKubernetesHostOptionEnabled(): boolean {
        return FT.isKubernetesHostOptionEnabled();
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

    ngOnInit() {
        this.sub = this.route.params.subscribe(params => {
            let projectId = params['projectId'];
            if (projectId) {
                this.projectLink = Links.PROJECTS + '/' + projectId;
            }
            super.ngOnInit();
        });

        this.populateCredentials();
    }

    ngOnDestroy() {
        this.sub.unsubscribe();
    }

    ngAfterViewInit() {
        this.opened = true;
        this.showCertificateWarning = false;
    }

    verifyCluster() {
        if (this.clusterForm.valid) {
            this.isVerifyingHost = true;
            this.isHostVerified = false;

            let host = this.getVchClusterInputData();
            let hostSpec = {
                'hostState': host
            };

            this.service.put(Links.CONTAINER_HOSTS + '?validate=true', hostSpec)
                .then((response) => {
                this.isVerifyingHost = false;
                this.isHostVerified = true;

                this.alertType = Constants.alert.type.SUCCESS;
                this.alertMessage = I18n.t('hosts.verified');
            }).catch(error => {
                this.isVerifyingHost = false;

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
                let publicAddress = this.clusterForm.value.publicAddress || '';
                clusterDtoPatch[Constants.clusters.properties.publicAddress] = publicAddress;
            }

            this.isSavingHost = true;
            this.service.patch(this.entity.documentSelfLink, clusterDtoPatch, this.projectLink)
                .then(() => {
                this.onClusterUpdateSuccess();

            }).catch(error => {
                this.onClusterUpdateError(error);
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

            this.isSavingHost = true;
            this.service.patch(this.entity.documentSelfLink, clusterDtoPatch, this.projectLink)
                .then(() => {
                this.updateVchClusterCredentials();

            }).catch(error => {
                this.onClusterUpdateError(error);
            });
        }
    }

    cancelCreateCluster() {
        this.showCertificateWarning = false;
        this.isSavingHost = false;
    }

    acceptCertificate() {
        this.showCertificateWarning = false;
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

    toggleModal(open) {
        this.opened = open;

        if (!open) {
            const PATH_UP = '../../';

            let path: any[] = [PATH_UP];
            path = [PATH_UP + Utils.getDocumentId(this.entity.documentSelfLink)];

            this.router.navigate(path, { relativeTo: this.route });
        }
    }

    onClusterUpdateSuccess() {
        this.clearView();

        this.toggleModal(false);
    }

    onClusterUpdateError(error) {
        this.isSavingHost = false;

        this.showErrorMessage(error);
    }

    clearView() {
        this.resetAlert();

        this.isSavingHost = false;
        this.isVerifyingHost = false;
        this.isHostVerified = true;

        this.clusterForm.reset();
        this.clusterForm.markAsPristine();
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

    private showErrorMessage(error) {
        this.alertType = Constants.alert.type.DANGER;
        this.alertMessage = Utils.getErrorMessage(error)._generic;
    }

    private updateVchClusterCredentials() {
        let persistedCredsLink = this.entity.nodes[this.entity.nodeLinks[0]].customProperties['__authCredentialsLink'];
        let formCredentials = this.clusterForm.value.credentials ? this.clusterForm.value.credentials.documentSelfLink : "";
        if(persistedCredsLink !== formCredentials) {
            var hostState = this.getVchClusterInputData();

            let hostSpec = {
                'hostState': hostState,
                'isUpdateOperation': true
            };

            this.isSavingHost = true;
            this.service.put(Links.CONTAINER_HOSTS, hostSpec).then((response) => {
                this.onClusterUpdateSuccess();

            }).catch(error => {
                this.onClusterUpdateError(error);
            });
        } else {
            this.onClusterUpdateSuccess();
        }
    }

    resetAlert() {
        this.alertMessage = null;
    }
}
