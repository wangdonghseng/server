package com.exscudo.peer.eon;

import com.exscudo.peer.core.data.identifier.AccountID;
import com.exscudo.peer.core.data.identifier.BaseIdentifier;

public class ColoredCoinID extends BaseIdentifier {
    private static final String PREFIX = "EON-C";

    public ColoredCoinID(long id) {
        super(id, PREFIX);
    }

    public ColoredCoinID(String id) {
        super(id, PREFIX);
    }

    public ColoredCoinID(AccountID id) {
        super(id.getValue(), PREFIX);
    }

    public AccountID getIssierAccount() {
        return new AccountID(getValue());
    }
}
