package com.distributed.cache.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheStore
 */
class CacheStoreTest {
    
    private CacheStore cache;
    
    @BeforeEach
    void setUp() {
        cache = new CacheStore(100);
    }
    
    @Test
    void testPutAndGet() {
        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
    }
    
    @Test
    void testGetNonExistent() {
        assertNull(cache.get("nonexistent"));
    }
    
    @Test
    void testDelete() {
        cache.put("key1", "value1");
        cache.delete("key1");
        assertNull(cache.get("key1"));
    }
    
    @Test
    void testSize() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        assertEquals(2, cache.size());
    }
    
    @Test
    void testClear() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.clear();
        assertEquals(0, cache.size());
    }
}
