package tlc2.tool.export;

import tlc2.module.Json;
import tlc2.value.IValue;

import java.io.IOException;
import java.util.*;

public class VarNode<K extends Comparable<?>, V extends IValue> {
    public final K key;
    public final V payload;
    public final Object children;

    public final boolean isChildSet;
    public VarNode(K key, V payload, boolean isChildSet) {
        this.key = key;
        this.payload = payload;
        this.isChildSet = isChildSet;
        if (isChildSet) {
            this.children = new HashSet<V>(7);
        } else {
            this.children = new HashMap<K, VarNode<K, V>>(7);
        }
    }

    public VarNode(K key, V payload) {
        this(key, payload, false);
    }

    /** adds a child to this node. If key already exists, it returns the old one.
     * @param key
     * @param payload
     * @return
     */
    public VarNode<K, V> addChildIfAbsent(K key, V payload) {
        return addChildIfAbsent(key, payload, false);
    }

    /**
     * adds a child to this node. If key already exists, it returns the old one.
     *
     * @param key
     * @param payload
     * @param isChildSet
     * @return
     */
    public VarNode<K, V> addChildIfAbsent(K key, V payload, boolean isChildSet) {
        Map<K, VarNode<K, V>> children = this.getChildrenMap();
        if (children.containsKey(key)) {
            return children.get(key);
        }
        VarNode<K, V> node = new VarNode<>(key, payload, isChildSet);
        children.put(key, node);
        return node;
    }

    public void addChild(V payload) {
        Set<V> children = this.getChildrenSet();
        children.add(payload);
    }

    /** check isChildSet before calling this method! */
    public Map<K, VarNode<K, V>> getChildrenMap() {
        if (isChildSet) {
            throw new RuntimeException("VarNode has a Set as child!");
        }
        return (Map<K, VarNode<K, V>>) this.children;
    }

    /** check isChildSet before calling this method! */
    public Set<V> getChildrenSet() {
        if (!isChildSet) {
            throw new RuntimeException("VarNode has a Map as child!");
        }
        return (Set<V>) this.children;
    }

    public VarNode<K, V> findNodeWithIdenticalPayload(V payload) {
        if (this.payload == payload) {
            return this;
        }

        if (this.isChildSet) {
            Set<V> childrenSet = this.getChildrenSet();
            for (V childPayload : childrenSet) {
                // there is a problem in the semantics here:
                // for map like structures, each key corresponds to a varnode but if its a set node
                // the payload we are looking for might be one of the elements of the set.
                // in this case, we return the closest var node (this), but this might be confusing
                if (childPayload == payload) {
                    return this;
                }
            }
        } else {
            Map<K, VarNode<K, V>> children = this.getChildrenMap();

            for (VarNode<K, V> c : children.values()) {
                VarNode<K, V> match = c.findNodeWithIdenticalPayload(payload);
                if (match != null) {
                    return match;
                }
            }
        }

        return null;
    }

    public VarNode<K, V> deepCopy() {
        VarNode<K, V> copy = new VarNode<>(key, payload, this.isChildSet);

        if (isChildSet) {
            Set<V> children = this.getChildrenSet();
            for (V c : children) {
                copy.getChildrenSet().add(c);
            }
        } else {
            Map<K, VarNode<K, V>> children = this.getChildrenMap();
            for (K childKey : children.keySet()) {
                VarNode<K, V> copiedChild = children.get(childKey).deepCopy();
                copy.getChildrenMap().put(childKey, copiedChild);
            }
        }

        return copy;
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
        if (isChildSet) {
            return toStringSet();
        } else {
            return toStringMap();
        }
    }

    private String toStringSet() {
        StringBuilder keyValueBuilder = new StringBuilder();

        if (this.key != null) keyValueBuilder.append("\"").append(this.key).append("\":");

        Set<V> children = this.getChildrenSet();

        if (children.isEmpty()) {
            keyValueBuilder.append("[]");
        } else {
            keyValueBuilder.append("[");

            for (IValue vals : children) {
                try {
                    keyValueBuilder.append(Json.toJson(vals)).append(",");
                } catch (IOException e) {
                    System.out.println("Failed to parse vals as json:" + vals);
                }
            }
            // delete last "," to be json conforming
            keyValueBuilder.deleteCharAt(keyValueBuilder.length() - 1);
            keyValueBuilder.append("]");
        }

        return keyValueBuilder.toString();
    }

    private String toStringMap() {
        StringBuilder keyValueBuilder = new StringBuilder();

        if (this.key != null) keyValueBuilder.append("\"").append(this.key).append("\":");

        Map<K, VarNode<K, V>> children = this.getChildrenMap();

        if (children.isEmpty()) {
            keyValueBuilder.append("true");
        } else {
            keyValueBuilder.append("{");
            for (VarNode<K, V> entry : children.values()) {
                keyValueBuilder.append(entry.toString()).append(",");
            }
            // delete last "," to be json conforming
            keyValueBuilder.deleteCharAt(keyValueBuilder.length() - 1);
            keyValueBuilder.append("}");
        }

        return keyValueBuilder.toString();
    }
}
