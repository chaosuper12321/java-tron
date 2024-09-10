package org.tron.core.db;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.tron.common.runtime.ProgramResult;
import org.tron.common.runtime.RuntimeImpl;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.ChainBaseManager;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.capsule.TransactionInfoCapsule;
import org.tron.core.capsule.utils.TransactionUtil;
import org.tron.core.store.AccountStore;
import org.tron.core.store.BalanceTraceStore;
import org.tron.core.store.DynamicPropertiesStore;
import org.tron.protos.Protocol;
import org.tron.protos.contract.BalanceContract;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Manager.class, TransactionUtil.class})
@Slf4j
public class ManagerMockTest {
  @InjectMocks
  @Resource
  private Manager dbManager;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.openMocks(this);
  }

  @After
  public void  clearMocks() {
    Mockito.framework().clearInlineMocks();
  }

  @Test
  public void processTransactionCostTimeMoreThan100() throws Exception {
    Manager managerSpy = spy(dbManager);
    BalanceContract.TransferContract transferContract =
        BalanceContract.TransferContract.newBuilder()
            .setAmount(10)
            .setOwnerAddress(ByteString.copyFromUtf8("aaa"))
            .setToAddress(ByteString.copyFromUtf8("bbb"))
            .build();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 6666; i++) {
      sb.append("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }
    Protocol.Transaction transaction = Protocol.Transaction.newBuilder().setRawData(
        Protocol.Transaction.raw.newBuilder()
            .setData(ByteString.copyFrom(sb.toString().getBytes(StandardCharsets.UTF_8)))
            .addContract(
                Protocol.Transaction.Contract.newBuilder()
                    .setParameter(Any.pack(transferContract))
                    .setType(Protocol.Transaction.Contract.ContractType.TransferContract)))
        .build();
    TransactionCapsule trxCap = new TransactionCapsule(transaction);
    ProgramResult result = new ProgramResult();
    result.setResultCode(Protocol.Transaction.Result.contractResult.SUCCESS);

    Sha256Hash transactionId = trxCap.getTransactionId();
    TransactionCapsule trxCapMock = mock(TransactionCapsule.class);
    TransactionTrace traceMock = mock(TransactionTrace.class);
    RuntimeImpl runtimeMock = mock(RuntimeImpl.class);
    BandwidthProcessor bandwidthProcessorMock = mock(BandwidthProcessor.class);
    ChainBaseManager chainBaseManagerMock = mock(ChainBaseManager.class);
    BalanceTraceStore balanceTraceStoreMock = mock(BalanceTraceStore.class);
    TransactionStore transactionStoreMock = mock(TransactionStore.class);
    TransactionInfoCapsule transactionInfoCapsuleMock = mock(TransactionInfoCapsule.class);
    Protocol.TransactionInfo transactionInfo = Protocol.TransactionInfo.newBuilder().build();

    // mock static
    PowerMockito.mockStatic(TransactionUtil.class);

    managerSpy.setChainBaseManager(chainBaseManagerMock);
    BlockCapsule blockCapMock = Mockito.mock(BlockCapsule.class);

    PowerMockito.when(TransactionUtil.buildTransactionInfoInstance(trxCapMock, blockCapMock, traceMock)).thenReturn(transactionInfoCapsuleMock);

    // this make cost > 100 cond is true
    PowerMockito.when(blockCapMock.isMerkleRootEmpty()).thenAnswer(new Answer<Boolean>() {
      @Override
      public Boolean answer(InvocationOnMock invocation) throws Throwable {
        Thread.sleep(100);
        return true;
      }
    });

    when(chainBaseManagerMock.getBalanceTraceStore()).thenReturn(balanceTraceStoreMock);
    when(chainBaseManagerMock.getAccountStore()).thenReturn(mock(AccountStore.class));
    when(chainBaseManagerMock.getDynamicPropertiesStore()).thenReturn(mock(DynamicPropertiesStore.class));
    when(chainBaseManagerMock.getTransactionStore()).thenReturn(transactionStoreMock);
    when(trxCapMock.getTransactionId()).thenReturn(transactionId);
    when(traceMock.getRuntimeResult()).thenReturn(result);
    when(transactionInfoCapsuleMock.getId()).thenReturn(transactionId.getBytes());
    when(transactionInfoCapsuleMock.getInstance()).thenReturn(transactionInfo);

    doNothing().when(managerSpy).validateTapos(trxCapMock);
    doNothing().when(managerSpy).validateCommon(trxCapMock);
    doNothing().when(managerSpy).validateDup(trxCapMock);

    // mock construct
    PowerMockito.whenNew(RuntimeImpl.class).withAnyArguments().thenReturn(runtimeMock);
    PowerMockito.whenNew(TransactionTrace.class).withAnyArguments().thenReturn(traceMock);
    PowerMockito.whenNew(BandwidthProcessor.class).withAnyArguments().thenReturn(bandwidthProcessorMock);

    doNothing().when(transactionStoreMock).put(transactionId.getBytes(), trxCapMock);
    doNothing().when(bandwidthProcessorMock).consume(trxCapMock, traceMock);
    doNothing().when(managerSpy).consumeBandwidth(trxCapMock, traceMock);
    doNothing().when(balanceTraceStoreMock).initCurrentTransactionBalanceTrace(trxCapMock);
    doNothing().when(balanceTraceStoreMock).updateCurrentTransactionStatus(anyString());
    doNothing().when(balanceTraceStoreMock).resetCurrentTransactionTrace();

    when(trxCapMock.getInstance()).thenReturn(trxCap.getInstance());
    when(trxCapMock.validatePubSignature(
        Mockito.any(AccountStore.class),
        Mockito.any(DynamicPropertiesStore.class))).thenReturn(true);
    when(trxCapMock.validateSignature(
        Mockito.any(AccountStore.class),
        Mockito.any(DynamicPropertiesStore.class))).thenReturn(true);

    assertNotNull(managerSpy.processTransaction(trxCapMock, blockCapMock));
  }

}
