/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the 'License').
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import services from 'core/services';
import links from 'core/links';
import utils from 'core/utils';
import { ContainerActions, NavigationActions } from 'actions/Actions';
import ContainersStore from 'stores/ContainersStore'; //eslint-disable-line

describe('Containers integration test', function() {

  var documentsToDelete = [];

  afterEach(function(done) {
    var deletionPromises = documentsToDelete.map(d => services.deleteDocument(d.documentSelfLink));
    Promise.all(deletionPromises).then(done);
  });

  it('it should create a template from a discovered container', function(done) {

    var createdDescription;
    var createdContainer;
    var templateIdNavigation;
    var clonedDescription;

    services.createContainerDescription({
      image: 'voting-app-vote:latest',
      instanceAdapterReference: '/adapters/docker-service',
      publishAll: false,
      name: 'vote-mcm26-30104893346',
      customProperties: {
        DISCOVERED_CONTAINER_UPDATED: 'true'
      }
    }).then((description) => {
      documentsToDelete.push(description);
      createdDescription = description;

      return services.createDocument(links.CONTAINERS, {
        names: ['vote-mcm26-30104893346'],
        descriptionLink: description.documentSelfLink,
        powerState: 'RUNNING'
      });
    }).then((container) => {
      container.documentId = utils.getDocumentId(container.documentSelfLink);
      ContainerActions.openContainerDetails(container.documentId);

      spyOn(NavigationActions, 'openTemplateDetails').and.callFake(function(_, templateId) {
        templateIdNavigation = templateId;
      });

      documentsToDelete.push(container);
      createdContainer = container;

      ContainerActions.createTemplateFromContainer(container);

      return testUtils.waitFor(function() {
        return templateIdNavigation;
      });
    }).then(() => {
      var templateLink = links.COMPOSITE_DESCRIPTIONS + '/' + templateIdNavigation;

      services.loadDocument(createdContainer.documentSelfLink).then((container) => {
        // The description link has not changed
        expect(container.descriptionLink).toEqual(createdDescription.documentSelfLink);
        return services.loadDocument(container.descriptionLink);
      }).then((description) => {
        createdDescription = description;
        // The description parent link is not empty
        expect(createdDescription.parentDescriptionLink).not.toBeUndefined();
        return services.loadDocument(description.parentDescriptionLink);
      }).then((description) => {
        clonedDescription = description;
        // The cloned description parent link is empty...
        expect(clonedDescription.parentDescriptionLink).toBeUndefined();
        // ...and the created one points to it
        expect(createdDescription.parentDescriptionLink).toEqual(
                                                                clonedDescription.documentSelfLink);
        return services.loadDocument(templateLink);
      }).then((template) => {
        expect(template.descriptionLinks).toEqual([clonedDescription.documentSelfLink]);
      }).then(done);
    });
  });
});
