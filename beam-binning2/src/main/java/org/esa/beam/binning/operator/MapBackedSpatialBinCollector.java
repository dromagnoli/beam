package org.esa.beam.binning.operator;

import org.esa.beam.binning.BinningContext;
import org.esa.beam.binning.SpatialBin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An implementation of {@link SpatialBinCollector} which simply stores the consumed {@link SpatialBin spatial bins} in a map.
 * This means that all data is kept in memory. There are other implementations which consume less memory.
 *
 * @see GeneralSpatialBinCollector
 * @see FileBackedSpatialBinCollector
 */
class MapBackedSpatialBinCollector implements SpatialBinCollector {

    // Note, we use a sorted map in order to sort entries on-the-fly
    private final SortedMap<Long, List<SpatialBin>> spatialBinMap = new TreeMap<Long, List<SpatialBin>>();

    private AtomicBoolean consumingCompleted;

    MapBackedSpatialBinCollector() {
        consumingCompleted = new AtomicBoolean(false);
    }

    @Override
    public SpatialBinCollection getSpatialBinCollection() {
        return new SortedMapWrappingSpatialBinCollection(spatialBinMap);
    }

    @Override
    public void consumeSpatialBins(BinningContext binningContext, List<SpatialBin> spatialBins) {
        if (consumingCompleted.get()) {
            throw new IllegalStateException("Consuming of bins has already been completed.");
        }
        for (SpatialBin spatialBin : spatialBins) {
            final long spatialBinIndex = spatialBin.getIndex();
            synchronized (spatialBinMap) {
                List<SpatialBin> spatialBinList = spatialBinMap.get(spatialBinIndex);
                if (spatialBinList == null) {
                    spatialBinList = new ArrayList<SpatialBin>();
                    spatialBinMap.put(spatialBinIndex, spatialBinList);
                }
                spatialBinList.add(spatialBin);
            }
        }
    }

    @Override
    public void consumingCompleted() {
        consumingCompleted.set(true);
    }

    private class SortedMapWrappingSpatialBinCollection implements SpatialBinCollection {

        private SortedMap<Long, List<SpatialBin>> map;

        private SortedMapWrappingSpatialBinCollection(SortedMap<Long, List<SpatialBin>> spatialBinMap) {
            this.map = spatialBinMap;
        }

        @Override
        public Iterable<List<SpatialBin>> getCollectedBins() {
            return new Iterable<List<SpatialBin>>() {
                @Override
                public Iterator<List<SpatialBin>> iterator() {
                    return map.values().iterator();
                }
            };
        }

        @Override
        public long size() {
            return map.size();
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void clear() {
            map.clear();
        }
    }
}