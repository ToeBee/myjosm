// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.imagery;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.jcs.access.behavior.ICacheAccess;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.interfaces.CachedTileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileJob;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.josm.data.cache.BufferedImageCacheEntry;
import org.openstreetmap.josm.data.cache.HostLimitQueue;
import org.openstreetmap.josm.data.cache.JCSCacheManager;
import org.openstreetmap.josm.data.cache.JCSCachedTileLoaderJob;
import org.openstreetmap.josm.data.preferences.IntegerProperty;

/**
 * @author Wiktor Niesiobędzki
 *
 * Wrapper class that bridges between JCS cache and Tile Loaders
 *
 */
public class TMSCachedTileLoader implements TileLoader, CachedTileLoader, TileCache {

    private final ICacheAccess<String, BufferedImageCacheEntry> cache;
    private final int connectTimeout;
    private final int readTimeout;
    private final Map<String, String> headers;
    private final TileLoaderListener listener;
    private static final String PREFERENCE_PREFIX   = "imagery.tms.cache.";

    /**
     * how many object on disk should be stored for TMS region. Average tile size is about 20kb. 25000 is around 500MB under this assumption
     */
    public static final IntegerProperty MAX_OBJECTS_ON_DISK = new IntegerProperty(PREFERENCE_PREFIX + "max_objects_disk", 25000);

    /**
     * overrides the THREAD_LIMIT in superclass, as we want to have separate limit and pool for TMS
     */
    public static final IntegerProperty THREAD_LIMIT = new IntegerProperty("imagery.tms.tmsloader.maxjobs", 25);

    /**
     * Limit definition for per host concurrent connections
     */
    public static final IntegerProperty HOST_LIMIT = new IntegerProperty("imagery.tms.tmsloader.maxjobsperhost", 6);

    /**
     * separate from JCS thread pool for TMS loader, so we can have different thread pools for default JCS
     * and for TMS imagery
     */
    private static ThreadPoolExecutor DEFAULT_DOWNLOAD_JOB_DISPATCHER = getThreadPoolExecutor();

    private final ThreadPoolExecutor downloadExecutor = DEFAULT_DOWNLOAD_JOB_DISPATCHER;

    private static ThreadPoolExecutor getThreadPoolExecutor() {
        return new ThreadPoolExecutor(
                THREAD_LIMIT.get().intValue(), // keep the thread number constant
                THREAD_LIMIT.get().intValue(), // do not this number of threads
                30, // keepalive for thread
                TimeUnit.SECONDS,
                new HostLimitQueue(HOST_LIMIT.get().intValue()),
                JCSCachedTileLoaderJob.getNamedThreadFactory("TMS downloader")
                );
    }

    /**
     * Constructor
     * @param listener          called when tile loading has finished
     * @param name              of the cache
     * @param connectTimeout    to remote resource
     * @param readTimeout       to remote resource
     * @param headers           HTTP headers to be sent along with request
     * @param cacheDir          where cache file shall reside
     * @throws IOException      when cache initialization fails
     */
    public TMSCachedTileLoader(TileLoaderListener listener, String name, int connectTimeout, int readTimeout, Map<String, String> headers, String cacheDir) throws IOException {
        this.cache = JCSCacheManager.getCache(name,
                200, // use fairly small memory cache, as cached objects are quite big, as they contain BufferedImages
                MAX_OBJECTS_ON_DISK.get(),
                cacheDir);
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.headers = headers;
        this.listener = listener;
    }

    @Override
    public TileJob createTileLoaderJob(Tile tile) {
        return new TMSCachedTileLoaderJob(listener, tile, cache,
                connectTimeout, readTimeout, headers, downloadExecutor);
    }

    @Override
    public void clearCache(TileSource source) {
        this.cache.clear();
    }

    @Override
    public Tile getTile(TileSource source, int x, int y, int z) {
        return createTileLoaderJob(new Tile(source,x, y, z)).getTile();
    }

    @Override
    public void addTile(Tile tile) {
        createTileLoaderJob(tile).getTile();
    }

    @Override
    public int getTileCount() {
        return 0;
    }

    @Override
    public void clear() {
        cache.clear();
    }

    /**
     * @return cache statistics as string
     */
    public String getStats() {
        return cache.getStats();
    }

    /**
     * cancels all outstanding tasks in the queue. This rollbacks the state of the tiles in the queue
     * to loading = false / loaded = false
     */
    public void cancelOutstandingTasks() {
        for(Runnable r: downloadExecutor.getQueue()) {
            if (downloadExecutor.remove(r) && r instanceof TMSCachedTileLoaderJob) {
                ((TMSCachedTileLoaderJob)r).handleJobCancellation();
            }
        }
    }
}
