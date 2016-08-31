/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.content;

import static java.util.stream.Stream.concat;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;
import static com.vmware.admiral.common.util.ValidationUtils.handleValidationException;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.assertContainersComponentsOnly;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.deserializeCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.deserializeDockerCompose;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.fromCompositeDescriptionToCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.fromCompositeTemplateToCompositeDescription;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.fromCompositeTemplateToDockerCompose;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.fromContainerDescriptionToComponentTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.fromDockerComposeToCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.getYamlType;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.serializeCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.serializeDockerCompose;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.content.CompositeTemplateUtil.YamlType;
import com.vmware.admiral.compute.content.compose.DockerCompose;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Service for parsing a composite template and creating a CompositeDescription (and all its
 * components)
 *
 * TODO need to implement a TaskService that handles creating all the objects and cleaning up on
 * failure
 */
public class CompositeDescriptionContentService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.COMPOSITE_DESC_CONTENT;

    public static final String SELF_LINK_PARAM_NAME = "selfLink";
    public static final String FORMAT_PARAM_NAME = "format";
    public static final String DISPOSITION_PARAM_NAME = "disposition";

    // Header to try to force the download of the file on the browser side with 'attachment' and a
    // default name for the file with 'filename='. But different browsers may behave differently.
    public static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    public static final String CONTENT_DISPOSITION_INLINE = "inline";
    public static final String CONTENT_DISPOSITION_ATTACHMENT = "attachment";
    public static final String CONTENT_DISPOSITION_FILENAME = "; filename=\"%s.yaml\"";

    public static final String TEMPLATE_CONTAINER_TYPE = "Container.Docker";

    // TODO: This type is temporary and must be updated to match the expected network component type
    // when available.
    public static final String TEMPLATE_CONTAINER_NETWORK_TYPE = "Network.Docker";
    public static final String TEMPLATE_CONTAINER_VOLUME_TYPE = "Volume.Docker";

    public static final String FORMAT_DOCKER_COMPOSE_TYPE = "Docker";

    @Override
    public void handleGet(Operation op) {
        Map<String, String> queryParams = UriUtils.parseUriQueryParams(op.getUri());
        String selfLink = queryParams.get(SELF_LINK_PARAM_NAME);
        assertNotEmpty(selfLink, SELF_LINK_PARAM_NAME);
        if (!selfLink.startsWith(UriUtils.URI_PATH_CHAR)) {
            selfLink = UriUtils
                    .buildUriPath(CompositeDescriptionFactoryService.SELF_LINK, selfLink);
        }

        // return YAML as Composite Template by default
        boolean returnDocker = FORMAT_DOCKER_COMPOSE_TYPE.equalsIgnoreCase(
                queryParams.get(FORMAT_PARAM_NAME));

        // return YAML as downloadable attachment by default
        boolean returnInline = CONTENT_DISPOSITION_INLINE.equalsIgnoreCase(
                queryParams.get(DISPOSITION_PARAM_NAME));

        sendRequest(Operation.createGet(this, selfLink).setCompletion((o, ex) -> {
            if (ex != null) {
                op.fail(ex);
                return;
            }

            CompositeDescription description = o.getBody(CompositeDescription.class);
            CompositeTemplate template = fromCompositeDescriptionToCompositeTemplate(description);

            QueryTask queryTask = QueryUtil.buildQuery(ContainerDescription.class, true);
            QueryUtil.addExpandOption(queryTask);
            QueryUtil.addListValueClause(queryTask, ServiceDocument.FIELD_NAME_SELF_LINK,
                    description.descriptionLinks);

            // query for all linked ContainerDescriptions
            template.components = new ConcurrentHashMap<>();

            new ServiceDocumentQuery<ContainerDescription>(
                    getHost(), ContainerDescription.class).query(queryTask, (r) -> {
                        if (r.hasException()) {
                            op.fail(r.getException());
                        } else if (r.hasResult()) {
                            ContainerDescription container = r.getResult();
                            ComponentTemplate<?> component = fromContainerDescriptionToComponentTemplate(
                                    container);
                            template.components.put(container.name, component);
                        } else {
                            // done fetching ContainerDescriptions, serialize the result to YAML
                            // and complete the request
                            try {
                                String content;
                                if (returnDocker) {
                                    DockerCompose compose = fromCompositeTemplateToDockerCompose(
                                            template);
                                    content = serializeDockerCompose(compose);
                                } else {
                                    content = serializeCompositeTemplate(template);
                                }
                                op.setBody(content);
                                op.setContentType(MEDIA_TYPE_APPLICATION_YAML);

                                String contentDisposition = (returnInline
                                        ? CONTENT_DISPOSITION_INLINE
                                        : CONTENT_DISPOSITION_ATTACHMENT)
                                        + CONTENT_DISPOSITION_FILENAME;

                                op.addResponseHeader(CONTENT_DISPOSITION_HEADER,
                                        String.format(contentDisposition, template.name));

                                op.complete();
                            } catch (Exception e) {
                                op.fail(e);
                            }
                        }
                    });
        }));
    }

    @Override
    public void handlePost(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        CompositeTemplate template;
        try {
            if (isApplicationYamlContent(op.getContentType())) {
                String content = op.getBody(String.class);
                YamlType yamlType = getYamlType(content);
                switch (yamlType) {
                case COMPOSITE_TEMPLATE:
                    template = deserializeCompositeTemplate(content);
                    break;
                case DOCKER_COMPOSE:
                    DockerCompose compose = deserializeDockerCompose(content);
                    template = fromDockerComposeToCompositeTemplate(compose);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unknown YAML content type! Only Blueprint and Docker Compose v2 formats are supported.");
                }
            } else {
                try {
                    template = op.getBody(CompositeTemplate.class);
                } catch (Exception e) {
                    logWarning("Failed to deserialize CompositeTemplate serialized content! %s",
                            Utils.toString(e));
                    throw new IllegalArgumentException(
                            "Failed to deserialize CompositeTemplate serialized content!");
                }
            }

            assertNotEmpty(template.name, "name");
            assertContainersComponentsOnly(template.components);
        } catch (Exception e) {
            handleValidationException(op, e);
            return;
        }

        Operation createDescriptionOp = Operation.createPost(this,
                CompositeDescriptionFactoryService.SELF_LINK);

        Operation[] createComponentsOps = createComponents(template.components);

        OperationSequence.create(createComponentsOps)
                .setCompletion((ops, failures) -> {
                    if (failures != null) {
                        op.fail(new IllegalStateException("Failed to create components: "
                                + Utils.toString(failures)));
                    } else {
                        CompositeDescription description = fromCompositeTemplateToCompositeDescription(
                                template);
                        description.descriptionLinks = ops.values().stream()
                                .map((o) -> o.getBody(ContainerDescription.class).documentSelfLink)
                                .collect(Collectors.toList());
                        description.bindings = template.bindings;
                        createDescriptionOp.setBody(description);
                    }
                })
                .next(createDescriptionOp)
                .setCompletion((ops, failures) -> {
                    if (failures != null) {
                        op.fail(new IllegalStateException("Failed to create CompositeDescription: "
                                + Utils.toString(failures)));
                    } else {
                        CompositeDescription description = ops.get(createDescriptionOp.getId())
                                .getBody(CompositeDescription.class);
                        op.addResponseHeader(Operation.LOCATION_HEADER,
                                description.documentSelfLink);
                        op.complete();
                    }
                }).sendWith(this);
    }

    private Operation[] createComponents(Map<String, ComponentTemplate<?>> components) {

        Stream<Operation> networks = components.values().stream()
                .filter(template -> TEMPLATE_CONTAINER_NETWORK_TYPE.equals(template.type))
                .map(component -> Operation
                        .createPost(this, ContainerNetworkDescriptionService.FACTORY_LINK)
                        .setBody(component.data));

        Stream<Operation> volumes = components.values().stream()
                .filter(template -> TEMPLATE_CONTAINER_VOLUME_TYPE.equals(template.type))
                .map(component -> Operation
                        .createPost(this, ContainerVolumeDescriptionService.FACTORY_LINK)
                        .setBody(component.data));

        Stream<Operation> containers = components.values().stream()
                .filter(template -> TEMPLATE_CONTAINER_TYPE.equals(template.type))
                .map(component -> Operation
                        .createPost(this, ContainerDescriptionService.FACTORY_LINK)
                        .setBody(component.data));

        return concat(containers, concat(networks, volumes)).toArray(Operation[]::new);
    }

    private boolean isApplicationYamlContent(String contentType) {
        return (contentType != null)
                && MEDIA_TYPE_APPLICATION_YAML.equals(contentType.split(";")[0]);
    }
}
