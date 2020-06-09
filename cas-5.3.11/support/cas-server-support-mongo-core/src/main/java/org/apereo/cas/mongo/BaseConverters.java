package org.apereo.cas.mongo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.mongodb.DBObject;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apereo.cas.util.DateTimeUtils;
import org.apereo.services.persondir.IPersonAttributes;
import org.bson.BsonReader;
import org.bson.BsonTimestamp;
import org.bson.BsonWriter;
import org.bson.Transformer;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.slf4j.Logger;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter; 
import org.springframework.data.convert.WritingConverter; 

import java.lang.ref.ReferenceQueue;
import java.security.cert.CertPath;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * Collection of mongo converters that map objects to
 * DB objects. Able to exclude types.
 *
 * @author Misagh Moayyed
 * @since 4.1
 */
@Slf4j
@NoArgsConstructor
public abstract class BaseConverters {

    /**
     * The type Null converter.
     *
     * @param <I> the type parameter
     * @param <O> the type parameter
     */
    public static class NullConverter<I, O> implements Converter<I, O> {

        @Override
        public O convert(final I i) {
            return null;
        }
    }

    /**
     * The type Logger converter.
     */
    public static class LoggerConverter extends NullConverter<Logger, DBObject> {
    }

    /**
     * The type Class converter.
     *
     * @since 4.1
     */
    public static class ClassConverter extends NullConverter<Class, DBObject> {
    }

    /**
     * The type Commons log converter.
     *
     * @since 4.1
     */
    public static class CommonsLogConverter extends NullConverter<Log, DBObject> {
    }

    /**
     * The type Person attributes converter.
     *
     * @since 4.1
     */
    public static class PersonAttributesConverter extends NullConverter<IPersonAttributes, DBObject> {
    }

    /**
     * The type Cache loader converter.
     *
     * @since 4.1
     */
    public static class CacheLoaderConverter extends NullConverter<CacheLoader, DBObject> {
    }

    /**
     * The type Cache converter.
     *
     * @since 4.1
     */
    public static class CacheConverter extends NullConverter<Cache, DBObject> {
    }

    /**
     * The type Caffein cache converter.
     */
    public static class CaffeinCacheConverter extends NullConverter<com.github.benmanes.caffeine.cache.Cache, DBObject> {
    }

    /**
     * The type Caffein cache loader converter.
     */
    public static class CaffeinCacheLoaderConverter extends NullConverter<com.github.benmanes.caffeine.cache.CacheLoader, DBObject> {
    }

    /**
     * The type Runnable converter.
     *
     * @since 4.1
     */
    public static class RunnableConverter extends NullConverter<Runnable, DBObject> {
    }

    /**
     * The type Reference queue converter.
     *
     * @since 4.1
     */
    public static class ReferenceQueueConverter extends NullConverter<ReferenceQueue, DBObject> {
    }

    /**
     * The type Thread local converter.
     *
     * @since 4.1
     */
    public static class ThreadLocalConverter extends NullConverter<ThreadLocal, DBObject> {
    }

    /**
     * The type Cert path converter.
     *
     * @since 4.1
     */
    public static class CertPathConverter extends NullConverter<CertPath, DBObject> {
    }

    /**
     * The type Date to zoned date time converter.
     */
    public static class DateToZonedDateTimeConverter implements Converter<Date, ZonedDateTime> {
        @Override
        public ZonedDateTime convert(final Date source) {
            return source == null ? null : ZonedDateTime.ofInstant(source.toInstant(), ZoneId.systemDefault());
        }
    }

    /**
     * The type String to zoned date time converter.
     */
    @ReadingConverter
    public static class StringToDateConverter implements Converter<String, Date> {
        @Override
        public Date convert(final String source) {
            if (StringUtils.isBlank(source)) {
                return null;
            }
            try {
                final ZonedDateTime zdt = ZonedDateTime.parse(source);
                return DateTimeUtils.dateOf(zdt);
            } catch (final DateTimeParseException e) {
                final LocalDateTime ldt = DateTimeUtils.localDateTimeOf(source);
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            }
        }
    }

    /**
     * The type String to zoned date time converter.
     */
    @ReadingConverter
    public static class StringToZonedDateTimeConverter implements Converter<String, ZonedDateTime> {
        @Override
        public ZonedDateTime convert(final String source) {
            if (StringUtils.isBlank(source)) {
                return null;
            }
            return ZonedDateTime.parse(source);
        }
    }

    /**
     * The type Zoned date time to date converter.
     */
    public static class ZonedDateTimeToDateConverter implements Converter<ZonedDateTime, Date> {
        @Override
        public Date convert(final ZonedDateTime source) {
            return source == null ? null : Date.from(source.toInstant());
        }
    }

    /**
     * The type Zoned date time to string converter.
     */
    @WritingConverter
    public static class ZonedDateTimeToStringConverter implements Converter<ZonedDateTime, String> {
        @Override
        public String convert(final ZonedDateTime source) {
            return source == null ? null : source.toString();
        }
    }

    /**
     * The type Zoned date time transformer.
     */
    public static class ZonedDateTimeTransformer implements Transformer {
        @Override
        public Object transform(final Object o) {
            final ZonedDateTime value = (ZonedDateTime) o;
            return value.toString();
        }
    }

    /**
     * The type Zoned date time codec provider.
     */
    public static class ZonedDateTimeCodecProvider implements CodecProvider {
        @Override
        public <T> Codec<T> get(final Class<T> aClass, final CodecRegistry codecRegistry) {
            if (aClass == ZonedDateTime.class) {
                return (Codec<T>) new ZonedDateTimeCodec();
            }
            return null;
        }

        private static class ZonedDateTimeCodec implements Codec<ZonedDateTime> {
            @Override
            public ZonedDateTime decode(final BsonReader reader, final DecoderContext decoderContext) {
                final BsonTimestamp stamp = reader.readTimestamp();
                final Date dt = new Date(stamp.getTime());
                return DateTimeUtils.zonedDateTimeOf(dt);
            }

            @Override
            public void encode(final BsonWriter writer, final ZonedDateTime zonedDateTime, final EncoderContext encoderContext) {
                writer.writeTimestamp(new BsonTimestamp(DateTimeUtils.dateOf(zonedDateTime).getTime()));
            }

            @Override
            public Class<ZonedDateTime> getEncoderClass() {
                return ZonedDateTime.class;
            }
        }
    }
}
