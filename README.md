#  Play 2 Hello World clickstart

This clickstart sets up a SBT build service, repository and a basic Play 2 application.

<a href="https://grandcentral.cloudbees.com/?CB_clickstart=https://raw.github.com/CloudBees-community/play2-clickstart/master/clickstart.json"><img src="https://d3ko533tu1ozfq.cloudfront.net/clickstart/deployInstantly.png"/></a>

Launch this clickstart and glory could be yours too ! Use it as a building block if you like.

You can launch this on Cloudbees via a clickstart automatically, or follow the instructions below. 

# Deploying manually: 

## To build and deploy this on CloudBees, follow those steps:

Create application:

    bees app:create MYAPP_ID

Create a new software project in Jenkins, changing the following:

* Add this git repository (or yours, with this code) on Jenkins
* Change JDK to:
    
        Oracle JDK 1.7 (Latest)
    
* Add an "Execute Shell" build step with:
    
        java -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=384M -jar /opt/sbt/sbt-launch-0.11.3-2.jar -Dsbt.log.noformat=true dist
    
* Also add a post-build step "Deploy to CloudBees" with those parameters:

        Applications: First Match
        Application Id: MYAPP_ID
        Filename Pattern: dist/*.zip
    
Then finally update your application from your own computer:
    
    bees config:set -a MYAPP_ID -Rjava_version=1.7 containerType=play2 proxyBuffering=false
    bees app:restart MYAPP_ID

## To build this locally:

You will need play2 installed, or sbt (this jenkins build currently uses SBT).

In the play2-clickstart directory, open a command line, and then type:

    play dist

Then deploy it on cloudbees typing:

    bees app:deploy -a MYAPP_ID -t play2 -Rjava_version=1.7 dist/*.zip proxyBuffering=false

## To run this locally:


You will need a locally running MySQL server for this instance. 


Use the following command, and then browse to localhost:9000

    play run
