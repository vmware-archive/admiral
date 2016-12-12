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

package utils

import "reflect"

//Filters
const (
	ApplicationFilter      = "/resources/composite-components?documentType=true&$count=true&$limit=21&$filter=documentSelfLink+eq+"
	BusinessGroupFilter    = "/tenants/%s/subtenants/?$top=1000"
	CertFilter             = "/config/trust-certs?expand&$filter=documentSelfLink+eq+"
	ContainerFilter        = "/resources/containers?documentType=true&$count=true&$limit=10000&$orderby=documentSelfLink+asc&$filter=documentSelfLink+eq+"
	CredentialsFilter      = "/core/auth/credentials?expand&$filter=customProperties/scope%20ne%20%27SYSTEM%27+and+documentSelfLink+eq+"
	DeploymentPolicyFilter = "/resources/deployment-policies?expand&$filter=documentSelfLink+eq+"
	EndpointFilter         = "/config/endpoints?expand=true&$filter=documentSelfLink+eq+"
	HostFilter             = "/resources/compute?documentType=true&$count=true&$limit=1000&$orderby=documentSelfLink%20asc&$filter=descriptionLink%20ne%20%27/resources/compute-descriptions/*-parent-compute-desc%27%20and%20customProperties/__computeHost%20eq%20%27*%27%20and%20customProperties/__computeContainerHost%20eq%20%27*%27+and+documentSelfLink+eq+"
	NetworkFilter          = "/resources/container-networks?expand&$filter=documentSelfLink+eq+"
	PlacementFilter        = "/resources/group-placements?expand&$filter=documentSelfLink+eq+"
	PlacementZoneFilter    = "/resources/elastic-placement-zones-config?documentType=true&expand=true&$filter=documentSelfLink+eq+"
	ProjectFilter          = "/resources/groups?documentType=true&expand=true&$filter=documentSelfLink+eq+"
	RegistryFilter         = "/config/registries?documentType=true&expand=true&$filter=documentSelfLink+eq+"
	RequestFilter          = "/request-status?documentType=true&$count=false&$limit=1000&$orderby=documentExpirationTimeMicros+desc&$filter=taskInfo/stage+eq+'*'+and+documentSelfLink+eq+"
	TemplateFilter         = "/resources/composite-descriptions?expand=true&documentType=true&$filter=parentDescriptionLink+ne+'*'+and+documentSelfLink+eq+"
	ClosureFilter          = "/resources/closures?documentType=true&$count=true&$limit=19&$orderby=&$filter=documentSelfLink+eq+"
)

// Resource Type "Enum"
type ResourceType int

func (rt ResourceType) GetName() string {
	switch reflect.ValueOf(rt).Int() {
	case 1:
		return "Application"
	case 2:
		return "Business Group"
	case 3:
		return "Certificate"
	case 4:
		return "Container"
	case 5:
		return "Credentials"
	case 6:
		return "Deployment Policy"
	case 7:
		return "Endpoint"
	case 8:
		return "Host"
	case 9:
		return "Network"
	case 10:
		return "Placement"
	case 11:
		return "Placement Zone"
	case 12:
		return "Project"
	case 13:
		return "Registry"
	case 14:
		return "Request"
	case 15:
		return "Template"
	case 16:
		return "Closure"
	default:
		return ""
	}
}

const (
	APPLICATION ResourceType = 1 + iota
	BUSINESS_GROUP
	CERTIFICATE
	CONTAINER
	CREDENTIALS
	DEPLOYMENT_POLICY
	ENDPOINT
	HOST
	NETWORK
	PLACEMENT
	PLACEMENT_ZONE
	PROJECT
	REGISTRY
	REQUEST
	TEMPLATE
	CLOSURE
)

const (
	NoElementsFoundMessage = "No elements found."
)
