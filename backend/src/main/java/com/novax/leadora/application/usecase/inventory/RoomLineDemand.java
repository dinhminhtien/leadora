package com.novax.leadora.application.usecase.inventory;

import java.util.UUID;

/**
 * One room type and how many rooms of it a quotation or booking is asking for.
 *
 * <p>Identified by {@code productId}, never by name. The quotation flow used to carry only a
 * free-text room type and match it against {@code product_services.name} with
 * {@code equalsIgnoreCase}, which meant a renamed product silently detached every quotation that
 * referenced it, and a typed-in description could never match at all. Allotment is keyed on the
 * product, so the demand side has to be too.
 */
public record RoomLineDemand(UUID productId, int quantity) {
}
