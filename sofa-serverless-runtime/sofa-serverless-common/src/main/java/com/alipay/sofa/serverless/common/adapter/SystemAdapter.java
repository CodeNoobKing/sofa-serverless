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

import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * System 的适配类。
 * - 保持 setProperty 的局部可见性。
 *   如果 ClassLoader A setProperty 了, 那么该值只对 ClassLoader A 可见。
 *   如果基座的 ClassLoader setProperty 了, 那么该值对所有 ClassLoader 可见 (除非被 biz 的 classLoader set 过）。
 *   eg:
 *     如果：
 *     BaseClassLoader: System.setProperty("foo", "bar")
 *     BizAClassLoader: System.setProperty("foo", "bar1")
 *     BizBClassLoader: 没有对 "foo" 进行过 set 操作。
 *     那么:
 *     BaseClassLoader: System.getProperty("foo") -> "bar"
 *     BizAClassLoader: System.getProperty("foo") -> "bar1"
 *     BizBClassLoader: System.getProperty("foo") -> "bar" （读取了 base 的全局的值)。
 * 用法:
 *
 * @author gouzhendong.gzd
 * @version $Id: SystemAdapter, v 0.1 2023-12-11 14:36 gouzhendong.gzd Exp $
 */
public class SystemAdapter {
    private final static AtomicReference<ClassLoader> baseClassLoader = new AtomicReference<>();

    static String BIZ_CLASS_LOADER_NAME = "com.alipay.sofa.ark.container.service.classloader.BizClassLoader";

    public static void setBaseClassLoader(ClassLoader baseClassLoader) {
        SystemAdapter.baseClassLoader.compareAndSet(null, baseClassLoader);
    }

    /**
     * 局部被污染的 Properties 类。
     * 为了兼容多 classLoader 下的 key 写入的局部可见性。
     * 目前只兼容常见的 setProperty 和 getProperty。
     */
    public static final class TaintedProperties extends Properties {
        public TaintedProperties(Properties tainted) {
            super(System.getProperties());
            this.putAll(tainted);
        }
    }

    /**
     * 命名为被污染的 Properties。
     * 只有被 setProperty 过的 key 才会被记录。
     * 否则直接读取默认的 System.getProperty 值。
     */
    private static final ConcurrentHashMap<ClassLoader, Properties> taintedProperties = new ConcurrentHashMap<>();

    /**
     * 如果 parent 的 classLoader 链中有 BizClassLoader 类型。
     * 那么返回这个 BizClassLoader 类型（因为中间件适配中很有可能拓展了 BizClassLoader)。
     * @return 当前 classLoader 或者 parent 链中的 bizClassLoader。
     */
    public static ClassLoader getClassLoader() {
        ClassLoader threadContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader currentClassLoader = threadContextClassLoader;
        while (currentClassLoader.getParent() != null) {
            currentClassLoader = currentClassLoader.getParent();
            // 此处无法用 instanceOf 判断。
            // 用全称判断。
            if (BIZ_CLASS_LOADER_NAME.equals(currentClassLoader.getClass().getName())) {
                return currentClassLoader;
            }

            // 如果 parent 的 classLoader 链中先找到了基座的 classLoader，而没有找到 BizClassLoader。
            // 那么, 认为这是一个基座 classLoader 有关的调用。
            if (currentClassLoader == baseClassLoader.get()) {
                return currentClassLoader;
            }
        }
        return threadContextClassLoader;
    }

    private static boolean isBaseClassLoader(ClassLoader classLoader) {
        return Objects.equals(classLoader, baseClassLoader.get());
    }

    /**
     * 保持 setProperty 的局部可见性。
     * 如果 ClassLoader A setProperty 了, 那么该值只对 ClassLoader A 可见。
     * @param key property 的 key。
     * @param value property 的 value。
     */
    public static void setProperty(String key, String value) {
        ClassLoader classLoader = getClassLoader();
        if (isBaseClassLoader(classLoader)) {
            System.setProperty(key, value);
        } else {
            Properties properties = taintedProperties.computeIfAbsent(classLoader, (unused) -> new TaintedProperties(new Properties()));
            properties.setProperty(key, value);
        }
    }

    /**
     * 保持 getProperty 的局部可见性。
     * @param key property 的 key。
     * @return property 的 value。
     */
    public static String getProperty(String key) {
        ClassLoader classLoader = getClassLoader();
        return taintedProperties.getOrDefault(classLoader, System.getProperties()).getProperty(key);
    }

    /**
     * 保持 getProperty 的局部可见性。
     * @param key property 的 key。
     * @return property 的 value。
     */
    public static String getProperty(String key, String def) {
        ClassLoader classLoader = getClassLoader();
        Properties properties = taintedProperties.getOrDefault(classLoader, System.getProperties());
        return properties.getProperty(key, def);
    }

    /**
     * 保持 getProperties 的局部可见性。
     * @return properties。
     */
    public static Properties getProperties() {
        ClassLoader classLoader = getClassLoader();
        return taintedProperties.getOrDefault(classLoader, System.getProperties());
    }

    /**
     * 保持 setProperties 的局部可见性。
     * @param properties properties。
     */
    public static void setProperties(Properties properties) {
        ClassLoader classLoader = getClassLoader();
        if (isBaseClassLoader(classLoader)) {
            System.setProperties(properties);
        } else {
            taintedProperties.put(classLoader, new TaintedProperties(properties));
        }

    }
}
