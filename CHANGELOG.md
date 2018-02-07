# CHANGELOG

## 1.3.2-SNAPSHOT

## 1.3.1-SNAPSHOT

* The option to [overwrite the public address](https://github.com/vmware/admiral/wiki/Configuration-guide#enable-or-disable-the-option-to-overwrite-the-public-address-for-a-host) for a host is now enabled by default in the standalone version of Admiral.

* Redesigned some of the calls to the Docker API to retry with delays on failure. This now applies to create, remove, start, stop, inspect and connect to network commands for a container.

* Added support for multiple path segments in the names of repositories, e.g. localhost:5000/category/sub-category/repo-name

Docker hub images & tags: https://hub.docker.com/r/vmware/admiral/

* dev - Standalone

* vic_dev - VIC

## 1.3.0

* Introduced the option to [overwrite the public address](https://github.com/vmware/admiral/wiki/Configuration-guide#enable-or-disable-the-option-to-overwrite-the-public-address-for-a-host) for a host (the address on which published ports can be reached). The feature is disabled by default.

Docker hub images & tags: https://hub.docker.com/r/vmware/admiral/

* v1.3.0 - Standalone

* vic_v1.3.0 - VIC

## 1.2.2

* Increased the security by default by TLSv1.0. More information [here](https://github.com/vmware/admiral/wiki/Configuration-guide#allow-usage-of-tls-v10).

* Increased the security by default by not allowing access to plain HTTP registries. More information [here](https://github.com/vmware/admiral/wiki/Configuration-guide#allow-access-to-plain-http-registries).

* Various bug fixes and improvements.

Docker hub images & tags: https://hub.docker.com/r/vmware/admiral/

* dev - Standalone

## 1.2.1

* Bug fixes around PSC integration in VIC.

Docker hub images & tags: https://hub.docker.com/r/vmware/admiral/

* v1.2.1 - Standalone

* vic_v1.2.1 - VIC

## 1.2.0

* Pluggable integration with PSC for SSO support in VIC.

* Added RBAC capabilities.

* Pluggable integration with [Harbor](https://vmware.github.io/harbor/) to configure and manage a default VIC registry.

* Deprecated usage of Placements and Placement Zones in favor of Clusters.

* Promoted the Project entities as a central resources and roles management entity.

* Extended adoption and integration with [Clarity](https://vmware.github.io/clarity/).

* Added new features to the basic authentication service based on local users.

* Bug fixes around VIC integration.

Docker hub images & tags: https://hub.docker.com/r/vmware/admiral/

* v1.2.0 - Standalone

* vic_v1.2.0 - VIC

## 1.1.1

* Bug fixes around VIC integration

## 1.1.0

* Better [VIC](https://vmware.github.io/vic-product/) integration

* Add support for [VCH](https://vmware.github.io/vic/) host

* Masthead integration with [Harbor](https://vmware.github.io/harbor/)

* Various bug fixes and improvements.

## 0.9.5

* Add ability to provision and manage Docker hosts on AWS, Azure and vSphere.

* Simplify adding of existing hosts, by auto configure them over SSH. More information [here](https://github.com/vmware/admiral/wiki/User-guide#automatic-configuration-over-ssh)

* Add a new configuration and runtime element - Closure. Ability to execute code in a stateless manner using different programming languages. Integrated in the container template and can be used to tweak the containers and their configuration at provisioning time.

* Redesigned the UI and integrating [Clarity](https://vmware.github.io/clarity/).

* Added UI for listing tags of images.

* Add the administrative Xenon UI

* Communication to the agent is now over SSL.

* Reduced the size of the Admiral agent.

* Reduced memory footprint.

* Various bug fixes and improvements.

## 0.9.1

* Added Admiral CLI, **a command line tool to manage and automate Admiral**. More information [here] (https://github.com/vmware/admiral/blob/master/cli/README.md).

* Groups are now Projects

* (Group Resource) Policies are now (Group Resource) Placements and Resource pools are now Placement Zones.

* Added support for tag-based placement zones.

* Add support for single and multi host user defined application networking using native Docker networking. Support for Docker compose container networking configuration.

  More information [here](https://github.com/vmware/admiral/wiki/User-guide#networking)

* Add UI and Services for managing networks

* Automatic discovery of existing networks

* Improved clustering

* Batch operations on UI list elements

* Enabled encryption of sensitive document properties

* Small usability enhancements and bugfixes

## 0.5.0

* Initial open source release.

Docker hub image: https://hub.docker.com/r/vmware/admiral/ v.0.5.0

All binaries: https://bintray.com/vmware/admiral/admiral#files/com/vmware/admiral

Admiral server: admiral-host-0.5.0-uber-jar-with-agent.jar