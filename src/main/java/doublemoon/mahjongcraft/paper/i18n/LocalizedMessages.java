package doublemoon.mahjongcraft.paper.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class LocalizedMessages {
    public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("zh-CN");
    private static final String MESSAGE_INDEX_RESOURCE = "i18n/_index.json";

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final MessageBundleIndex index = MessageBundleIndex.load();
    private final Map<Locale, Map<String, String>> bundles = new ConcurrentHashMap<>();

    public Component render(Locale locale, String key, TagResolver... placeholders) {
        String template = this.template(this.resolveLocale(locale), key);
        return this.miniMessage.deserialize(template, TagResolver.resolver(placeholders));
    }

    public String plain(Locale locale, String key, TagResolver... placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(this.render(locale, key, placeholders));
    }

    public boolean contains(Locale locale, String key) {
        return this.bundle(this.resolveLocale(locale)).containsKey(key)
            || this.bundle(this.index.defaultLocale()).containsKey(key)
            || this.bundle(Locale.ENGLISH).containsKey(key);
    }

    public TagResolver tag(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    public TagResolver number(Locale locale, String key, Number value) {
        return Placeholder.unparsed(key, NumberFormat.getIntegerInstance(this.resolveLocale(locale)).format(value));
    }

    public Locale normalizeLocale(String rawLocale) {
        if (rawLocale == null || rawLocale.isBlank()) {
            return this.index.defaultLocale();
        }
        Locale locale = Locale.forLanguageTag(rawLocale.replace('_', '-'));
        return locale.getLanguage().isBlank() ? this.index.defaultLocale() : this.resolveLocale(locale);
    }

    private Locale resolveLocale(Locale locale) {
        return this.index.resolve(locale == null ? this.index.defaultLocale() : locale);
    }

    private String template(Locale locale, String key) {
        String template = this.bundle(locale).get(key);
        if (template != null) {
            return template;
        }
        template = this.bundle(this.index.defaultLocale()).get(key);
        if (template != null) {
            return template;
        }
        template = this.bundle(Locale.ENGLISH).get(key);
        if (template != null) {
            return template;
        }
        throw new IllegalArgumentException("Missing message key: " + key);
    }

    private Map<String, String> bundle(Locale locale) {
        Locale resolved = this.resolveLocale(locale);
        return this.bundles.computeIfAbsent(resolved, this::loadBundle);
    }

    private Map<String, String> loadBundle(Locale locale) {
        String resourceName = this.index.resourceName(locale);
        if (resourceName == null) {
            return Map.of();
        }

        Properties properties = new Properties();
        try (
            InputStream inputStream = LocalizedMessages.class.getClassLoader().getResourceAsStream(resourceName);
            Reader reader = inputStream == null ? null : new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        ) {
            if (reader == null) {
                throw new IllegalStateException("Missing message bundle resource: " + resourceName);
            }
            properties.load(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load message bundle: " + resourceName, exception);
        }

        Map<String, String> loaded = new LinkedHashMap<>();
        properties.forEach((key, value) -> loaded.put(String.valueOf(key), String.valueOf(value)));
        return Map.copyOf(loaded);
    }

    private static final class MessageBundleIndex {
        private static final Pattern DEFAULT_LOCALE_PATTERN = Pattern.compile("\"defaultLocale\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern BUNDLE_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+\\.properties)\"");
        private static final MessageBundleIndex FALLBACK = new MessageBundleIndex(
            DEFAULT_LOCALE,
            Map.of(
                "en", "messages.properties",
                DEFAULT_LOCALE.toLanguageTag(), "messages_zh_CN.properties"
            )
        );

        private final Locale defaultLocale;
        private final Map<String, String> bundles;

        private MessageBundleIndex(Locale defaultLocale, Map<String, String> bundles) {
            this.defaultLocale = defaultLocale;
            this.bundles = Map.copyOf(bundles);
        }

        static MessageBundleIndex load() {
            try (InputStream inputStream = LocalizedMessages.class.getClassLoader().getResourceAsStream(MESSAGE_INDEX_RESOURCE)) {
                if (inputStream == null) {
                    return FALLBACK;
                }

                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Matcher defaultLocaleMatcher = DEFAULT_LOCALE_PATTERN.matcher(json);
                Locale defaultLocale = defaultLocaleMatcher.find()
                    ? Locale.forLanguageTag(defaultLocaleMatcher.group(1))
                    : DEFAULT_LOCALE;

                Map<String, String> bundles = new LinkedHashMap<>();
                Matcher bundleMatcher = BUNDLE_PATTERN.matcher(json);
                while (bundleMatcher.find()) {
                    bundles.put(bundleMatcher.group(1), bundleMatcher.group(2));
                }
                if (bundles.isEmpty()) {
                    return FALLBACK;
                }
                return new MessageBundleIndex(defaultLocale, bundles);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to load " + MESSAGE_INDEX_RESOURCE, exception);
            }
        }

        Locale defaultLocale() {
            return this.defaultLocale;
        }

        Locale resolve(Locale requestedLocale) {
            Locale safeLocale = requestedLocale == null ? this.defaultLocale : requestedLocale;
            String exactTag = safeLocale.toLanguageTag();
            if (this.bundles.containsKey(exactTag)) {
                return Locale.forLanguageTag(exactTag);
            }

            String language = safeLocale.getLanguage();
            if (language != null && !language.isBlank()) {
                if (this.bundles.containsKey(language)) {
                    return Locale.forLanguageTag(language);
                }
                for (String supportedLocale : this.bundles.keySet()) {
                    if (supportedLocale.startsWith(language + "-")) {
                        return Locale.forLanguageTag(supportedLocale);
                    }
                }
            }

            return this.defaultLocale;
        }

        String resourceName(Locale locale) {
            return this.bundles.get(this.resolve(locale).toLanguageTag());
        }
    }
}
