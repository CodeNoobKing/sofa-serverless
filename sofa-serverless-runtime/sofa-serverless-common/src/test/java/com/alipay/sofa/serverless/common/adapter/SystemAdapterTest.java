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
package com.alipay.sofa.serverless.common.adapter;

import com.alipay.sofa.ark.container.service.classloader.BizClassLoader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

import static org.mockito.Mockito.*;

/**
 *
 * @author gouzhendong.gzd
 * @version $Id: SystemAdapterTest, v 0.1 2023-12-11 15:30 gouzhendong.gzd Exp $
 */
public class SystemAdapterTest {

    @Before
    public void setUp() {
        // MOCK URL_CLASS_LOADER AS BIZ_CLASS_LOADER
        SystemAdapter.BIZ_CLASS_LOADER_NAME = URLClassLoader.class.getName();
    }

    public void runInBizClassLoader(ClassLoader baseClassLoader, ClassLoader bizClassLoader,
                                    Runnable runnable) {
        try {
            Thread.currentThread().setContextClassLoader(bizClassLoader);
            runnable.run();
        } finally {
            Thread.currentThread().setContextClassLoader(baseClassLoader);
        }
    }

    /**
     * 测试系统变量的 ClassLoader 级别的隔离。
     */
    @Test
    public void testSystemAdapter_PropertiesClassLoaderIsolation() throws Exception {

        ClassLoader mainClassLoader = Thread.currentThread().getContextClassLoader();
        SystemAdapter.setBaseClassLoader(mainClassLoader);

        ClassLoader bizAClassLoader = new URLClassLoader(new URL[0]);
        bizAClassLoader = new URLClassLoader(new URL[0], bizAClassLoader);
        ClassLoader bizBClassLoader = new URLClassLoader(new URL[0]);
        bizBClassLoader = new URLClassLoader(new URL[0], bizBClassLoader);

        {
            SystemAdapter.setProperty("foo", "bar");
            SystemAdapter.setProperty("hello", "world");
        }

        {
            Assert.assertEquals("bar", SystemAdapter.getProperty("foo"));
            Assert.assertEquals("world", SystemAdapter.getProperty("hello"));
        }

        runInBizClassLoader(
                mainClassLoader,
                bizAClassLoader,
                () -> {
                    SystemAdapter.setProperty("foo", "bar1");
                    SystemAdapter.setProperty("john", "doe");
                }
        );

        // foo key is tainted in bar1
        runInBizClassLoader(
                mainClassLoader,
                bizAClassLoader,
                () -> {
                    Assert.assertEquals("bar1", SystemAdapter.getProperty("foo"));
                    Assert.assertEquals("doe", SystemAdapter.getProperty("john"));
                    Assert.assertEquals("world", SystemAdapter.getProperty("hello"));
                }
        );

        // base properties is not poluted
        Assert.assertEquals("bar", SystemAdapter.getProperty("foo"));
        Assert.assertNull(SystemAdapter.getProperty("john"));
        Assert.assertEquals("world", SystemAdapter.getProperty("hello"));

        // no key is tainted
        runInBizClassLoader(
                mainClassLoader,
                bizBClassLoader,
                () -> {
                    Assert.assertEquals("bar", SystemAdapter.getProperty("foo"));
                    Assert.assertEquals("world", SystemAdapter.getProperty("hello"));
                    Assert.assertNull(SystemAdapter.getProperty("john"));
                }
        );

        // testSetWhole
        Properties newBaseProperties = new Properties();
        newBaseProperties.setProperty("hello", "base world!");
        SystemAdapter.setProperties(newBaseProperties);
        Assert.assertEquals("base world!", SystemAdapter.getProperty("hello"));

        Properties newBizProperties = new Properties();
        newBizProperties.setProperty("hello", "biz world!");
        runInBizClassLoader(
                mainClassLoader,
                bizAClassLoader,
                () -> {
                    SystemAdapter.setProperties(newBizProperties);
                }
        );

        runInBizClassLoader(
                mainClassLoader,
                bizAClassLoader,
                () -> {
                    Assert.assertEquals("biz world!", SystemAdapter.getProperty("hello"));
                    Assert.assertEquals("biz world!", SystemAdapter.getProperty("hello", "not"));
                    Assert.assertEquals("not", SystemAdapter.getProperty("miss", "not"));
                    Assert.assertTrue(SystemAdapter.getProperties().containsKey("hello"));
                }
        );
        Assert.assertEquals("base world!", SystemAdapter.getProperty("hello"));

        runInBizClassLoader(
                mainClassLoader,
                bizBClassLoader,
                () -> {
                    Assert.assertEquals("base world!", SystemAdapter.getProperty("hello"));
                }
        );

    }

    @Test
    public void testClassLoader_isBase() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader shouldBeMainClassLoader = new URLClassLoader(new URL[0], contextClassLoader);
        SystemAdapter.setBaseClassLoader(shouldBeMainClassLoader);
        Assert.assertEquals(contextClassLoader, SystemAdapter.getClassLoader());
    }

    @Test
    public void testClassLoader_isBiz() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        // MOCK BIZ CLASS LOADER
        SystemAdapter.setBaseClassLoader(contextClassLoader);

        ClassLoader parentClassLoader = new URLClassLoader(new URL[0]);
        ClassLoader mockedClassLoader = new URLClassLoader(new URL[0], parentClassLoader);

        runInBizClassLoader(
                contextClassLoader,
                mockedClassLoader,
                () -> {
                    Assert.assertEquals(parentClassLoader, SystemAdapter.getClassLoader());
                }
        );
    }
}
