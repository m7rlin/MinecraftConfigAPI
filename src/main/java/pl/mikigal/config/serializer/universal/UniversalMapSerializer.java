package pl.mikigal.config.serializer.universal;

import org.bukkit.configuration.ConfigurationSection;
import pl.mikigal.config.BukkitConfiguration;
import pl.mikigal.config.exception.InvalidConfigFileException;
import pl.mikigal.config.exception.MissingSerializerException;
import pl.mikigal.config.serializer.Serializer;
import pl.mikigal.config.serializer.Serializers;
import pl.mikigal.config.util.TypeUtils;

import java.util.*;

/**
 * Helper built-in serializer for processing Map
 * @see Map
 * @see Serializer
 * @since 1.0
 * @author Mikołaj Gałązka
 */
public class UniversalMapSerializer extends Serializer<Map> {

	@Override
	protected void saveObject(String path, Map object, BukkitConfiguration configuration) {
		if (object.size() == 0) {
			// Java's generics suck so I can't check generic type of empty Map
			throw new IllegalStateException("Can't set empty Map to config");
		}

		Class<?> generic = TypeUtils.getMapGeneric(object)[1];
		Serializer<?> serializer = Serializers.of(generic);
		if (serializer == null && !TypeUtils.isSimpleType(generic)) {
			throw new MissingSerializerException(generic);
		}

		configuration.set(path + ".structure", object.getClass().getName());
		configuration.set(path + ".type", generic.getName());

		for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
			if (serializer == null) {
				configuration.set(path + "." + entry.getKey(), entry.getValue());
				continue;
			}

			serializer.serialize(path + "." + entry.getKey(), entry.getValue(), configuration);
		}
	}

	@Override
	public Map<?, ?> deserialize(String path, BukkitConfiguration configuration) {
		ConfigurationSection section = configuration.getConfigurationSection(path);

		String mapRaw = section.getString("structure");
		String serializerRaw = section.getString("type");

		Objects.requireNonNull(mapRaw, "Collection type is not defined for " + path);
		Objects.requireNonNull(serializerRaw, "Serializer type is not defined for " + path);

		try {
			Serializer<?> serializer = Serializers.of(serializerRaw);
			Class<?> mapClass = Class.forName(mapRaw);
			Class<?> serializerClass = Class.forName(serializerRaw);

			if (serializer == null && !TypeUtils.isSimpleType(serializerClass)) {
				throw new MissingSerializerException(serializerClass);
			}

			Map map = (Map) mapClass.newInstance();
			for (String key : section.getKeys(false)) {
				if (key.equals("type") || key.equals("structure")) {
					continue;
				}

				if (serializer == null) {
					map.put(key, configuration.get(path + "." + key));
					continue;
				}

				map.put(key, serializer.deserialize(path + "." + key, configuration));
			}

			return map;
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
