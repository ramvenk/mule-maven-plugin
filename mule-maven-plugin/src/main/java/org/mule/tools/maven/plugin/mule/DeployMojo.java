/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.tools.maven.plugin.mule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.mule.test.infrastructure.process.MuleProcessController;
import org.mule.tools.maven.plugin.mule.agent.AgentDeployer;
import org.mule.tools.maven.plugin.mule.arm.ArmDeployer;
import org.mule.tools.maven.plugin.mule.cloudhub.CloudhubDeployer;
import org.mule.util.FilenameUtils;

/**
 * Maven plugin to deploy Mule applications to different kind of servers: Standalone (both Community and Enterprise), Clustered, Anypoint Runtime Manager and CloudHub.
 * Main uses are running integration tests and deploying applications.
 * Some of the features are:
 * Download Mule Standalone from a Maven Repository and install it locally.
 * Deploy a Mule application to a server.
 * Undeploy a Mule appliction.
 * Assemble a Mule cluster and deploy applications.
 *
 * @author <a href="mailto:asequeira@gmail.com">Ale Sequeira</a>
 * @see UndeployMojo
 * @see org.mule.test.infrastructure.process.MuleProcessController
 * @since 1.0
 */
@Mojo(name = "deploy", requiresProject = true)
public class DeployMojo extends AbstractMuleMojo {

  private static final long DEFAULT_POLLING_DELAY = 1000;
  private static final Integer MAX_CLUSTER_SIZE = 8;

  @Component
  protected ArchiverManager archiverManager;


  /**
   * When set to true the plugin will use Mule Standalone Community Edition.
   *
   * @since 2.0
   */
  @Parameter(readonly = true, required = false, defaultValue = "false", property = "mule.community")
  protected boolean community;

  /**
   * Maven coordinates for the Mule Runtime distribution to download.
   * You need to specify:
   * <li>groupId</li>
   * <li>artifactId</li>
   * <li>version</li>
   * This parameter and <code>muleVersion</code> are mutual exclusive
   *
   * @since 1.0
   * @deprecated Use the official maven artifact descriptor, if you need to use Community distribution @see community property
   */
  @Parameter(readonly = true)
  private ArtifactDescription muleDistribution;

  /**
   * Deployment timeout in milliseconds.
   *
   * @since 1.0
   */
  @Parameter(property = "mule.deployment.timeout", defaultValue = "60000", required = true)
  protected Long deploymentTimeout;

  /**
   * List of Mule Runtime Standalone command line arguments.
   * Adding a property to this list is the same that adding it to the command line when starting Mule using bin/mule.
   * If you want to add a Mule property don't forget to prepend <code>-M-D</code>.
   * If you want to add a System property for the Wrapper don't forget to prepend <code>-D</code>.
   * <p>
   * Example: <code>&lt;arguments&gt;&lt;argument&gt;-M-Djdbc.url=jdbc:oracle:thin:@myhost:1521:orcl&lt;/argument&gt;&lt;/arguments&gt;</code>
   *
   * @since 1.0
   */
  @Parameter(property = "mule.arguments", required = false)
  protected String[] arguments;

  /**
   * List of external libs (Jar files) to be added to MULE_HOME/user/lib directory.
   *
   * @since 1.0
   */
  @Parameter
  protected List<File> libs = new ArrayList<>();

  /**
   * Region to deploy the application in Cloudhub.
   *
   * @since 2.0
   */
  @Parameter(property = "cloudhub.region", defaultValue = "us-east-1")
  protected String region;

  /**
   * Number of workers for the deployment of the application in Cloudhub.
   *
   * @since 2.0
   */
  @Parameter(property = "cloudhub.workers")
  protected Integer workers = 1;

  /**
   * Type of workers for the deployment of the application in Cloudhub.
   *
   * @since 2.0
   */
  @Parameter(defaultValue = "Medium", property = "cloudhub.workerType")
  protected String workerType;

  /**
   * CloudHub properties.
   * @since 2.0
   *
   */
  @Parameter(required = false)
  protected Map<String, String> properties;


  public void doExecute() throws MojoExecutionException, MojoFailureException {
    initializeApplication();
    initializeEnvironment();
    switch (deploymentType) {
      case standalone:
        standalone();
        break;
      case cluster:
        cluster();
        break;
      case arm:
        arm();
        break;
      case cloudhub:
        cloudhub();
        break;
      case agent:
        agent();
        break;
      default:
        throw new MojoFailureException("Unsupported deployment type: " + deploymentType);
    }
  }

  private void cloudhub() throws MojoFailureException, MojoExecutionException {
    CloudhubDeployer deployer = new CloudhubDeployer(uri, username, password, environment, applicationName, application,
                                                     region, muleVersion, workers, workerType, getLog(), properties,
                                                     businessGroup);
    deployWithDeployer(deployer);
  }

  private void arm() throws MojoFailureException, MojoExecutionException {
    ArmDeployer deployer = new ArmDeployer(uri, username, password, environment, targetType, target, application, applicationName,
                                           getLog(), businessGroup, armInsecure);
    deployWithDeployer(deployer);
  }

  private void agent() throws MojoFailureException, MojoExecutionException {
    AgentDeployer deployer = new AgentDeployer(getLog(), applicationName, application, uri);
    deployWithDeployer(deployer);
  }

  private void deployWithDeployer(AbstractDeployer deployer) throws MojoExecutionException, MojoFailureException {
    if (null != script) {
      executeGroovyScript();
    }
    try {
      deployer.deploy();
    } catch (DeploymentException e) {
      getLog().error("Failed to deploy " + applicationName + ": " + e.getMessage(), e);
      throw new MojoFailureException("Failed to deploy [" + application + "]");
    }
  }

  private void cluster() throws MojoExecutionException, MojoFailureException {
    validateSize();
    File[] muleHomes = new File[size];
    List<MuleProcessController> controllers = new LinkedList();
    for (int i = 0; i < size; i++) {
      File buildDirectory = new File(mavenProject.getBuild().getDirectory(), "mule" + i);
      buildDirectory.mkdir();
      File home = doInstallMule(buildDirectory);
      controllers.add(new MuleProcessController(home.getAbsolutePath(), timeout));
      muleHomes[i] = home;
    }

    renameApplicationToApplicationName();

    if (null != script) {
      executeGroovyScript();
    }
    new ClusterDeployer(muleHomes, controllers, getLog(), application, deploymentTimeout, arguments, DEFAULT_POLLING_DELAY)
        .addLibraries(libs).execute();
  }

  private void validateSize() throws MojoFailureException {
    if (size > MAX_CLUSTER_SIZE) {
      throw new MojoFailureException("Cannot create cluster with more than 8 nodes");
    }
  }

  public void standalone() throws MojoExecutionException, MojoFailureException {
    File muleHome = installMule(new File(mavenProject.getBuild().getDirectory()));
    MuleProcessController mule = new MuleProcessController(muleHome.getAbsolutePath(), timeout);

    renameApplicationToApplicationName();

    Deployer deployer = new Deployer(mule, getLog(), application, deploymentTimeout, arguments, DEFAULT_POLLING_DELAY)
        .addLibraries(libs);
    addDomain(deployer);
    addDependencies(deployer);
    if (null != script) {
      executeGroovyScript();
    }
    deployer.execute();
  }

  private void renameApplicationToApplicationName() throws MojoFailureException {
    if (!FilenameUtils.getBaseName(application.getName()).equals(applicationName)) {
      try {
        File destApplication = new File(application.getParentFile(), applicationName + ".zip");
        FileUtils.copyFile(application, destApplication);
        application = destApplication;
      } catch (IOException e) {
        throw new MojoFailureException("Couldn't rename [" + application + "] to [" + applicationName + "]");
      }
    }
  }

  private File installMule(File buildDirectory) throws MojoExecutionException, MojoFailureException {
    if (muleHome == null) {
      muleHome = doInstallMule(buildDirectory);
    }
    mavenProject.getProperties().setProperty("mule.home", muleHome.getAbsolutePath());
    getLog().info("Using MULE_HOME: " + muleHome);
    return muleHome;
  }

  private File doInstallMule(File buildDirectory) throws MojoExecutionException, MojoFailureException {
    if (muleDistribution == null) {
      if (community) {
        muleDistribution = new ArtifactDescription("org.mule.distributions", "mule-standalone", muleVersion, "tar.gz");
        this.getLog().debug("muleDistribution not set, using default community artifact: " + muleDistribution);
      } else {
        muleDistribution = new ArtifactDescription("com.mulesoft.muleesb.distributions", "mule-ee-distribution-standalone",
                                                   muleVersion, "tar.gz");
        this.getLog().debug("muleDistribution not set, using default artifact: " + muleDistribution);
      }
    }
    unpackMule(muleDistribution, buildDirectory);
    return new File(buildDirectory, muleDistribution.getContentDirectory());
  }

  /**
   * This code was inspired by maven-dependency-plugin GetMojo.
   */
  private void unpackMule(ArtifactDescription muleDistribution, File destDir)
      throws MojoExecutionException, MojoFailureException {
    File src = getDependency(muleDistribution);
    getLog().info("Copying " + src.getAbsolutePath() + " to " + destDir.getAbsolutePath());
    extract(src, destDir, muleDistribution.getType());
  }

  private void extract(File src, File dest, String type)
      throws MojoExecutionException, MojoFailureException {
    try {
      UnArchiver unArchiver = getArchiver(type);
      unArchiver.setSourceFile(src);
      unArchiver.setDestDirectory(dest);
      unArchiver.extract();
    } catch (ArchiverException e) {
      throw new MojoExecutionException("Couldn't extract file " + src + " to " + dest);
    } catch (Exception e) {
      throw new MojoFailureException("Couldn't extract file " + src + " to " + dest);
    }
  }

  private UnArchiver getArchiver(String type) throws MojoExecutionException {
    UnArchiver unArchiver;
    try {
      unArchiver = archiverManager.getUnArchiver(type);
      getLog().debug("Found unArchiver by extension: " + unArchiver);
      return unArchiver;
    } catch (NoSuchArchiverException e) {
      throw new MojoExecutionException("Couldn't find archiver for type: " + type);
    }
  }


}
