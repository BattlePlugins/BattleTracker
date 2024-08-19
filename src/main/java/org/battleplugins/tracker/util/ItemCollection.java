package org.battleplugins.tracker.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ItemCollection {
    private final Set<Material> materials = new HashSet<>();

    ItemCollection(Collection<Material> materials) {
        this.materials.addAll(materials);
    }

    public Set<Material> getMaterials() {
        return Set.copyOf(this.materials);
    }

    public boolean contains(Material material) {
        return this.materials.contains(material);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ItemCollection that = (ItemCollection) object;
        return Objects.equals(this.materials, that.materials);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.materials);
    }

    public static ItemCollection of(Collection<Material> materials) {
        return new ItemCollection(materials);
    }

    public static ItemCollection fromString(String string) {
        if (string.startsWith("#")) {
            return fromTag(string);
        } else {
            return fromMaterial(string);
        }
    }

    private static ItemCollection fromTag(String tag) {
        if (tag.startsWith("#")) {
            tag = tag.substring(1);
        }

        Tag<Material> itemTags = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.fromString(tag), Material.class);
        if (itemTags == null) {
            throw new IllegalArgumentException("Invalid tag: " + tag);
        }

        return ItemCollection.of(itemTags.getValues());
    }

    private static ItemCollection fromMaterial(String material) {
        Material mat = Material.matchMaterial(material);
        if (mat == null) {
            throw new IllegalArgumentException("Invalid material: " + material);
        }

        return ItemCollection.of(Set.of(mat));
    }
}
