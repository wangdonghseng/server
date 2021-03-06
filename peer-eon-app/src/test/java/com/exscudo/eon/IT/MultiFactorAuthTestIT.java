package com.exscudo.eon.IT;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import com.exscudo.eon.api.bot.AccountBotService;
import com.exscudo.peer.core.IFork;
import com.exscudo.peer.core.common.TimeProvider;
import com.exscudo.peer.core.crypto.ISigner;
import com.exscudo.peer.core.crypto.ed25519.Ed25519Signer;
import com.exscudo.peer.core.data.Block;
import com.exscudo.peer.core.data.Transaction;
import com.exscudo.peer.core.data.identifier.AccountID;
import com.exscudo.peer.core.storage.Storage;
import com.exscudo.peer.eon.Fork;
import com.exscudo.peer.eon.ForkInitializer;
import com.exscudo.peer.eon.TransactionType;
import com.exscudo.peer.eon.tx.builders.DelegateBuilder;
import com.exscudo.peer.eon.tx.builders.PaymentBuilder;
import com.exscudo.peer.eon.tx.builders.QuorumBuilder;
import com.exscudo.peer.eon.tx.builders.RegistrationBuilder;
import com.exscudo.peer.eon.tx.builders.RejectionBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;

@Category(IIntegrationTest.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MultiFactorAuthTestIT {
    private static final String GENERATOR = "eba54bbb2dd6e55c466fac09707425145ca8560fe40de3fa3565883f4d48779e";
    private static final String DELEGATE_1 = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
    private static final String DELEGATE_2 = "112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00";
    private static final String DELEGATE_NEW = "2233445566778899aabbccddeeff00112233445566778899aabbccddeeff0000";

    private TimeProvider mockTimeProvider;
    private PeerContext ctx;

    @Before
    public void setUp() throws Exception {
        mockTimeProvider = Mockito.mock(TimeProvider.class);

        Storage storage = Utils.createStorage();
        long timestamp = Utils.getLastBlock(storage).getTimestamp();
        String begin = Instant.ofEpochMilli((timestamp - 1) * 1000).toString();
        String end = Instant.ofEpochMilli((timestamp + 10 * 180 * 1000) * 1000).toString();
        IFork fork = new Fork(Utils.getGenesisBlockID(storage),
                              new Fork.Item[] {new Fork.Item(1, begin, end, ForkInitializer.items[0].handler, 2)});
        ctx = new PeerContext(GENERATOR, mockTimeProvider, storage, fork);
    }

    @Test
    public void step_1_mfa() throws Exception {

        ISigner delegate_1 = new Ed25519Signer(DELEGATE_1);
        ISigner delegate_2 = new Ed25519Signer(DELEGATE_2);

        Block lastBlock = ctx.blockExplorerService.getLastBlock();
        int timestamp = lastBlock.getTimestamp();

        // registration

        Mockito.when(mockTimeProvider.get()).thenReturn(timestamp + 180 + 1);

        Transaction tx1 = RegistrationBuilder.createNew(delegate_1.getPublicKey())
                                             .validity(timestamp + 1, 3600)
                                             .build(ctx.getSigner());
        ctx.transactionBotService.putTransaction(tx1);
        Transaction tx2 = RegistrationBuilder.createNew(delegate_2.getPublicKey())
                                             .validity(timestamp + 1, 3600)
                                             .build(ctx.getSigner());
        ctx.transactionBotService.putTransaction(tx2);

        ctx.generateBlockForNow();
        Assert.assertEquals("Registration in block",
                            2,
                            ctx.blockExplorerService.getLastBlock().getTransactions().size());

        // payments
        Mockito.when(mockTimeProvider.get()).thenReturn(timestamp + 2 * 180 + 1);

        AccountID delegate_1_id = new AccountID(delegate_1.getPublicKey());
        Transaction tx3 = PaymentBuilder.createNew(1000L, delegate_1_id)
                                        .validity(timestamp + 180 + 1, 3600)
                                        .build(ctx.getSigner());
        ctx.transactionBotService.putTransaction(tx3);
        AccountID delegate_2_id = new AccountID(delegate_2.getPublicKey());
        Transaction tx4 = PaymentBuilder.createNew(1000L, delegate_2_id)
                                        .validity(timestamp + 180 + 1, 3600)
                                        .build(ctx.getSigner());
        ctx.transactionBotService.putTransaction(tx4);

        ctx.generateBlockForNow();
        Assert.assertEquals("Payments in block", 2, ctx.blockExplorerService.getLastBlock().getTransactions().size());

        // set quorum and delegates
        Mockito.when(mockTimeProvider.get()).thenReturn(timestamp + 3 * 180 + 1);

        Transaction tx6 = DelegateBuilder.createNew(delegate_1_id, 30)
                                         .validity(timestamp + 2 * 180 + 1, 3600)
                                         .build(ctx.getSigner());
        ctx.transactionBotService.putTransaction(tx6);
        Transaction tx7 = DelegateBuilder.createNew(delegate_2_id, 20)
                                         .validity(timestamp + 2 * 180 + 1, 3600)
                                         .build(ctx.getSigner());
        ctx.transactionBotService.putTransaction(tx7);
        Transaction tx8 = QuorumBuilder.createNew(50)
                                       .quorumForType(TransactionType.Payment, 85)
                                       .validity(timestamp + 2 * 180 + 1, 3600)
                                       .build(ctx.getSigner());
        ctx.transactionBotService.putTransaction(tx8);

        ctx.generateBlockForNow();
        Assert.assertEquals("Transactions in block",
                            3,
                            ctx.blockExplorerService.getLastBlock().getTransactions().size());

        String id1 = delegate_1_id.toString();
        AccountBotService.Info info1 = ctx.accountBotService.getInformation(id1);
        AccountID signer_id = new AccountID(ctx.getSigner().getPublicKey());
        assertTrue(info1.voter.get(signer_id.toString()) == 30);

        String id2 = delegate_2_id.toString();
        AccountBotService.Info info2 = ctx.accountBotService.getInformation(id2);
        assertTrue(info2.voter.get(signer_id.toString()) == 20);

        // enable mfa
        Mockito.when(mockTimeProvider.get()).thenReturn(timestamp + 4 * 180 + 1);

        Transaction tx5 =
                DelegateBuilder.createNew(signer_id, 50).validity(timestamp + 3 * 180 + 1, 3600).build(ctx.getSigner());
        ctx.transactionBotService.putTransaction(tx5);

        ctx.generateBlockForNow();
        Assert.assertEquals("Transactions in block",
                            1,
                            ctx.blockExplorerService.getLastBlock().getTransactions().size());

        // try put transaction
        ISigner delegate_new = new Ed25519Signer(DELEGATE_NEW);
        Mockito.when(mockTimeProvider.get()).thenReturn(timestamp + 180 * 5 + 1);

        Transaction tx9 = RegistrationBuilder.createNew(delegate_new.getPublicKey())
                                             .validity(timestamp + 4 * 180 + 1, 3600)
                                             .build(ctx.getSigner());
        ctx.transactionBotService.putTransaction(tx9);
        Transaction tx10 = PaymentBuilder.createNew(100L, delegate_1_id)
                                         .validity(timestamp + 4 * 180 + 1, 3600)
                                         .build(ctx.getSigner());
        try {
            ctx.transactionBotService.putTransaction(tx10);
        } catch (Exception ignore) {

        }

        ctx.generateBlockForNow();
        Assert.assertEquals("Transactions in block",
                            1,
                            ctx.blockExplorerService.getLastBlock().getTransactions().size());

        // try put transaction
        Mockito.when(mockTimeProvider.get()).thenReturn(timestamp + 180 * 6 + 1);

        Transaction tx11 = PaymentBuilder.createNew(100L, delegate_1_id)
                                         .validity(timestamp + 180 * 5 + 1, 3600)
                                         .build(ctx.getSigner(), new ISigner[] {delegate_1});
        try {
            ctx.transactionBotService.putTransaction(tx11);
        } catch (Exception ignore) {

        }

        ctx.generateBlockForNow();
        Assert.assertEquals("Transactions in block",
                            0,
                            ctx.blockExplorerService.getLastBlock().getTransactions().size());

        // put transaction
        Mockito.when(mockTimeProvider.get()).thenReturn(timestamp + 180 * 7 + 1);

        Transaction tx12 = PaymentBuilder.createNew(100L, delegate_1_id)
                                         .validity(timestamp + 180 * 6 + 1, 3600)
                                         .build(ctx.getSigner(), new ISigner[] {delegate_1, delegate_2});
        ctx.transactionBotService.putTransaction(tx12);

        ctx.generateBlockForNow();
        Assert.assertEquals("Transactions in block",
                            1,
                            ctx.blockExplorerService.getLastBlock().getTransactions().size());

        // reject
        Mockito.when(mockTimeProvider.get()).thenReturn(timestamp + 180 * 8 + 1);

        Transaction tx13 =
                RejectionBuilder.createNew(signer_id).validity(timestamp + 180 * 7 + 1, 3600).build(delegate_2);
        ctx.transactionBotService.putTransaction(tx13);

        ctx.generateBlockForNow();
        Assert.assertEquals("Transactions in block",
                            1,
                            ctx.blockExplorerService.getLastBlock().getTransactions().size());
        info2 = ctx.accountBotService.getInformation(id2);
        assertNull(info2.voter);

        // put transaction
        Mockito.when(mockTimeProvider.get()).thenReturn(timestamp + 180 * 9 + 1);
        ctx.transactionBotService.putTransaction(tx11);
        ctx.generateBlockForNow();
        Assert.assertEquals("Transactions in block",
                            1,
                            ctx.blockExplorerService.getLastBlock().getTransactions().size());
    }
}
