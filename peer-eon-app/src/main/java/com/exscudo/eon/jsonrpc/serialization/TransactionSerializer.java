package com.exscudo.eon.jsonrpc.serialization;

import java.io.IOException;
import java.util.Map;

import com.exscudo.peer.core.data.Transaction;
import com.exscudo.peer.core.data.mapper.TransactionMapper;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * JSON custom serialisation of {@code Transaction}
 *
 * @see Transaction
 */
public class TransactionSerializer extends StdSerializer<Transaction> {
    private static final long serialVersionUID = -7988862783097982697L;

    public TransactionSerializer() {
        super(Transaction.class);
    }

    @Override
    public void serialize(Transaction value, JsonGenerator gen, SerializerProvider provider) throws IOException {

        Map<String, Object> map = TransactionMapper.convert(value);
        map.put(StringConstant.id, value.getID().toString());
        gen.writeObject(map);
    }
}
