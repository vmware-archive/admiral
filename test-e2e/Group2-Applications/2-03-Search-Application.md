Test 2-03 - Search Applications
=======

# Purpose:
To verify that search application within Admiral UI works as expected

# References:
[1 - VMware VIC Documentation](https://vmware.github.io/vic-product/assets/files/html/1.2/index.html)

# Environment:
This test requires that a vSphere server is running and available and VIC OVA is installed

# Test Steps:
1. Create several applications with Admiral
2. Navigate to the applications page and search for a specific application that exists
3. Attempt a search for an application that does not exist

# Expected Outcome:
* Verify in Step 2 that the user sees the desired application and only the desired application
* Verify in Step 3 that the user does not see any applications at all

# Possible Problems:
None






