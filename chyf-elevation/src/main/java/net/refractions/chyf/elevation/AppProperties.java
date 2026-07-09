/*
 * Copyright 2026 Canadian Wildlife Federation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.refractions.chyf.elevation;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Represents the configuration properties used for updating flowpath
 * geometries (raw elevation computation and smoothing).
 * <p>
 * Load an instance from a .properties file using {@link #loadFromFile(String)}.
 */
public class AppProperties {

    // the aoi table that is both the source and target of the geometries to update
    private String aoiTable;
    
    // the flowpath table that is both the source and target of the geometries to update
    private String eflowpathTable;

    // the properties table which has a graph_id associated with each flowpath
    // edge; used for calculating the smoothed elevation; not required for
    // computing raw elevation
    private String eflowpathPropertiesTable;

    // the geometry column to read and write from; should have no srid or
    // geometry type associated with it
    private String geometryColumn;

    // the srid of the geometry column
    private int srid;

    // the cloud optimized geoservice for the DEM
    private String cogPath;

    // the number of threads to use in processing
    private int numThreads;

    // the size of blocks (in degrees) for computing raw elevation
    private double blockSize;

    // the target group size for smoothing processing; if a connected group is
    // larger than this the entire connected group will be computed together
    private int smoothingTargetGroupSize;

    // the batch size for processing edges when smoothing (internal vertices,
    // not the graph)
    private int smoothingFlowpathBatchSize;

    // comma delimited list of aoi short_names to process; empty means
    // process everything
    private List<String> aoi;

    // property key constants
    public static final String KEY_AOI_TABLE = "AOI_TABLE";
    public static final String KEY_EFLOWPATH_TABLE = "EFLOWPATH_TABLE";
    public static final String KEY_EFLOWPATH_PROPERTIES_TABLE = "EFLOWPATH_PROPERTIES_TABLE";
    public static final String KEY_GEOMETRY_COLUMN = "GEOMETRY_COLUMN";
    public static final String KEY_SRID = "SRID";
    public static final String KEY_COG_PATH = "COG_PATH";
    public static final String KEY_NUM_THREADS = "NUM_THREADS";
    public static final String KEY_BLOCK_SIZE = "BLOCK_SIZE";
    public static final String KEY_SMOOTHING_TARGET_GROUP_SIZE = "SMOOTHING_TARGET_GROUP_SIZE";
    public static final String KEY_SMOOTHING_FLOWPATH_BATCH_SIZE = "SMOOTHING_FLOWPATH_BATCH_SIZE";
    public static final String KEY_AOI_FILTER = "AOI_FILTER";

    /**
     * Reads a .properties file from the given path and returns a populated
     * FlowpathConfig instance.
     *
     * @param filePath path to the properties file
     * @return a populated FlowpathConfig
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if a required property is missing or
     *                                   a numeric property cannot be parsed
     */
    public static AppProperties loadFromFile(Path filePath) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(filePath)) {
            props.load(in);
        }

        AppProperties config = new AppProperties();

        config.eflowpathTable = requireString(props, KEY_EFLOWPATH_TABLE);
        //not required
        config.aoiTable = props.getProperty(KEY_AOI_TABLE, "").trim();
        // not required
        config.eflowpathPropertiesTable = props.getProperty(KEY_EFLOWPATH_PROPERTIES_TABLE, "").trim();
        config.geometryColumn = requireString(props, KEY_GEOMETRY_COLUMN);
        config.srid = requireInt(props, KEY_SRID);
        config.cogPath = requireString(props, KEY_COG_PATH);
        config.numThreads = requireInt(props, KEY_NUM_THREADS);
        config.blockSize = requireDouble(props, KEY_BLOCK_SIZE);
        config.smoothingTargetGroupSize = requireInt(props, KEY_SMOOTHING_TARGET_GROUP_SIZE);
        config.smoothingFlowpathBatchSize = requireInt(props, KEY_SMOOTHING_FLOWPATH_BATCH_SIZE);

        String aoiRaw = props.getProperty(KEY_AOI_FILTER, "").trim();
        config.aoi = parseAoi(aoiRaw);

        return config;
    }


    private static List<String> parseAoi(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String requireString(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value.trim();
    }

    private static int requireInt(Properties props, String key) {
        String value = requireString(props, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Property " + key + " must be an integer, got: " + value, e);
        }
    }

    private static double requireDouble(Properties props, String key) {
        String value = requireString(props, key);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Property " + key + " must be a number, got: " + value, e);
        }
    }

    // ---- getters / setters ----

    public String getAoiTable() {
        return aoiTable;
    }
    
    public String getEflowpathTable() {
        return eflowpathTable;
    }

    public void setEflowpathTable(String eflowpathTable) {
        this.eflowpathTable = eflowpathTable;
    }

    public String getEflowpathPropertiesTable() {
        return eflowpathPropertiesTable;
    }

    public void setEflowpathPropertiesTable(String eflowpathPropertiesTable) {
        this.eflowpathPropertiesTable = eflowpathPropertiesTable;
    }

    public String getGeometryColumn() {
        return geometryColumn;
    }

    public void setGeometryColumn(String geometryColumn) {
        this.geometryColumn = geometryColumn;
    }

    public int getSrid() {
        return srid;
    }

    public void setSrid(int srid) {
        this.srid = srid;
    }

    public String getCogPath() {
        return cogPath;
    }

    public void setCogPath(String cogPath) {
        this.cogPath = cogPath;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    public double getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(double blockSize) {
        this.blockSize = blockSize;
    }

    public int getSmoothingTargetGroupSize() {
        return smoothingTargetGroupSize;
    }

    public void setSmoothingTargetGroupSize(int smoothingTargetGroupSize) {
        this.smoothingTargetGroupSize = smoothingTargetGroupSize;
    }

    public int getSmoothingFlowpathBatchSize() {
        return smoothingFlowpathBatchSize;
    }

    public void setSmoothingFlowpathBatchSize(int smoothingFlowpathBatchSize) {
        this.smoothingFlowpathBatchSize = smoothingFlowpathBatchSize;
    }

    /**
     * @return the list of AOI short_names to process; empty list means
     *         process everything
     */
    public List<String> getAoi() {
        return aoi;
    }

    public void setAoi(List<String> aoi) {
        this.aoi = aoi;
    }

    public boolean hasAoiFilter() {
        return aoi != null && !aoi.isEmpty();
    }

    @Override
    public String toString() {
        return "FlowpathConfig{" +
                "eflowpathTable='" + eflowpathTable + '\'' +
                ", eflowpathPropertiesTable='" + eflowpathPropertiesTable + '\'' +
                ", geometryColumn='" + geometryColumn + '\'' +
                ", srid=" + srid +
                ", cogPath='" + cogPath + '\'' +
                ", numThreads=" + numThreads +
                ", blockSize=" + blockSize +
                ", smoothingTargetGroupSize=" + smoothingTargetGroupSize +
                ", smoothingFlowpathBatchSize=" + smoothingFlowpathBatchSize +
                ", aoi=" + aoi +
                '}';
    }
}