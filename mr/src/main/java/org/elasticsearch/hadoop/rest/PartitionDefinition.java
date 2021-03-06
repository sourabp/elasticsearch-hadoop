package org.elasticsearch.hadoop.rest;

import org.apache.hadoop.io.Writable;
import org.elasticsearch.hadoop.cfg.PropertiesSettings;
import org.elasticsearch.hadoop.cfg.Settings;
import org.elasticsearch.hadoop.serialization.dto.mapping.Field;
import org.elasticsearch.hadoop.util.IOUtils;
import org.elasticsearch.hadoop.util.StringUtils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

/**
 * Represents a logical split of an elasticsearch query.
 */
public class PartitionDefinition implements Serializable, Comparable<PartitionDefinition> {
    private final String index;
    private final int shardId;
    private final Slice slice;
    private final String serializedSettings, serializedMapping;

    public PartitionDefinition(String index, int shardId, Settings settings, Field mapping) {
        this(index, shardId, null, settings, mapping);
    }

    public PartitionDefinition(String index, int shardId, Slice slice,
                               Settings settings, Field mapping) {
        this.index = index;
        this.shardId = shardId;
        if (settings != null) {
            this.serializedSettings = settings.save();
        } else {
            this.serializedSettings = null;
        }
        if (mapping != null) {
            this.serializedMapping = IOUtils.serializeToBase64(mapping);
        } else {
            this.serializedMapping = null;
        }
        this.slice = slice;
    }

    public PartitionDefinition(DataInput in) throws IOException {
        this.index = in.readUTF();
        this.shardId = in.readInt();
        if (in.readBoolean()) {
            this.slice = new Slice(in.readInt(), in.readInt());
        } else {
            this.slice = null;
        }

        if (in.readBoolean()) {
            int length = in.readInt();
            byte[] utf = new byte[length];
            in.readFully(utf);
            this.serializedSettings = StringUtils.asUTFString(utf);
        } else {
            this.serializedSettings = null;
        }
        if (in.readBoolean()) {
            int length = in.readInt();
            byte[] utf = new byte[length];
            in.readFully(utf);
            this.serializedMapping = StringUtils.asUTFString(utf);
        } else {
            this.serializedMapping = null;
        }
    }

    public void write(DataOutput out) throws IOException {
        out.writeUTF(index);
        out.writeInt(shardId);
        out.writeBoolean(slice != null);
        if (slice != null) {
            out.writeInt(slice.id);
            out.writeInt(slice.max);
        }

        out.writeBoolean(serializedSettings != null);
        if (serializedSettings != null) {
            // same goes for settings
            byte[] utf = StringUtils.toUTF(serializedSettings);
            out.writeInt(utf.length);
            out.write(utf);
        }
        out.writeBoolean(serializedMapping != null);
        if (serializedMapping != null) {
            // avoid using writeUTF since the mapping can be longer than 65K
            byte[] utf = StringUtils.toUTF(serializedMapping);
            out.writeInt(utf.length);
            out.write(utf);
        }
    }

    public String getIndex() {
        return index;
    }

    public int getShardId() {
        return shardId;
    }

    public Slice getSlice() {
        return slice;
    }

    public String getSerializedSettings() {
        return serializedSettings;
    }

    public String getSerializedMapping() {
        return serializedMapping;
    }

    public Settings settings() {
        PropertiesSettings settings = new PropertiesSettings();
        return serializedMapping != null ? settings.load(serializedSettings) : settings;
    }

    @Override
    public int compareTo(PartitionDefinition o) {
        int cmp = index.compareTo(o.index);
        if (cmp != 0) {
            return cmp;
        }
        cmp = shardId - o.shardId;
        if (cmp != 0) {
            return cmp;
        }
        if (slice != null) {
            return slice.compareTo(o.slice);
        }
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PartitionDefinition that = (PartitionDefinition) o;

        if (shardId != that.shardId) return false;
        if (!index.equals(that.index)) return false;
        return slice != null ? slice.equals(that.slice) : that.slice == null;

    }

    @Override
    public int hashCode() {
        int result = index.hashCode();
        result = 31 * result + shardId;
        result = 31 * result + (slice != null ? slice.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SlicePartition [index=").append(index)
                .append(",shardId=").append(shardId).append(",id=")
                .append(slice.id).append(",max=").append(slice.max).append("]");
        return builder.toString();
    }

    public static class Slice implements Serializable, Comparable<Slice> {
        public final int id;
        public final int max;

        public Slice(int id, int max) {
            this.id = id;
            this.max = max;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Slice slice = (Slice) o;

            if (id != slice.id) return false;
            return max == slice.max;

        }

        @Override
        public int hashCode() {
            int result = id;
            result = 31 * result + max;
            return result;
        }

        @Override
        public int compareTo(Slice o) {
            int cmp = id - o.id;
            if (cmp != 0) {
                return cmp;
            }
            return max - o.max;
        }
    }
}
