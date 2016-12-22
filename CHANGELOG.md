# CHANGELOG

## 0.9.2
* Various bug fixes related to document and service replication consistency in an SSL enabled cluster environment. Minor UI bug fixes.

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