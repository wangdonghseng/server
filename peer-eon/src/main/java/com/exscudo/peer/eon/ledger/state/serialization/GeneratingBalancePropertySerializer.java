package com.exscudo.peer.eon.ledger.state.serialization;

import java.io.IOException;
import java.util.HashMap;

import com.exscudo.peer.core.data.Account;
import com.exscudo.peer.core.data.AccountProperty;
import com.exscudo.peer.eon.PropertyType;
import com.exscudo.peer.eon.ledger.AccountPropertySerializer;
import com.exscudo.peer.eon.ledger.state.GeneratingBalanceProperty;

public class GeneratingBalancePropertySerializer extends AccountPropertySerializer<GeneratingBalanceProperty> {

    public GeneratingBalancePropertySerializer() {
        super(GeneratingBalanceProperty.class);
    }

    @Override
    public Account doSerialize(GeneratingBalanceProperty generatingBalance, Account account) throws IOException {

        if (generatingBalance.getValue() != 0L) {
            HashMap<String, Object> data = new HashMap<>();
            data.put("amount", generatingBalance.getValue());
            data.put("timestamp", generatingBalance.getTimestamp());

            return account.putProperty(new AccountProperty(PropertyType.DEPOSIT, data));
        } else {
            return account.removeProperty(PropertyType.DEPOSIT);
        }
    }
}
