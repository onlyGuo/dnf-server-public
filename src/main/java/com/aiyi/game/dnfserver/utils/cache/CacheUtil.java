package com.aiyi.game.dnfserver.utils.cache;

import com.aiyi.core.exception.FlowException;
import com.aiyi.core.exception.ServiceInvokeException;
import com.aiyi.core.exception.ValidationException;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 缓存工具类
 */
public class CacheUtil {

    private static final Logger logger = LoggerFactory.getLogger(CacheUtil.class);

    private static Executor executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Cache-Local-Lock-Executor");
        }
    });
    private static final Map<String, CacheItem> cacheItemMap = new HashMap<>();
    static {
        new Thread(() -> {
            while (true){
                Set<Map.Entry<String, CacheItem>> entries = CacheUtil.cacheItemMap.entrySet();
                try {
                    for (Map.Entry<String, CacheItem> entry : entries) {
                        String key = entry.getKey();
                        CacheItem item = entry.getValue();
                        if (item.getExpTime() <= System.currentTimeMillis()) {
                            CacheUtil.cacheItemMap.remove(key);
                            logger.debug("Cache expire by [{}]", key);
                            if (item.getCollback() != null) {
                                try {
                                    item.getCollback().run();
                                } catch (Exception e) {
                                    logger.error("Cache expire collback function execute error!", e);
                                }
                            }
                        }
                    }
                }catch (ConcurrentModificationException e){
                    continue;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 输出缓存中的信息
     * @return
     */
    public static String dump(){
        return JSON.toJSONString(cacheItemMap);
    }

    /**
     * 将一个对象放入缓存
     * @param key
     *      缓存键
     * @param value
     *      缓存值
     * @param timeUnit
     *      缓存时间单位
     * @param time
     *      缓存时间
     */
    public static void put(Key key, Object value, TimeUnit timeUnit, long time){
        CacheItem item = new CacheItem();
        item.setValue(value);
        switch (timeUnit){
            case DAYS: time = System.currentTimeMillis() + time * 24 * 60 * 60 * 1000;break;
            case HOURS: time = System.currentTimeMillis() + time * 60 * 60 * 1000;break;
            case MINUTES: time = System.currentTimeMillis() + time * 60 * 1000;break;
            case SECONDS: time = System.currentTimeMillis() + time * 1000;break;
            default: time = System.currentTimeMillis() + time;break;
        }
        item.setExpTime(time);
        CacheUtil.cacheItemMap.put(key.toString(), item);
    }

    /**
     * 将一个对象放入缓存
     * @param key
     *      缓存键
     * @param value
     *      缓存值
     * @param timeUnit
     *      缓存时间单位
     * @param time
     *      缓存时间
     */
    public static void put(Key key, Object value, TimeUnit timeUnit, long time, Runnable runnable){
        CacheItem item = new CacheItem();
        item.setValue(value);
        item.setCollback(runnable);
        switch (timeUnit){
            case DAYS: time = System.currentTimeMillis() + time * 24 * 60 * 60 * 1000;break;
            case HOURS: time = System.currentTimeMillis() + time * 60 * 60 * 1000;break;
            case MINUTES: time = System.currentTimeMillis() + time * 60 * 1000;break;
            case SECONDS: time = System.currentTimeMillis() + time * 1000;break;
            default: time = System.currentTimeMillis() + time;break;
        }
        item.setExpTime(time);
        CacheUtil.cacheItemMap.put(key.toString(), item);
    }

    /**
     * 从缓存中取出对象
     * @param key
     *      缓存Key
     * @param clazz
     *      对象类型
     * @param <T>
     * @return
     */
    public static<T> T get(Key key, Class<T> clazz){
        CacheItem cacheItem = cacheItemMap.get(key.toString());
        if (null == cacheItem){
            return null;
        }
        return cacheItem.getValue(clazz);
    }

    /**
     * 重新设置对象过期时间
     * @param key
     *      缓存的Key
     * @param timeUnit
     *      时间单位
     * @param time
     *      时间值
     */
    public static void expire(Key key, TimeUnit timeUnit, long time){
        Object o = get(key, Object.class);
        if (null != o){
            put(key, o, timeUnit, time);
        }
    }

    /**
     * 使对象立刻过期
     * @param key
     *      对象Key
     */
    public static void expire(Key key){
        put(key, null, TimeUnit.MILLISECONDS, 0);
    }

    /**
     * 全局业务锁
     * @param runner
     *      业务处理内容
     */
    public static <T> T lock(LockRunner<T> runner){
        return lock(5000, Key.as(".default"), runner);
    }

    /**
     * 指定业务空间的业务锁
     * @param key
     *      业务空间KEY
     * @param runner
     *      业务处理内容
     */
    public static <T> T lock(Key key, LockRunner<T> runner){
        return lock(5000, key, runner);
    }

    /**
     * 指定业务空间和处理超时时间的业务锁
     * @param time
     *      超时时间
     * @param key
     *      业务空间KEY
     * @param runner
     *      业务处理内容
     */
    public static <T> T lock(long time, Key key, LockRunner<T> runner){
        key = Key.as("LOCAL.LOCK.TASK", key.toString());
        Integer integer = get(key, Integer.class);
        if (null != integer){
            throw new ValidationException("操作过于频繁");
        }
        put(key, 1, TimeUnit.MILLISECONDS, time);

        FutureTask<T> future=new FutureTask<>(runner::exec);
        executor.execute(future);

        try{
            return future.get(time, TimeUnit.MILLISECONDS);
        }catch (InterruptedException|ExecutionException e) {
            if (e instanceof ExecutionException){
                Throwable cause = ((ExecutionException) e).getCause();
                if (cause instanceof FlowException){
                    throw (FlowException) cause;
                }
            }
            throw new ServiceInvokeException(e.getMessage(), e);
        }catch (TimeoutException e) {
            throw new ServiceInvokeException("业务执行超时", e);
        }finally {
            expire(key);
            future.cancel(true);
        }
    }
}
