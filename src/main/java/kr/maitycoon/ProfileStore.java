package kr.maitycoon;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Main-thread only. Never silently overwrite unreadable player data. */
public final class ProfileStore {
    private final Path directory;
    public ProfileStore(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
    }
    public Profile load(UUID id) throws IOException {
        Path path = directory.resolve(id + ".yml");
        if (!Files.exists(path)) return Profile.empty();
        var yaml = new YamlConfiguration();
        try {
            yaml.load(path.toFile());
            if (!yaml.isLong("coins") && !yaml.isInt("coins")) throw new IOException("Invalid coins");
            if (!yaml.isLong("experience") && !yaml.isInt("experience")) throw new IOException("Invalid experience");
            if (!yaml.isBoolean("starter-claimed")) throw new IOException("Invalid starter flag");
            return new Profile(yaml.getLong("coins"), yaml.getLong("zcoin"), yaml.getLong("experience"), yaml.getBoolean("starter-claimed"),
                new HashSet<>(yaml.getStringList("dex")), new HashSet<>(yaml.getIntegerList("dex-rewards")), yaml.getBoolean("land-permit"), yaml.getLong("lamps"));
        } catch (InvalidConfigurationException | IllegalArgumentException e) {
            throw new IOException("Invalid profile: " + id, e);
        }
    }
    public void save(UUID id, Profile profile) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("schema", 2);
        yaml.set("coins", profile.coins());
        yaml.set("zcoin", profile.zcoin());
        yaml.set("experience", profile.experience());
        yaml.set("starter-claimed", profile.starterClaimed());
        yaml.set("dex", new TreeSet<>(profile.dex()).stream().toList());
        yaml.set("dex-rewards", new TreeSet<>(profile.dexRewards()).stream().toList());
        yaml.set("land-permit", profile.landPermit());
        yaml.set("lamps", profile.lamps());
        Path temp = Files.createTempFile(directory, id.toString(), ".tmp");
        try {
            Files.writeString(temp, yaml.saveToString());
            Files.move(temp, directory.resolve(id + ".yml"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
