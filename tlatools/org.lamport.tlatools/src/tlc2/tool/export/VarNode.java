package tlc2.tool.export;

import java.util.*;

public class VarNode<K extends Comparable<Object>, V> {
    public final K key;
    public final V payload;
    public final List<VarNode<K, V>> children;

    public VarNode(K key, V payload) {
        this.key = key;
        this.payload = payload;
        this.children = new ArrayList<>(5);
    }

    public VarNode<K, V> addChild(K toKey, V payload) {
        VarNode<K, V> node = new VarNode<>(toKey, payload);
        this.children.add(node);
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
}
