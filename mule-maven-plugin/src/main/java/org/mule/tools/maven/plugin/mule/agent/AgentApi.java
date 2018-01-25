/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.tools.maven.plugin.mule.agent;

import org.mule.tools.maven.plugin.mule.AbstractApi;
import org.mule.tools.maven.plugin.mule.ApiException;

import java.io.File;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.maven.plugin.logging.Log;

public class AgentApi extends AbstractApi {

  public static final String APPLICATIONS_PATH = "/mule/applications/";

  private final String uri;

  public AgentApi(Log log, String uri) {
    super(log);
    this.uri = uri;
  }

  public void deployApplication(String applicationName, File file) {
    Response response =
        put(uri, APPLICATIONS_PATH + applicationName, Entity.entity(file, MediaType.APPLICATION_OCTET_STREAM_TYPE));

    if (response.getStatus() != 202) // Created
    {
      throw new ApiException(response, uri + APPLICATIONS_PATH + applicationName);
    }
  }

  public void undeployApplication(String appName) {
    Response response = delete(uri, APPLICATIONS_PATH + appName);

    if (response.getStatus() != 202) {
      throw new ApiException(response, uri + APPLICATIONS_PATH + appName);
    }
  }

}
