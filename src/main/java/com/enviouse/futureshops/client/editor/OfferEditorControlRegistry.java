package com.enviouse.futureshops.client.editor;

import java.util.LinkedHashSet;
import java.util.Set;

public final class OfferEditorControlRegistry {
    private static final String PREFIX =
            "gui.futureshops.offer_editor.";
    private static final Set<String> CONTROLS = Set.of(
            "active", "no_category", "select_category",
            "use_held_icon", "pick_inventory", "search_registry",
            "output", "remove_output",
            "duplicate_output", "previous_output", "next_output",
            "move_output_up", "move_output_down",
            "free", "money", "items", "compound",
            "previous_option", "next_option", "duplicate_option",
            "move_option_up", "move_option_down", "item_cost",
            "remove_cost", "remove_option", "sell_to_shop",
            "sell_input", "remove_input", "previous_component",
            "next_component", "move_component_up",
            "move_component_down", "remove_component",
            "decrease_component_count",
            "increase_component_count",
            "unlimited_stock",
            "bundle_comparison", "select_comparison",
            "revert", "apply", "save_close",
            "cancel", "help", "duplicate", "remove_listing",
            "reload_server", "review_changes", "preview_state",
            "preview_mode", "reset_section", "back_to_local_draft",
            "template", "back_to_templates");
    private static final Set<String> FIELDS = Set.of(
            "displayName", "description", "categoryId", "permission",
            "iconItemId", "iconNbt", "outputs.itemId", "outputs.count",
            "outputs.exactNbt", "acquireOptions.label",
            "acquireOptions.moneyCost",
            "acquireOptions.itemCosts.itemId",
            "acquireOptions.itemCosts.count",
            "acquireOptions.itemCosts.exactNbt",
            "acquireOptions.outputMultiplier",
            "acquireOptions.permission", "acquireOptions.startsAtEpoch",
            "acquireOptions.endsAtEpoch",
            "acquireOptions.maximumPerRequest",
            "acquireOptions.lifetime", "acquireOptions.periodQuantity",
            "acquireOptions.periodSeconds",
            "acquireOptions.cooldownSeconds", "sellOptions.label",
            "sellOptions.moneyPayout", "sellOptions.capacity",
            "sellOptions.itemInputs.itemId",
            "sellOptions.itemInputs.count",
            "sellOptions.itemInputs.exactNbt",
            "sellOptions.permission", "sellOptions.startsAtEpoch",
            "sellOptions.endsAtEpoch",
            "sellOptions.maximumPerRequest", "sellOptions.lifetime",
            "sellOptions.periodQuantity", "sellOptions.periodSeconds",
            "sellOptions.cooldownSeconds", "stock.quantity",
            "stock.refreshSeconds", "limits.maximumPerRequest",
            "limits.lifetime", "limits.periodQuantity",
            "limits.periodSeconds", "limits.cooldownSeconds",
            "schedule.startsAtEpoch", "schedule.endsAtEpoch",
            "expiresAtEpoch", "bundleComparisons");

    private OfferEditorControlRegistry() {
    }

    public static String helpKey(String control) {
        if (!CONTROLS.contains(control)) {
            throw new IllegalArgumentException(
                    "Unregistered offer editor control");
        }
        return PREFIX + "help." + control;
    }

    public static String fieldLabelKey(String field) {
        if (!FIELDS.contains(field)) {
            throw new IllegalArgumentException(
                    "Unregistered offer editor field");
        }
        return PREFIX + "field." + field;
    }

    public static String fieldHelpKey(String field) {
        return PREFIX + "help.field."
                + registeredField(field);
    }

    public static Set<String> requiredTranslationKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        CONTROLS.forEach(control -> keys.add(helpKey(control)));
        FIELDS.forEach(field -> {
            keys.add(fieldLabelKey(field));
            keys.add(fieldHelpKey(field));
        });
        for (OfferEditorDraft.Section section
                : OfferEditorDraft.Section.values()) {
            String name = section.name().toLowerCase(
                    java.util.Locale.ROOT);
            keys.add(PREFIX + "section." + name);
            keys.add(PREFIX + "help.section." + name);
        }
        for (OfferEditorTemplates.Template template
                : OfferEditorTemplates.Template.values()) {
            keys.add(PREFIX + "template." + template.key());
            keys.add(PREFIX + "help.template." + template.key());
        }
        return Set.copyOf(keys);
    }

    public static Set<String> controls() {
        return CONTROLS;
    }

    public static Set<String> fields() {
        return FIELDS;
    }

    private static String registeredField(String field) {
        if (!FIELDS.contains(field)) {
            throw new IllegalArgumentException(
                    "Unregistered offer editor field");
        }
        return field;
    }
}
