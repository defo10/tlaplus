package tlc2.tool.export;

import java.util.*;

public class VarNode<K extends Comparable<?>, V> {
    public final K key;
    public final V payload;
    public final Map<K, VarNode<K, V>> children;

    public VarNode(K key, V payload) {
        this.key = key;
        this.payload = payload;
        this.children = new HashMap<>(10);
    }

    /** adds a child to this node. If key already exists, it returns the old one.
     * @param key
     * @param payload
     * @return
     */
    public VarNode<K, V> addChildIfAbsent(K key, V payload) {
        if (this.children.containsKey(key)) {
            return this.children.get(key);
        }
        VarNode<K, V> node = new VarNode<>(key, payload);
        this.children.put(key, node);
        return node;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VarNode)) {
            return false;
        }
        VarNode<?, ?> n = (VarNode<?, ?>) obj;
        return this.key.equals(n.key);
    }

    @Override
    public String toString() {
        StringBuilder keyValueBuilder = new StringBuilder();

        if (this.key != null) keyValueBuilder.append("\"").append(this.key).append("\":");

        if (this.children.isEmpty()) {
            keyValueBuilder.append("true");
        } else {
            keyValueBuilder.append("{");
            for (VarNode<K, V> entry : this.children.values()) {
                keyValueBuilder.append(entry.toString()).append(",");
            }
            // delete last "," to be json conforming
            keyValueBuilder.deleteCharAt(keyValueBuilder.length() - 1);
            keyValueBuilder.append("}");
        }

        return keyValueBuilder.toString();
    }
}
