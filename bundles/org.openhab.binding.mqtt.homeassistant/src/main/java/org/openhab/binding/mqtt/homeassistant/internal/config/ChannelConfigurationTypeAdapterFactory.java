/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mqtt.homeassistant.internal.config;

import java.io.IOException;
import java.lang.reflect.Field;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.homeassistant.internal.MappingJsonReader;
import org.openhab.binding.mqtt.homeassistant.internal.config.dto.AbstractChannelConfiguration;
import org.openhab.binding.mqtt.homeassistant.internal.config.dto.Device;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * This a Gson type adapter factory.
 *
 * <p>
 * It will create a type adapter for every class derived from {@link
 * AbstractChannelConfiguration} and ensures,
 * that abbreviated names are replaces with their long versions during the read.
 *
 * <p>
 * In elements, whose name end in'_topic' '~' replacement is performed.
 *
 * <p>
 * The adapters also handle {@link Device}
 *
 * @author Jochen Klein - Initial contribution
 */
@NonNullByDefault
public class ChannelConfigurationTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    @Nullable
    public <T> TypeAdapter<T> create(@Nullable Gson gson, @Nullable TypeToken<T> type) {
        if (gson == null || type == null) {
            return null;
        }
        if (AbstractChannelConfiguration.class.isAssignableFrom(type.getRawType())) {
            return createHAConfig(gson, type);
        }
        if (Device.class.isAssignableFrom(type.getRawType())) {
            return createHADevice(gson, type);
        }
        return null;
    }

    /**
     * Handle {@link
     * AbstractChannelConfiguration}
     *
     * @param gson parser
     * @param type type
     * @return adapter
     */
    private <T> TypeAdapter<T> createHAConfig(Gson gson, TypeToken<T> type) {
        /* The delegate is the 'default' adapter */
        final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        return new TypeAdapter<T>() {
            @Override
            public @Nullable T read(JsonReader in) throws IOException {
                /* read the object using the default adapter, but translate the names in the reader */
                T result = delegate.read(MappingJsonReader.getConfigMapper(in));
                /* do the '~' expansion afterwards */
                expandTidleInTopics(AbstractChannelConfiguration.class.cast(result));
                return result;
            }

            @Override
            public void write(JsonWriter out, @Nullable T value) throws IOException {
                delegate.write(out, value);
            }
        };
    }

    private <T> TypeAdapter<T> createHADevice(Gson gson, TypeToken<T> type) {
        /* The delegate is the 'default' adapter */
        final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        return new TypeAdapter<T>() {
            @Override
            public @Nullable T read(JsonReader in) throws IOException {
                /* read the object using the default adapter, but translate the names in the reader */
                T result = delegate.read(MappingJsonReader.getDeviceMapper(in));
                return result;
            }

            @Override
            public void write(JsonWriter out, @Nullable T value) throws IOException {
                delegate.write(out, value);
            }
        };
    }

    private void expandTidleInTopics(AbstractChannelConfiguration config) {
        Class<?> type = config.getClass();

        String tilde = config.getTilde();

        while (type != Object.class) {
            Field[] fields = type.getDeclaredFields();

            for (Field field : fields) {
                if (String.class.isAssignableFrom(field.getType()) && field.getName().endsWith("_topic")) {
                    field.setAccessible(true);

                    try {
                        final String oldValue = (String) field.get(config);

                        String newValue = oldValue;
                        if (oldValue != null && !oldValue.isBlank()) {
                            if (oldValue.charAt(0) == '~') {
                                newValue = tilde + oldValue.substring(1);
                            } else if (oldValue.charAt(oldValue.length() - 1) == '~') {
                                newValue = oldValue.substring(0, oldValue.length() - 1) + tilde;
                            }
                        }

                        field.set(config, newValue);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            type = type.getSuperclass();
        }
    }
}
