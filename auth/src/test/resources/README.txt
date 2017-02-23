How to register with Lightwave
==============================

a) From your shell, within the directory admiral/auth type:
$ mvn clean install -DskipTests -P lightwave
$ java -jar target/admiral-auth-{VERSION}-lightwave.jar --tenant=vsphere.local --domainController=x.x.x.x --username=administrator@vsphere.local --password=Password1! --configFile=./lightwave-config.properties

Or using all the parameters available:
$ java -jar target/admiral-auth-{VERSION}-lightwave.jar --tenant=vsphere.local --domainController=x.x.x.x --domainControllerPort=443 --username=administrator@vsphere.local --password=Password1! --loginRedirectUrl=http://localhost:8282/sso/token --postLogoutRedirectUrl=http://localhost:8282/sso/ui/logout.html --resourceServer=rs_admiral --configFile=./lightwave-config.properties

b) From your IDE, run the class RegisterWithLightwave with the same arguments like from the shell.