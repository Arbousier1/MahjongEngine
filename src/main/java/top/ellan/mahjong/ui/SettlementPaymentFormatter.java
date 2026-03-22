package top.ellan.mahjong.ui;

import top.ellan.mahjong.i18n.MessageService;
import top.ellan.mahjong.riichi.model.SettlementPayment;
import top.ellan.mahjong.riichi.model.SettlementPaymentType;
import top.ellan.mahjong.riichi.model.YakuSettlement;
import top.ellan.mahjong.table.core.MahjongTableSession;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;

final class SettlementPaymentFormatter {
    private SettlementPaymentFormatter() {
    }

    static void appendBreakdown(
        List<Component> lore,
        Locale locale,
        MahjongTableSession session,
        YakuSettlement settlement
    ) {
        MessageService messages = session.plugin().messages();
        for (SettlementPayment payment : settlement.getPaymentBreakdown()) {
            if (payment.getType() == SettlementPaymentType.RIICHI_POOL) {
                lore.add(messages.render(locale, "ui.payment.riichi_pool", messages.number(locale, "value", payment.getAmount())));
                continue;
            }
            String payer = payment.getPayerUuid().isBlank() ? "-" : displayName(session, payment.getPayerUuid());
            String label = switch (payment.getType()) {
                case RON -> messages.plain(locale, "ui.payment.ron");
                case TSUMO -> messages.plain(locale, "ui.payment.tsumo");
                case PAO -> messages.plain(locale, "ui.payment.pao");
                case HONBA -> messages.plain(locale, "ui.payment.honba");
                default -> payment.getType().name();
            };
            String note = payment.getNote().isBlank() ? "" : " (" + paymentNoteLabel(session, locale, payment.getNote()) + ")";
            lore.add(messages.render(
                locale,
                "ui.payment.line",
                messages.tag("kind", label + note),
                messages.tag("payer", payer),
                messages.number(locale, "value", payment.getAmount())
            ));
        }
    }

    static String displayName(MahjongTableSession session, String stringUuid) {
        try {
            String displayName = session.displayName(UUID.fromString(stringUuid));
            return displayName == null || displayName.isBlank() ? stringUuid : displayName;
        } catch (IllegalArgumentException ex) {
            return stringUuid;
        }
    }

    private static String paymentNoteLabel(MahjongTableSession session, Locale locale, String note) {
        return switch (note) {
            case "DAISUUSHI" -> translatedLabel(session, locale, "double_yakuman", "DAISUSHI");
            case "DAISANGEN" -> translatedLabel(session, locale, "yakuman", note);
            default -> note;
        };
    }

    private static String translatedLabel(MahjongTableSession session, Locale locale, String prefix, String rawValue) {
        String normalized = rawValue.toLowerCase(Locale.ROOT);
        String key = prefix + "." + normalized;
        return session.plugin().messages().contains(locale, key)
            ? session.plugin().messages().plain(locale, key)
            : rawValue;
    }
}

