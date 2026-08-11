package com.novax.leadora.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    /**
     * The serializer every cache stores values with.
     *
     * <p>{@code new GenericJackson2JsonRedisSerializer()} builds its own bare ObjectMapper — it does
     * <b>not</b> reuse the application's — and that mapper has no {@code JavaTimeModule}. Any cached
     * value carrying a {@code LocalDate} therefore failed to serialize at all, which the configured
     * {@link CacheErrorHandler} turned into a warning; the caches on the UC-23 reports cost a Redis
     * round trip per request and never held anything. Exposed as a static factory so
     * {@code ReportResponseCacheRoundTripTest} exercises the same construction rather than a
     * lookalike.
     */
    public static GenericJackson2JsonRedisSerializer valueSerializer() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                // ISO-8601 strings rather than epoch arrays: a cached payload is far easier to read
                // when someone is staring at redis-cli trying to work out why a report looks odd.
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Supplying our own mapper means we also take over the default typing the no-arg
        // constructor would have switched on. Without it nothing writes a @class marker and every
        // cached value comes back a LinkedHashMap, which fails on the cast at the call site.
        //
        // The allow-list is narrower than the permissive validator Spring's default would use:
        // only our own types and the JDK types the DTOs are built from can be reconstructed, so a
        // tampered cache entry cannot name an arbitrary class to instantiate.
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.novax.leadora.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .build();
        mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default configuration: 10 minutes TTL, JSON serialization
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer()));

        // UC-23 reports are recomputed on a short TTL rather than evicted: nothing publishes a
        // domain event a @CacheEvict could hang off, so a stale window is unavoidable and the
        // honest move is to keep it small. Every report cache is listed — one left out silently
        // falls back to the 10-minute default, which is not what a reporting screen wants.
        Map<String, RedisCacheConfiguration> customConfigs = new HashMap<>();
        customConfigs.put("sales-performance-report", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        customConfigs.put("pipeline-progression-report", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        customConfigs.put("quotation-outcome-report", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        // UC-23.6: five round trips and the heaviest report here, but read far less often than the
        // dashboards, so the same 5 minutes rather than anything longer — a stale scorecard is worse
        // than a slow one when somebody is about to have a conversation about it.
        customConfigs.put("rep-scorecard", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        // UC-23.7: an LLM call per miss, against a quota that returns 429 when exhausted. Ten
        // minutes so a manager re-reading the same review, or two managers opening it at once,
        // costs one generation rather than several — and so a double-click cannot burn the quota.
        customConfigs.put("rep-scorecard-ai-review", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        // Shorter still: "overdue" is derived from the clock, so a cached copy of this report ages
        // into being wrong rather than merely stale.
        customConfigs.put("task-performance-report", defaultConfig.entryTtl(Duration.ofMinutes(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(customConfigs)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache GET failed for cache '{}' (key: {}). Falling back to database. Error: {}", 
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis cache PUT failed for cache '{}' (key: {}). Error: {}", 
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache EVICT failed for cache '{}' (key: {}). Error: {}", 
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis cache CLEAR failed for cache '{}'. Error: {}", 
                        cache.getName(), exception.getMessage());
            }
        };
    }
}
