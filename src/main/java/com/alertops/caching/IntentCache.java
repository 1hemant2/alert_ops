
package com.alertops.caching;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IntentCache {

    private static final String CACHE = "intent";

    // create / overwrite intent
    @CachePut(value = CACHE, key = "#intentId")
    public Intent put(UUID intentId, Intent intent) {
        return intent;
    }

    // read intent (NO default creation)
    @Cacheable(value = CACHE, key = "#intentId")
    public Intent get(UUID intentId) {
        return null; // returned only on caching miss
    }

    // consume intent (burn after reading)
    @CacheEvict(value = CACHE, key = "#intentId")
    public void delete(UUID intentId) {}
}

