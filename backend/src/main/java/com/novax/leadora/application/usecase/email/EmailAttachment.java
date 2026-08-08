package com.novax.leadora.application.usecase.email;

import java.io.InputStream;
import java.util.function.Supplier;

public record EmailAttachment(
    String filename,
    Supplier<InputStream> streamSupplier
) {}
