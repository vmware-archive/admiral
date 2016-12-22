package cmd

import (
	"errors"

	"admiral/endpoints"

	"github.com/spf13/cobra"
)

var (
	MissingEndpointNameError = errors.New("Endpoint name not provided.")
	MissingEndpointIdError   = errors.New("Endpoint ID not provided.")
)

const (
	EndpointAddedMessage   = "Endpoint added: "
	EndpointUpdatedMessage = "Endpoint updated: "
	EndpointRemovedMessage = "Endpoint removed: "
)

func init() {
	initEndpointList()
	initEndpointAddAws()
	initEndpointAddAzure()
	initEndpointAddVsphere()
	initEndpointRemove()
	initEndpointUpdateAws()
	initEndpointUpdateAzure()
	initEndpointUpdateVsphere()
}

var endpointListCmd = &cobra.Command{
	Use:   "ls",
	Short: "List endpoints.",
	Long:  "List endpoints.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunEndpointList(args)
		formatAndPrintOutput(output, err)
	},
}

func initEndpointList() {
	EndpointRootCmd.AddCommand(endpointListCmd)
}

func RunEndpointList(args []string) (string, error) {
	el := &endpoints.EndpointList{}
	_, err := el.FetchEndpoints()
	if err != nil {
		return "", err
	}
	return el.GetOutputString(), nil
}

var endpointAddAwsCmd = &cobra.Command{
	Use:   "aws [NAME]",
	Short: "Add AWS endpoint.",
	Long:  "Add AWS endpoint.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunEndpointAddAws(args)
		processOutput(output, err)
	},
}

func initEndpointAddAws() {
	endpointAddAwsCmd.Flags().StringVar(&accessKey, "access-key", "", required+accessKeyDesc)
	endpointAddAwsCmd.Flags().StringVar(&secretKey, "secret-key", "", required+secretKeyDesc)
	endpointAddAwsCmd.Flags().StringVar(&regionId, "region-id", "", required+regionIdDesc)
	EndpointRootAddCmd.AddCommand(endpointAddAwsCmd)
}

func RunEndpointAddAws(args []string) (string, error) {
	var (
		name string
		ok   bool
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", MissingEndpointNameError
	}

	id, err := endpoints.AddAwsEndpoint(name, accessKey, secretKey, regionId)
	return EndpointAddedMessage + id, err
}

var endpointAddAzureCmd = &cobra.Command{
	Use:   "azure [NAME]",
	Short: "Add Azure endpoint.",
	Long:  "Add Azure endpoint.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunEndpointAddAzure(args)
		processOutput(output, err)
	},
}

func initEndpointAddAzure() {
	endpointAddAzureCmd.Flags().StringVar(&accessKey, "access-key", "", required+accessKeyDesc)
	endpointAddAzureCmd.Flags().StringVar(&secretKey, "secret-key", "", required+secretKeyDesc)
	endpointAddAzureCmd.Flags().StringVar(&regionId, "region-id", "", required+regionIdDesc)
	endpointAddAzureCmd.Flags().StringVar(&subscriptionId, "subscription-id", "", required+subscriptionIdDesc)
	endpointAddAzureCmd.Flags().StringVar(&endpointTenantId, "tenant-id", "", required+endpointTenantIdDesc)
	EndpointRootAddCmd.AddCommand(endpointAddAzureCmd)
}

func RunEndpointAddAzure(args []string) (string, error) {
	var (
		name string
		ok   bool
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", MissingEndpointNameError
	}

	id, err := endpoints.AddAzureEndpoint(name, accessKey, secretKey, regionId, subscriptionId, endpointTenantId)
	return EndpointAddedMessage + id, err
}

var endpointAddVsphereCmd = &cobra.Command{
	Use:   "vsphere [NAME]",
	Short: "Add vSphere endpoint.",
	Long:  "Add vSphere endpoint.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunEndpointAddVsphere(args)
		processOutput(output, err)
	},
}

func initEndpointAddVsphere() {
	endpointAddVsphereCmd.Flags().StringVar(&endpointHostName, "hostname", "", required+endpointHostNameDesc)
	endpointAddVsphereCmd.Flags().StringVarP(&endpointUsername, "username", "u", "", required+endpointUsernameDesc)
	endpointAddVsphereCmd.Flags().StringVarP(&endpointPassword, "password", "p", "", required+endpointPasswordDesc)
	endpointAddVsphereCmd.Flags().StringVar(&datacenterName, "datacenter", "", datacenterNameDesc)
	EndpointRootAddCmd.AddCommand(endpointAddVsphereCmd)
}

func RunEndpointAddVsphere(args []string) (string, error) {
	var (
		name string
		ok   bool
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", MissingEndpointNameError
	}

	id, err := endpoints.AddVsphereEndpoint(name, endpointHostName, endpointUsername, endpointPassword, datacenterName)
	return EndpointAddedMessage + id, err
}

var endpointRemoveCmd = &cobra.Command{
	Use:   "rm [ENDPOINT]",
	Short: "Remove endpoint.",
	Long:  "Remove endpoint.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunEndpointRemove(args)
		processOutput(output, err)
	},
}

func initEndpointRemove() {
	EndpointRootCmd.AddCommand(endpointRemoveCmd)
}

func RunEndpointRemove(args []string) (string, error) {
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingEndpointIdError
	}

	id, err := endpoints.RemoveEndpoint(id)
	return EndpointRemovedMessage + id, err
}

var endpointUpdateAwsCmd = &cobra.Command{
	Use:   "aws [ENDPOINT]",
	Short: "Update AWS endpoint.",
	Long:  "Update AWS endpoint.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunEndpointUpdateAws(args)
		processOutput(output, err)
	},
}

func initEndpointUpdateAws() {
	endpointUpdateAwsCmd.Flags().StringVar(&newName, "name", "", newNameDesc)
	endpointUpdateAwsCmd.Flags().StringVar(&accessKey, "access-key", "", prefixNew+accessKeyDesc)
	endpointUpdateAwsCmd.Flags().StringVar(&secretKey, "secret-key", "", prefixNew+secretKeyDesc)
	endpointUpdateAwsCmd.Flags().StringVar(&regionId, "region-id", "", prefixNew+regionIdDesc)
	EndpointRootUpdateCmd.AddCommand(endpointUpdateAwsCmd)
}

func RunEndpointUpdateAws(args []string) (string, error) {
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingEndpointNameError
	}

	id, err := endpoints.EditAwsEndpoint(id, newName, accessKey, secretKey, regionId)
	return EndpointUpdatedMessage + id, err
}

var endpointUpdateAzureCmd = &cobra.Command{
	Use:   "azure [ENDPOINT]",
	Short: "Update Azure endpoint.",
	Long:  "Update Azure endpoint.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunEndpointUpdateAzure(args)
		processOutput(output, err)
	},
}

func initEndpointUpdateAzure() {
	endpointUpdateAzureCmd.Flags().StringVar(&newName, "name", "", newNameDesc)
	endpointUpdateAzureCmd.Flags().StringVar(&accessKey, "access-key", "", prefixNew+accessKeyDesc)
	endpointUpdateAzureCmd.Flags().StringVar(&secretKey, "secret-key", "", prefixNew+secretKeyDesc)
	endpointUpdateAzureCmd.Flags().StringVar(&regionId, "region-id", "", prefixNew+regionIdDesc)
	endpointUpdateAzureCmd.Flags().StringVar(&subscriptionId, "subscription-id", "", prefixNew+subscriptionIdDesc)
	endpointUpdateAzureCmd.Flags().StringVar(&endpointTenantId, "tenant-id", "", prefixNew+endpointTenantIdDesc)
	EndpointRootUpdateCmd.AddCommand(endpointUpdateAzureCmd)
}

func RunEndpointUpdateAzure(args []string) (string, error) {
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingEndpointNameError
	}

	id, err := endpoints.EditAzureEndpoint(id, newName, accessKey, secretKey, regionId, subscriptionId, endpointTenantId)
	return EndpointUpdatedMessage + id, err
}

var endpointUpdateVsphereCmd = &cobra.Command{
	Use:   "vsphere [ENDPOINT]",
	Short: "Update vSphere endpoint.",
	Long:  "Update vSphere endpoint.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunEndpointUpdateVsphere(args)
		processOutput(output, err)
	},
}

func initEndpointUpdateVsphere() {
	endpointUpdateVsphereCmd.Flags().StringVar(&newName, "name", "", newNameDesc)
	endpointUpdateVsphereCmd.Flags().StringVar(&endpointHostName, "hostname", "", prefixNew+endpointHostNameDesc)
	endpointUpdateVsphereCmd.Flags().StringVarP(&endpointUsername, "username", "u", "", prefixNew+endpointUsernameDesc)
	endpointUpdateVsphereCmd.Flags().StringVarP(&endpointPassword, "password", "p", "", prefixNew+endpointPasswordDesc)
	endpointUpdateVsphereCmd.Flags().StringVar(&datacenterName, "datacenter", "", prefixNew+datacenterNameDesc)
	EndpointRootUpdateCmd.AddCommand(endpointUpdateVsphereCmd)
}

func RunEndpointUpdateVsphere(args []string) (string, error) {
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingEndpointNameError
	}

	id, err := endpoints.EditVsphereEndpoint(id, newName, endpointHostName, endpointUsername, endpointPassword, datacenterName)
	return EndpointUpdatedMessage + id, err
}
