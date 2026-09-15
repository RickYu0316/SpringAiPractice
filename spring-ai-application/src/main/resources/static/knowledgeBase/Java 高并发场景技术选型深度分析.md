# Java 高并发场景技术选型深度分析

## 一、基础数据结构场景

### 1. 并发Map场景


| 场景特征                | 推荐方案                                           | 原因分析                          | 示例代码                                                                                                                                      |
| ------------------- | ---------------------------------------------- | ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| **高并发读写，弱一致性迭代可接受** | `ConcurrentHashMap`                            | JDK1.8使用CAS+synchronized，锁粒度细 | `Map<String, Object> map = new ConcurrentHashMap<>(16, 0.75f, 16);`                                                                       |
| **需要强一致性迭代，并发度较低**  | `Collections.synchronizedMap(new HashMap<>())` | 方法级同步，迭代时自动同步                 | `Map<String, Object> syncMap = Collections.synchronizedMap(new HashMap<>());`                                                             |
| **有序且高并发**          | `ConcurrentSkipListMap`                        | 跳表实现，有序且并发度高                  | `Map<String, Object> sortedMap = new ConcurrentSkipListMap<>();`                                                                          |
| **读多写极少，允许弱一致性**    | `CopyOnWriteMap`（Guava）                        | 写时复制，读完全无锁                    | `Map<String, Object> map = new ConcurrentHashMap<>(); Map<String, Object> copyOnWrite = Collections.unmodifiableMap(new HashMap<>(map));` |
| **需要LRU淘汰策略**       | `ConcurrentLinkedHashMap` 或 `Caffeine`         | LRU策略+并发安全                    | `Cache<String, Object> cache = Caffeine.newBuilder().maximumSize(1000).build();`                                                          |
| **需要软/弱引用缓存**       | `ConcurrentHashMap` + `SoftReference`          | 内存不足时自动回收                     | `ConcurrentHashMap<String, SoftReference<Object>> cache = new ConcurrentHashMap<>();`                                                     |


### 2. 并发List场景


| 场景特征            | 推荐方案                                              | 原因分析          | 示例代码                                                                            |
| --------------- | ------------------------------------------------- | ------------- | ------------------------------------------------------------------------------- |
| **读多写极少，遍历频繁**  | `CopyOnWriteArrayList`                            | 写时复制，读无锁，遍历安全 | `List<String> list = new CopyOnWriteArrayList<>();`                             |
| **读写都频繁，随机访问多** | `Collections.synchronizedList(new ArrayList<>())` | 方法级同步，随机访问性能好 | `List<String> list = Collections.synchronizedList(new ArrayList<>());`          |
| **并发队列场景**      | `ConcurrentLinkedQueue` 或 `LinkedBlockingQueue`   | 无锁CAS或阻塞队列    | `Queue<String> queue = new ConcurrentLinkedQueue<>();`                          |
| **优先级队列，并发访问**  | `PriorityBlockingQueue`                           | 阻塞+优先级        | `Queue<String> queue = new PriorityBlockingQueue<>(Comparator.reverseOrder());` |
| **延迟队列场景**      | `DelayQueue`                                      | 元素延迟取出        | `DelayQueue<Delayed> delayQueue = new DelayQueue<>();`                          |
| **固定大小，环形缓冲**   | `ArrayBlockingQueue`                              | 数组实现，有界阻塞     | `BlockingQueue<String> queue = new ArrayBlockingQueue<>(1000);`                 |


### 3. 并发Set场景


| 场景特征         | 推荐方案                                           | 原因分析      | 示例代码                                                              |
| ------------ | ---------------------------------------------- | --------- | ----------------------------------------------------------------- |
| **高并发去重**    | `ConcurrentHashMap.newKeySet()`                | 基于CHM，性能好 | `Set<String> set = ConcurrentHashMap.newKeySet();`                |
| **有序并发集合**   | `ConcurrentSkipListSet`                        | 基于跳表，有序   | `Set<String> set = new ConcurrentSkipListSet<>();`                |
| **写极少，读遍历多** | `CopyOnWriteArraySet`                          | 写时复制，遍历安全 | `Set<String> set = new CopyOnWriteArraySet<>();`                  |
| **需要强一致性**   | `Collections.synchronizedSet(new HashSet<>())` | 方法级同步     | `Set<String> set = Collections.synchronizedSet(new HashSet<>());` |


## 二、计数器/累加器场景


| 场景特征           | 推荐方案                              | 原因分析              | 性能对比                                                          |
| -------------- | --------------------------------- | ----------------- | ------------------------------------------------------------- |
| **单机高并发计数**    | `LongAdder`                       | 分段累加，CAS优化，写多场景最优 | 写性能：LongAdder > AtomicLong > synchronized                     |
| **精确计数，并发度中等** | `AtomicLong`                      | CAS操作，读写平衡        | 适合读写比例均衡的场景                                                   |
| **需要原子比较交换**   | `AtomicLong` 或 `AtomicInteger`    | 支持CAS复合操作         | `atomic.compareAndSet(expect, update)`                        |
| **多维度统计**      | `ConcurrentHashMap` + `LongAdder` | 按key分段统计          | `map.computeIfAbsent(key, k -> new LongAdder()).increment();` |
| **分布式计数**      | Redis INCR 或分布式锁                  | 跨JVM统计            | `redisTemplate.opsForValue().increment("counter", 1);`        |
| **高精度浮点计数**    | `DoubleAdder`                     | 浮点版本LongAdder     | `DoubleAdder adder = new DoubleAdder(); adder.add(1.5);`      |


## 三、缓存场景


| 场景特征           | 推荐方案                    | 优点             | 缺点            | 适用场景               |
| -------------- | ----------------------- | -------------- | ------------- | ------------------ |
| **单机内存缓存，高性能** | `Caffeine`              | 高性能，丰富策略，API友好 | 单机            | 单机缓存，替代Guava Cache |
| **单机缓存，功能丰富**  | `Guava Cache`           | 功能完善，社区成熟      | 性能略低于Caffeine | 需要丰富淘汰策略的缓存        |
| **简单内存缓存**     | `ConcurrentHashMap` 自定义 | 灵活，无依赖         | 功能需自行实现       | 简单缓存需求             |
| **堆外内存缓存**     | `MapDB` 或 `OHC`         | 突破堆大小限制        | 序列化开销         | 大数据量缓存             |
| **分布式缓存**      | Redis Cluster           | 分布式，高可用        | 网络开销          | 多实例共享缓存            |
| **多级缓存**       | Caffeine + Redis        | 本地+远程，性能好      | 复杂度高          | 高并发读场景             |
| **堆内缓存+持久化**   | `Ehcache`               | 功能全面，支持持久化     | 较重            | 企业级应用              |


**Spring Boot配置示例：**

```java
@Configuration
public class CacheConfig {
    // 场景1：简单本地缓存
    @Bean
    public Cache<String, Object> simpleCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats()
            .build();
    }
    
    // 场景2：带刷新功能的缓存
    @Bean
    public LoadingCache<String, Object> loadingCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .build(key -> loadFromDB(key));
    }
    
    // 场景3：软引用缓存（内存敏感）
    @Bean
    public Cache<String, Object> softRefCache() {
        return Caffeine.newBuilder()
            .softValues()  // 内存不足时回收
            .maximumSize(10_000)
            .build();
    }
}
```

## 四、任务/线程池场景


| 场景特征         | 推荐方案                                              | 线程池配置                        | 监控要点          |
| ------------ | ------------------------------------------------- | ---------------------------- | ------------- |
| **CPU密集型计算** | `FixedThreadPool`                                 | 核心线程数 = CPU核数+1              | CPU使用率，队列堆积   |
| **IO密集型任务**  | `CachedThreadPool` 或 自定义                          | 核心线程数可较多，队列用SynchronousQueue | 线程创建数，执行时间    |
| **定时/延迟任务**  | `ScheduledThreadPoolExecutor`                     | 核心线程数按任务数配置                  | 任务执行时间，调度精度   |
| **需要优先级调度**  | 自定义`ThreadPoolExecutor` + `PriorityBlockingQueue` | 优先级队列                        | 高优先级任务等待时间    |
| **批量短任务**    | `ForkJoinPool`                                    | 工作窃取，适合可拆分任务                 | 任务拆分粒度，工作负载均衡 |
| **资源隔离场景**   | 多个专用线程池                                           | 按业务类型隔离                      | 各池资源使用情况      |
| **需要优雅关闭**   | 自定义拒绝策略 + 关闭钩子                                    | 使用CallerRunsPolicy或自定义策略     | 关闭时等待时间，任务丢失率 |


**配置示例：**

```java
// 场景1：IO密集型（Web服务器）
@Bean
public ThreadPoolExecutor ioIntensiveExecutor() {
    int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
    int maxPoolSize = 200;
    int queueCapacity = 1000;
    
    return new ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(queueCapacity),
        new NamedThreadFactory("io-pool-"),
        new ThreadPoolExecutor.CallerRunsPolicy()  // 让调用线程执行
    );
}

// 场景2：CPU密集型计算
@Bean
public ThreadPoolExecutor cpuIntensiveExecutor() {
    int corePoolSize = Runtime.getRuntime().availableProcessors();
    
    return new ThreadPoolExecutor(
        corePoolSize,
        corePoolSize,  // 固定大小
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(1000),
        new NamedThreadFactory("cpu-pool-"),
        new ThreadPoolExecutor.AbortPolicy()  // 拒绝并抛异常
    );
}

// 场景3：定时任务
@Bean
public ScheduledThreadPoolExecutor scheduledExecutor() {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
        4,  // 核心线程数
        new NamedThreadFactory("scheduler-")
    );
    executor.setRemoveOnCancelPolicy(true);  // 取消后立即移除
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    return executor;
}
```

## 五、锁/同步场景


| 场景特征          | 推荐方案                                     | 锁类型       | 适用场景              |
| ------------- | ---------------------------------------- | --------- | ----------------- |
| **简单代码块同步**   | `synchronized`                           | 悲观锁，JVM内置 | 简单同步，竞争不激烈        |
| **可重入，灵活控制**  | `ReentrantLock`                          | 悲观锁，API丰富 | 需要尝试锁、超时、公平锁等特性   |
| **读写分离，读多写少** | `ReentrantReadWriteLock` 或 `StampedLock` | 读写锁       | 读多写少场景            |
| **乐观读，写少读多**  | `StampedLock`                            | 乐观锁       | 读非常多，写很少，可容忍数据不一致 |
| **分布式锁**      | Redis RedLock 或 ZooKeeper                | 分布式锁      | 跨JVM同步            |
| **数据库乐观锁**    | 版本号/时间戳                                  | 乐观锁       | 数据库更新，避免ABA问题     |
| **无锁编程**      | `Atomic` 类 + CAS                         | 无锁        | 简单原子操作，高性能要求      |


**性能对比表：**

```java
// 性能测试结果（假设场景：读占80%，写占20%）
// 1. synchronized: 吞吐量 1000 ops/sec
// 2. ReentrantLock: 吞吐量 1200 ops/sec
// 3. ReentrantReadWriteLock: 吞吐量 3000 ops/sec
// 4. StampedLock(乐观读): 吞吐量 5000 ops/sec
// 5. StampedLock(悲观读): 吞吐量 2800 ops/sec
```

## 六、消息/事件处理场景


| 场景特征        | 推荐方案             | 核心组件                        | 特点                  |
| ----------- | ---------------- | --------------------------- | ------------------- |
| **本地事件总线**  | Spring Event     | `ApplicationEventPublisher` | 简单，同步/异步可选，Spring集成 |
| **高性能消息队列** | Disruptor        | `RingBuffer`                | 无锁，内存队列，超高吞吐        |
| **复杂事件处理**  | Reactor / RxJava | `Flux` / `Observable`       | 响应式，背压支持            |
| **任务分发**    | ExecutorService  | `ThreadPoolExecutor`        | 内置，功能完善             |
| **发布订阅模式**  | Guava EventBus   | `EventBus`                  | 简单，进程内发布订阅          |
| **流式处理**    | Akka Streams     | `Source`, `Flow`, `Sink`    | 流处理，背压，容错           |
| **分布式消息**   | Kafka/RocketMQ   | Topic/Partition             | 分布式，持久化，高吞吐         |


**选型决策树：**

```
是否需要跨进程通信？
├── 是 → 使用Kafka/RocketMQ
└── 否 → 是否需要超高吞吐（百万级/秒）？
    ├── 是 → 使用Disruptor
    └── 否 → 是否需要响应式/背压？
        ├── 是 → 使用Reactor/RxJava
        └── 否 → 是否需要Spring集成？
            ├── 是 → 使用Spring Event
            └── 否 → 使用Guava EventBus
```

## 七、流处理/批处理场景


| 场景特征                | 推荐方案              | 框架         | 适用场景        |
| ------------------- | ----------------- | ---------- | ----------- |
| **微批处理**            | Spring Batch      | 批处理框架      | 定时批处理，ETL作业 |
| **实时流处理**           | Flink/Storm       | 流处理引擎      | 实时计算，CEP    |
| **简单流处理**           | Java Stream + 并行流 | Stream API | 内存内数据并行处理   |
| **内存计算**            | Spark             | 内存计算引擎     | 迭代计算，机器学习   |
| **SQL over Stream** | ksqlDB            | 流式SQL      | 用SQL处理流数据   |
| **复杂事件处理**          | Esper/Drools      | CEP引擎      | 模式匹配，规则引擎   |


## 八、数据库并发场景


| 场景特征      | 推荐方案              | 技术栈                       | 注意事项         |
| --------- | ----------------- | ------------------------- | ------------ |
| **高并发读**  | 多级缓存 + 读写分离       | Redis + MySQL主从           | 缓存穿透/击穿/雪崩防护 |
| **高并发写**  | 分库分表 + 队列削峰       | ShardingSphere + RocketMQ | 分片键选择，事务处理   |
| **分布式事务** | Seata/TCC         | 分布式事务框架                   | 性能开销，最终一致性   |
| **连接池管理** | HikariCP/Druid    | 连接池                       | 连接数配置，监控     |
| **乐观锁控制** | 版本号字段             | 数据库行版本                    | 重试机制，ABA问题   |
| **悲观锁控制** | SELECT FOR UPDATE | 行级锁                       | 死锁风险，性能影响    |
| **唯一性约束** | 数据库唯一索引 + 幂等      | 唯一索引                      | 先查后插的并发问题    |
| **批量操作**  | 批处理 + 分页          | JdbcTemplate batchUpdate  | 事务大小，内存占用    |


**Spring配置示例：**

```java
@Configuration
public class DatabaseConfig {
    
    // 场景1：高并发读 - 多级缓存
    @Bean
    public CacheService cacheService() {
        return new CacheService() {
            // 一级缓存：本地Caffeine
            private Cache<String, Object> localCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
            
            // 二级缓存：Redis
            private RedisTemplate<String, Object> redisTemplate;
            
            public Object get(String key) {
                // 1. 查本地缓存
                Object value = localCache.getIfPresent(key);
                if (value != null) return value;
                
                // 2. 查Redis
                value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    localCache.put(key, value);
                    return value;
                }
                
                // 3. 查数据库
                value = loadFromDB(key);
                if (value != null) {
                    redisTemplate.opsForValue().set(key, value, 1, TimeUnit.HOURS);
                    localCache.put(key, value);
                }
                return value;
            }
        };
    }
    
    // 场景2：高并发写 - 队列削峰
    @Bean
    public AsyncWriteService writeService() {
        return new AsyncWriteService() {
            private BlockingQueue<WriteTask> queue = new LinkedBlockingQueue<>(10000);
            private ExecutorService executor = Executors.newFixedThreadPool(4);
            
            @PostConstruct
            public void init() {
                for (int i = 0; i < 4; i++) {
                    executor.submit(this::processQueue);
                }
            }
            
            public void asyncWrite(WriteTask task) {
                if (!queue.offer(task)) {
                    // 队列满，降级为同步写或拒绝
                    handleWriteFull(task);
                }
            }
        };
    }
}
```

## 九、分布式场景


| 场景特征       | 推荐方案                      | 框架/组件                | 关键考虑      |
| ---------- | ------------------------- | -------------------- | --------- |
| **分布式锁**   | Redis RedLock 或 ZooKeeper | Redisson, Curator    | 死锁，脑裂，性能  |
| **分布式ID**  | Snowflake/TinyID          | 雪花算法，美团Leaf          | 趋势递增，时钟回拨 |
| **配置中心**   | Apollo/Nacos              | 携程Apollo，阿里Nacos     | 动态刷新，灰度发布 |
| **服务注册发现** | Nacos/Eureka/Consul       | Spring Cloud Alibaba | 健康检查，负载均衡 |
| **分布式缓存**  | Redis Cluster/Codis       | Redis集群方案            | 数据分片，扩容   |
| **分布式事务**  | Seata/TCC-Transaction     | 阿里Seata              | 事务模式，性能   |
| **限流降级**   | Sentinel/Hystrix          | 阿里Sentinel           | 熔断策略，流量控制 |
| **分布式调度**  | XXL-Job/Elastic-Job       | 分布式任务调度              | 分片广播，故障转移 |


## 十、特定业务场景

### 1. 秒杀系统

```java
@Component
public class SeckillService {
    // 方案1：Redis + Lua 原子操作
    public boolean seckillWithLua(String productId, String userId) {
        String script = 
            "if redis.call('get', KEYS[1]) >= ARGV[1] then " +
            "    redis.call('decr', KEYS[1]) " +
            "    redis.call('sadd', KEYS[2], ARGV[2]) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";
        
        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        return redisTemplate.execute(redisScript, 
            Arrays.asList("stock:" + productId, "users:" + productId), 
            "1", userId) == 1L;
    }
    
    // 方案2：令牌桶限流
    public boolean tryAcquire(String key, int capacity, int tokensPerSecond) {
        RateLimiter limiter = RateLimiter.create(tokensPerSecond);
        return limiter.tryAcquire();
    }
    
    // 方案3：队列削峰
    public void seckillWithQueue(String productId, String userId) {
        // 请求放入队列
        seckillQueue.offer(new SeckillRequest(productId, userId));
        // 异步处理
        processQueueAsync();
    }
}
```

### 2. 实时排行榜

```java
@Component
public class RankService {
    // 方案1：Redis ZSet
    public void addScore(String userId, double score) {
        redisTemplate.opsForZSet().add("rank:game1", userId, score);
    }
    
    public Long getRank(String userId) {
        return redisTemplate.opsForZSet().reverseRank("rank:game1", userId);
    }
    
    // 方案2：ConcurrentSkipListSet（单机）
    private ConcurrentSkipListSet<Player> rankSet = 
        new ConcurrentSkipListSet<>(Comparator
            .comparingDouble(Player::getScore)
            .reversed()
            .thenComparing(Player::getUserId));
    
    // 方案3：数据库 + 定时更新
    @Scheduled(fixedRate = 10000)  // 每10秒更新
    public void updateRank() {
        // 批量更新排名
    }
}
```

### 3. 分布式Session

```java
@Configuration
public class SessionConfig {
    // 方案1：Spring Session + Redis
    @Bean
    public RedisIndexedSessionRepository sessionRepository() {
        return new RedisIndexedSessionRepository(redisTemplate);
    }
    
    // 方案2：JWT Token
    public String createToken(User user) {
        return Jwts.builder()
            .setSubject(user.getId())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(SignatureAlgorithm.HS512, "secret")
            .compact();
    }
    
    // 方案3：Session Stick（会话保持）
    // 通过Nginx ip_hash或cookie保持
}
```

## 选型决策矩阵


| 评估维度      | 权重   | ConcurrentHashMap | Collections.synchronizedMap | ConcurrentSkipListMap | CopyOnWriteArrayList |
| --------- | ---- | ----------------- | --------------------------- | --------------------- | -------------------- |
| **并发性能**  | 30%  | 9/10              | 5/10                        | 8/10                  | 读:10/10, 写:3/10      |
| **内存占用**  | 15%  | 7/10              | 8/10                        | 5/10                  | 3/10                 |
| **功能完整性** | 20%  | 9/10              | 6/10                        | 8/10                  | 5/10                 |
| **易用性**   | 10%  | 8/10              | 9/10                        | 7/10                  | 8/10                 |
| **扩展性**   | 15%  | 8/10              | 5/10                        | 7/10                  | 4/10                 |
| **监控支持**  | 10%  | 7/10              | 8/10                        | 6/10                  | 6/10                 |
| **综合得分**  | 100% | 8.2               | 6.4                         | 7.1                   | 5.7                  |


## 通用选型建议

### 1. 先确定核心需求

```
优先级排序：
1. 一致性要求：强一致性 vs 最终一致性 vs 弱一致性
2. 性能要求：吞吐量 vs 延迟 vs 可用性
3. 数据规模：数据量大小，是否可单机容纳
4. 读写比例：读多写少 vs 写多读少 vs 读写均衡
5. 访问模式：随机访问 vs 顺序访问 vs 范围查询
```

### 2. 分阶段演进

```java
// 阶段1：单机简单应用
Map<String, Object> cache = new ConcurrentHashMap<>();

// 阶段2：单机高性能
Cache<String, Object> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build();

// 阶段3：分布式缓存
@Bean
public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager();
    cacheManager.setCaffeine(Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES));
    
    // 多级缓存
    return new RedisCaffeineCacheManager(redisTemplate, cacheManager);
}
```

### 3. 监控与调优

```java
// 监控指标
@Component
public class ConcurrencyMonitor {
    // 1. 并发数据结构监控
    private final Map<String, AtomicLong> operationCounters = 
        new ConcurrentHashMap<>();
    
    // 2. 线程池监控
    public void monitorThreadPool(ThreadPoolExecutor executor) {
        System.out.println("Active threads: " + executor.getActiveCount());
        System.out.println("Queue size: " + executor.getQueue().size());
        System.out.println("Completed tasks: " + executor.getCompletedTaskCount());
    }
    
    // 3. 锁竞争监控
    public void monitorLockContention() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] threadIds = threadMXBean.getAllThreadIds();
        for (long threadId : threadIds) {
            ThreadInfo info = threadMXBean.getThreadInfo(threadId);
            if (info.getLockOwnerId() != -1) {
                System.out.println("Thread " + info.getThreadName() + 
                    " is waiting for lock held by thread " + info.getLockOwnerName());
            }
        }
    }
}
```

记住：**没有最好的技术，只有最适合的技术**。选型时要综合考虑性能、复杂度、团队熟悉度、维护成本等多方面因素，并在不同阶段进行合理的演进。