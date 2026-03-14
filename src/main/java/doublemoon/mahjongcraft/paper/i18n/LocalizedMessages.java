package doublemoon.mahjongcraft.paper.i18n;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class LocalizedMessages {
    public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("zh-CN");
    private static final ResourceBundle.Control NO_DEFAULT_LOCALE_FALLBACK =
        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<Locale, ResourceBundle> bundles = new ConcurrentHashMap<>();

    public Component render(Locale locale, String key, TagResolver... placeholders) {
        ResourceBundle bundle = this.bundle(locale);
        String template = bundle.containsKey(key) ? bundle.getString(key) : this.bundle(DEFAULT_LOCALE).getString(key);
        return this.miniMessage.deserialize(template, TagResolver.resolver(placeholders));
    }

    public String plain(Locale locale, String key, TagResolver... placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(this.render(locale, key, placeholders));
    }

    public TagResolver tag(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    public TagResolver number(Locale locale, String key, Number value) {
        return Placeholder.unparsed(key, NumberFormat.getIntegerInstance(locale).format(value));
    }

    public Locale normalizeLocale(String rawLocale) {
        if (rawLocale == null || rawLocale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        String normalized = rawLocale.replace('_', '-');
        Locale locale = Locale.forLanguageTag(normalized);
        return locale.getLanguage().isBlank() ? DEFAULT_LOCALE : locale;
    }

    private ResourceBundle bundle(Locale locale) {
        return this.bundles.computeIfAbsent(
            locale,
            resolved -> ResourceBundle.getBundle("messages", resolved, NO_DEFAULT_LOCALE_FALLBACK)
        );
    }
}
