import com.jayway.awaitility.Awaitility
import groovy.json.JsonSlurper

import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

String uri = 'https://anypoint.mulesoft.com'
String ME = "/accounts/api/me";
String LOGIN = "/accounts/login";
String REGISTRATION = "/hybrid/api/v1/servers/registrationToken"
String environmentsPath = "/accounts/api/organizations/%s/environments";
String AUTHORIZATION_HEADER = "Authorization";
String SERVERS = "/hybrid/api/v1/servers";
String ENV_ID_HEADER = "X-ANYPNT-ENV-ID";
String ORG_ID_HEADER = "X-ANYPNT-ORG-ID";

target = ClientBuilder.newClient().target(uri).path(LOGIN);
Entity<String> json = Entity.json('{"username": "' + username + '", "password": "' + password + '"}');
response = target.request(MediaType.APPLICATION_JSON_TYPE).
        post(json, String.class);
def bearerToken = new JsonSlurper().parseText(response).access_token
context.bearerToken = bearerToken

target = ClientBuilder.newClient().target(uri).path(ME);
response = target.request(MediaType.APPLICATION_JSON_TYPE).
        header("Authorization", "bearer " + bearerToken).get(String.class);
def orgId = new JsonSlurper().parseText(response).user.organization.id;
context.orgId = orgId

target = ClientBuilder.newClient().target(uri).path(String.format(environmentsPath, orgId));
response = target.request(MediaType.APPLICATION_JSON_TYPE).
        header("Authorization", "bearer " + bearerToken).get(String.class);
def environments = new JsonSlurper().parseText(response).data
def envId = (environments.find { it.name == 'Production' }).id
context.envId = envId

target = ClientBuilder.newClient().target(uri).path(REGISTRATION);
response = target.request(MediaType.APPLICATION_JSON_TYPE).
        header(AUTHORIZATION_HEADER, "bearer " + bearerToken).header(ENV_ID_HEADER, envId).header(ORG_ID_HEADER, orgId).
        get(String.class);
def token = new JsonSlurper().parseText(response).data

muleHome = "target/mule-enterprise-standalone-${muleVersion}"
new File(muleHome + '/conf/mule-agent.jks').delete()
new File(muleHome + '/conf/mule-agent.yml').delete()
assert (muleHome + "/bin/amc_setup -H $token server-name-deploy-undeploy").execute().waitFor() == 0 : 'Failed to pair server'
assert (muleHome + "/bin/mule start").execute().waitFor() == 0 : 'Failed to start Mule'

target = ClientBuilder.newClient().target(uri).path(SERVERS);
response = target.request(MediaType.APPLICATION_JSON_TYPE).
        header(AUTHORIZATION_HEADER, "bearer " + context.bearerToken).header(ENV_ID_HEADER, envId).header(ORG_ID_HEADER, orgId).
        get(String.class)
def serverId = (new JsonSlurper().parseText(response).data.find{ it.name == "server-name-deploy-undeploy"}).id
assert serverId != null : "Server not found"

serverIsRunning = new Callable<Boolean>() {
        public Boolean call() throws Exception {
                def target = ClientBuilder.newClient().target(uri).path(SERVERS + "/$serverId");
                def response = target.request(MediaType.APPLICATION_JSON_TYPE).
                        header(AUTHORIZATION_HEADER, "bearer " + bearerToken).header(ENV_ID_HEADER, envId).header(ORG_ID_HEADER, orgId).
                        get(String.class);
                return new JsonSlurper().parseText(response).data.status == 'RUNNING'
        }
}

Awaitility.await().atMost(4, TimeUnit.MINUTES).until(serverIsRunning)