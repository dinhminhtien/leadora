package com.novax.leadora.infrastructure.integration.fcm;

import com.google.firebase.messaging.FirebaseMessagingException;

public class PermanentFcmException extends RuntimeException {
    public PermanentFcmException(FirebaseMessagingException cause) {
        super(cause);
    }
}
