/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.serverless.mojo;

import com.alipay.sofa.serverless.biz.SOFAArkServiceContainerSingleton;
import com.alipay.sofa.serverless.biz.SOFAArkTestBiz;
import com.alipay.sofa.serverless.model.SOFAServerlessCompatibleTestMojoConfiguration;
import com.alipay.sofa.serverless.mojo.common.CustomJunit5SummaryGeneratingListener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;

/**
 * @author CodeNoobKing
 * @date 2024/1/15
 */
@Mojo(name = "sofa-serverless-compatible-test", defaultPhase = LifecyclePhase.INTEGRATION_TEST, requiresDependencyResolution = ResolutionScope.COMPILE)
public class SOFAServerlessCompatibleTestMojo extends AbstractMojo {

    @Component
    private ArtifactResolver resolver;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject     project;

    @Parameter(property = "sofaArkCompatibleTestConfigFile", defaultValue = "sofaArkCompatibleTestConfigFile.yaml")
    private String           sofaArkCompatibleTestConfigFile;

    private ObjectMapper     yamlObjectMapper = new ObjectMapper(new YAMLFactory());

    @SneakyThrows
    public URLClassLoader buildURLClassLoader() {
        List<URL> urls = new ArrayList<>();
        urls.add(new File(project.getBuild().getTestOutputDirectory()).toURI().toURL());
        urls.add(new File(project.getBuild().getOutputDirectory()).toURI().toURL());
        for (Artifact artifact : project.getArtifacts()) {
            urls.add(artifact.getFile().toURI().toURL());
        }

        for (URL url : urls) {
            getLog().debug(String.format("%s, BaseClassLoaderUrl", url));
        }

        return new URLClassLoader(urls.toArray(new URL[0]),
        // this is necessary because we are calling test engine programmatically.
        // some classes are required to be loaded by TCCL even when we are setting TCCL to biz classLoader.
            Thread.currentThread().getContextClassLoader());
    }

    @SneakyThrows
    private List<SOFAServerlessCompatibleTestMojoConfiguration> loadConfigs() {
        Path configFilePath = Paths.get(project.getBuild().getTestOutputDirectory(),
            sofaArkCompatibleTestConfigFile);
        byte[] bytes = Files.readAllBytes(configFilePath);
        return yamlObjectMapper.readValue(bytes,
            new TypeReference<List<SOFAServerlessCompatibleTestMojoConfiguration>>() {
            });
    }

    private List<SOFAArkTestBiz> buildTestBiz(URLClassLoader baseClassLoader) {
        List<SOFAServerlessCompatibleTestMojoConfiguration> configs = loadConfigs();

        // if root project classes is not configured to include by class loader
        // then it mused be loaded by base classloader
        List<String> rootProjectClasses = new ArrayList<>();
        rootProjectClasses.add(Paths.get(project.getBuild().getOutputDirectory()).toAbsolutePath()
            .toString());
        rootProjectClasses.add(Paths.get(project.getBuild().getTestOutputDirectory())
            .toAbsolutePath().toString());

        List<SOFAArkTestBiz> result = new ArrayList<>();
        for (SOFAServerlessCompatibleTestMojoConfiguration config : CollectionUtils
            .emptyIfNull(configs)) {
            result.add(new SOFAArkTestBiz(config.getBootstrapClass(), config.getName(), project
                .getVersion(), ListUtils.emptyIfNull(config.getTestClasses()), ListUtils.union(
                config.getLoadByBizClassLoaderPatterns(), rootProjectClasses), baseClassLoader));
        }
        return result;
    }

    @SneakyThrows
    public void executeJunit5() {
        URLClassLoader baseClassLoader = buildURLClassLoader();
        SOFAArkServiceContainerSingleton.init(baseClassLoader);
        List<SOFAArkTestBiz> sofaArkTestBizs = buildTestBiz(baseClassLoader);
        for (SOFAArkTestBiz sofaArkTestBiz : sofaArkTestBizs) {
            getLog().info(String.format("%s, CompatibleTestStarted", sofaArkTestBiz.getIdentity()));

            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.
                    request().
                    selectors(sofaArkTestBiz.
                            getTestClasses().
                            stream().
                            map(DiscoverySelectors::selectClass).
                            toArray(ClassSelector[]::new)
                    ).
                    build();
            Launcher launcher = LauncherFactory.create();
            // Optional: Add a listener for test execution results
            SummaryGeneratingListener listener = new CustomJunit5SummaryGeneratingListener(getLog());
            launcher.registerTestExecutionListeners(listener);
            // the following code would change TCCL to BizClassLoader
            sofaArkTestBiz.executeTest(new Runnable() {
                @Override
                public void run() {
                    launcher.execute(request);
                }
            }).get();
            listener.getSummary().printTo(new PrintWriter(System.out));
        }
    }

    public void execute() {
        executeJunit5();
    }
}
