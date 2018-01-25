/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.tools.maven.plugin.mule;


import javax.ws.rs.core.Response;

public class ApiException extends RuntimeException {

  private int statusCode;
  private String reasonPhrase;

  public ApiException(String message, int statusCode, String reasonPhrase) {
    super(String.format("%d %s: %s", statusCode, reasonPhrase, message));

    this.statusCode = statusCode;
    this.reasonPhrase = reasonPhrase;

  }

  public ApiException(Response response, String uri) {
    this(uri, response.getStatusInfo().getStatusCode(), response.getStatusInfo().getReasonPhrase());
  }

  public ApiException(Response response) {
    this(response.readEntity(String.class), response.getStatusInfo().getStatusCode(), response.getStatusInfo().getReasonPhrase());
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getReasonPhrase() {
    return reasonPhrase;
  }
}
