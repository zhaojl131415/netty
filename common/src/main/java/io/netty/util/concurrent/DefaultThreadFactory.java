/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.util.concurrent;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.util.Locale;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ThreadFactory} implementation with a simple naming rule.
 */
public class DefaultThreadFactory implements ThreadFactory {

    private static final AtomicInteger poolId = new AtomicInteger();

    private final AtomicInteger nextId = new AtomicInteger();
    private final String prefix;
    private final boolean daemon;
    private final int priority;
    protected final ThreadGroup threadGroup;

    public DefaultThreadFactory(Class<?> poolType) {
        this(poolType, false, Thread.NORM_PRIORITY);
    }

    public DefaultThreadFactory(String poolName) {
        this(poolName, false, Thread.NORM_PRIORITY);
    }

    public DefaultThreadFactory(Class<?> poolType, boolean daemon) {
        this(poolType, daemon, Thread.NORM_PRIORITY);
    }

    public DefaultThreadFactory(String poolName, boolean daemon) {
        this(poolName, daemon, Thread.NORM_PRIORITY);
    }

    /**
     *
     * @param poolType NioEventLoopGroup.class
     * @param priority Thread.MAX_PRIORITY 线程最高优先级 10
     */
    public DefaultThreadFactory(Class<?> poolType, int priority) {
        // deamon： 表示通过工厂创建的线程是否是守护线程false
        this(poolType, false, priority);
    }

    public DefaultThreadFactory(String poolName, int priority) {
        this(poolName, false, priority);
    }

    /**
     *
     * @param poolType NioEventLoopGroup.class
     * @param daemon 表示通过工厂创建的线程是否是守护线程false
     * @param priority Thread.MAX_PRIORITY 线程最高优先级 10
     */
    public DefaultThreadFactory(Class<?> poolType, boolean daemon, int priority) {
        // toPoolName(poolType): NioEventLoopGroup -> nioEventLoopGroup
        this(toPoolName(poolType), daemon, priority);
    }

    /**
     * 根据类获取类名，并把首字母转换为小写
     * @param poolType
     * @return
     */
    public static String toPoolName(Class<?> poolType) {
        ObjectUtil.checkNotNull(poolType, "poolType");
        // 获取类名
        String poolName = StringUtil.simpleClassName(poolType);
        switch (poolName.length()) {
            case 0:
                return "unknown";
            case 1:
                // 转换为小写
                return poolName.toLowerCase(Locale.US);
            default:
                // isUpperCase 判断指定字符是否为大写字母
                // isLowerCase 判断指定字符是否为小写字母
                if (Character.isUpperCase(poolName.charAt(0)) && Character.isLowerCase(poolName.charAt(1))) {
                    return Character.toLowerCase(poolName.charAt(0)) + poolName.substring(1);
                } else {
                    return poolName;
                }
        }
    }

    /**
     *
     * @param poolName  nioEventLoopGroup
     * @param daemon false
     * @param priority Thread.MAX_PRIORITY 线程最高优先级 10
     * @param threadGroup
     */
    public DefaultThreadFactory(String poolName, boolean daemon, int priority, ThreadGroup threadGroup) {
        ObjectUtil.checkNotNull(poolName, "poolName");

        if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException(
                    "priority: " + priority + " (expected: Thread.MIN_PRIORITY <= priority <= Thread.MAX_PRIORITY)");
        }
        // poolId.incrementAndGet() 原子计数器
        // nioEventLoopGroup-(原子计数器++)-
        prefix = poolName + '-' + poolId.incrementAndGet() + '-';
        this.daemon = daemon;
        this.priority = priority;
        this.threadGroup = threadGroup;
    }

    public DefaultThreadFactory(String poolName, boolean daemon, int priority) {
        // System.getSecurityManager() == null 判断是否有安全管理器
        this(poolName, daemon, priority, System.getSecurityManager() == null ?
                Thread.currentThread().getThreadGroup() : System.getSecurityManager().getThreadGroup());
    }

    /**
     * 创建线程
     * @param r
     * @return
     */
    @Override
    public Thread newThread(Runnable r) {
        Thread t = newThread(FastThreadLocalRunnable.wrap(r), prefix + nextId.incrementAndGet());
        try {
            if (t.isDaemon() != daemon) {
                t.setDaemon(daemon);
            }

            if (t.getPriority() != priority) {
                t.setPriority(priority);
            }
        } catch (Exception ignored) {
            // Doesn't matter even if failed to set.
        }
        return t;
    }

    protected Thread newThread(Runnable r, String name) {
        return new FastThreadLocalThread(threadGroup, r, name);
    }
}
