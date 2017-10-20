Test 1-01 - Basic Navigation
=======

# Purpose:
To verify that basic navigation around the admiral UI works as expected

# References:
[1 - VMware VIC Documentation](https://vmware.github.io/vic-product/assets/files/html/1.2/index.html)

# Environment:
This test requires that a vSphere server is running and available and VIC OVA is installed

# Test Steps:
1. Login to the admiral management portal
2. Navigate to the applications, containers, networks, volumes, templates, project repositories, public repositories, container hosts pages
3. Click on Administration tab
4. Navigate to the identity management, projects, registries, configuration, logs pages
5. Click on the username and logout of site

# Expected Outcome:
* Login to the admiral management portal should succeed and the user should land on the home/applications page
* Verify on each page in Step 2 and Step 4 that the page loads properly
* Logging out should be successful and the user should return to the vSphere login page

# Possible Problems:
None






