package com.exscudo.eon.IT;

import com.exscudo.peer.core.common.TimeProvider;
import com.exscudo.peer.core.data.Block;
import com.exscudo.peer.core.data.Transaction;
import com.exscudo.peer.core.data.identifier.AccountID;
import com.exscudo.peer.eon.tx.builders.PaymentBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;

@Category(IIntegrationTest.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BacklogTestIT {

    private static String GENERATOR = "eba54bbb2dd6e55c466fac09707425145ca8560fe40de3fa3565883f4d48779e";
    private static String GENERATOR2 = "d2005ef0df1f6926082aefa09917874cfb212d1ff4eb55c78f670ef9dd23ef6c";
    private TimeProvider mockTimeProvider;

    private PeerContext ctx1;
    private PeerContext ctx2;

    @Before
    public void setUp() throws Exception {
        mockTimeProvider = Mockito.mock(TimeProvider.class);
        ctx1 = new PeerContext(GENERATOR, mockTimeProvider);
        ctx2 = new PeerContext(GENERATOR2, mockTimeProvider);

        ctx1.syncBlockPeerService = Mockito.spy(ctx1.syncBlockPeerService);
        ctx2.syncBlockPeerService = Mockito.spy(ctx2.syncBlockPeerService);

        ctx1.setPeerToConnect(ctx2);
        ctx2.setPeerToConnect(ctx1);
    }

    @Test
    public void step_1_backlog_load_forked_transactions() throws Exception {

        Block lastBlock = ctx1.blockExplorerService.getLastBlock();
        Mockito.when(mockTimeProvider.get()).thenReturn(lastBlock.getTimestamp() + 180 + 1);

        Transaction tx1 = PaymentBuilder.createNew(10000L, new AccountID(ctx2.getSigner().getPublicKey()))
                                        .validity(lastBlock.getTimestamp() + 150, 3600)
                                        .build(ctx1.getSigner());
        Transaction tx2 = PaymentBuilder.createNew(10000L, new AccountID(ctx2.getSigner().getPublicKey()))
                                        .validity(lastBlock.getTimestamp() + 100, 3600)
                                        .build(ctx1.getSigner());

        ctx1.transactionBotService.putTransaction(tx1);
        ctx2.transactionBotService.putTransaction(tx2);

        Assert.assertNotNull("Tx1 in backlog of ctx1", ctx1.backlogExplorerService.getById(tx1.getID().toString()));
        Assert.assertNotNull("Tx2 in backlog of ctx2", ctx2.backlogExplorerService.getById(tx2.getID().toString()));

        ctx1.generateBlockForNow();
        ctx2.generateBlockForNow();

        Assert.assertNull("Ctx1 backlog is empty", ctx1.backlogExplorerService.getById(tx1.getID().toString()));
        Assert.assertNull("Ctx2 backlog is empty", ctx2.backlogExplorerService.getById(tx2.getID().toString()));

        Assert.assertNotNull("Tx1 in blockchain of ctx1",
                             ctx1.transactionExplorerService.getById(tx1.getID().toString()));
        Assert.assertNotNull("Tx2 in blockchain of ctx2",
                             ctx2.transactionExplorerService.getById(tx2.getID().toString()));

        ctx1.fullBlockSync();
        ctx2.fullBlockSync();

        Assert.assertEquals("Blockchain synchronized",
                            ctx1.blockExplorerService.getLastBlock().getID(),
                            ctx2.blockExplorerService.getLastBlock().getID());

        lastBlock = ctx1.blockExplorerService.getLastBlock();

        Transaction[] transactionSet = lastBlock.getTransactions().toArray(new Transaction[0]);
        Assert.assertEquals("In last block single transaction", 1, transactionSet.length);

        if (transactionSet[0].getID().equals(tx1.getID())) {
            Assert.assertNotNull("Tx2 in backlog of ctx2", ctx2.backlogExplorerService.getById(tx2.getID().toString()));
        } else {
            Assert.assertNotNull("Tx1 in backlog of ctx1", ctx1.backlogExplorerService.getById(tx1.getID().toString()));
        }
    }
}
