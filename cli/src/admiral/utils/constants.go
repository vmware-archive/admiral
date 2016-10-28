package utils

import "reflect"

//Filters
const (
	ApplicationFilter      = "/resources/composite-components?documentType=true&$count=true&$limit=21&$filter=documentSelfLink+eq+"
	CertFilter             = "/config/trust-certs?expand&$filter=documentSelfLink+eq+"
	ContainerFilter        = "/resources/containers?documentType=true&$count=true&$limit=10000&$orderby=documentSelfLink+asc&$filter=documentSelfLink+eq+"
	CredentialsFilter      = "/core/auth/credentials?expand&$filter=customProperties/scope%20ne%20%27SYSTEM%27+and+documentSelfLink+eq+"
	DeploymentPolicyFilter = "/resources/deployment-policies?expand&$filter=documentSelfLink+eq+"
	HostFilter             = "/resources/compute?documentType=true&$count=true&$limit=1000&$orderby=documentSelfLink%20asc&$filter=descriptionLink%20ne%20%27/resources/compute-descriptions/*-parent-compute-desc%27%20and%20customProperties/__computeHost%20eq%20%27*%27%20and%20customProperties/__computeContainerHost%20eq%20%27*%27+and+documentSelfLink+eq+"
	NetworkFilter          = "/resources/container-networks?expand&$filter=documentSelfLink+eq+"
	PlacementFilter        = "/resources/group-placements?expand&$filter=documentSelfLink+eq+"
	PlacementZoneFilter    = "/resources/elastic-placement-zones-config?documentType=true&expand=true&$filter=documentSelfLink+eq+"
	ProjectFilter          = "/resources/groups?documentType=true&expand=true&$filter=documentSelfLink+eq+"
	RegistryFilter         = "/config/registries?documentType=true&expand=true&$filter=documentSelfLink+eq+"
	TemplateFilter         = "/resources/composite-descriptions?expand=true&documentType=true&$filter=parentDescriptionLink+ne+'*'+and+documentSelfLink+eq+"
)

// Resource Type "Enum"
type ResourceType int

func (rt ResourceType) GetName() string {
	switch reflect.ValueOf(rt).Int() {
	case 1:
		return "Application"
	case 2:
		return "Certificate"
	case 3:
		return "Container"
	case 4:
		return "Credentials"
	case 5:
		return "Deployment Policy"
	case 6:
		return "Host"
	case 7:
		return "Network"
	case 8:
		return "Placement"
	case 9:
		return "Placement Zone"
	case 10:
		return "Project"
	case 11:
		return "Registry"
	case 12:
		return "Template"
	default:
		return ""
	}
}

const (
	APPLICATION ResourceType = 1 + iota
	CERTIFICATE
	CONTAINER
	CREDENTIALS
	DEPLOYMENT_POLICY
	HOST
	NETWORK
	PLACEMENT
	PLACEMENT_ZONE
	PROJECT
	REGISTRY
	TEMPLATE
)
