package com.example.rpctest;

import com.alipay.sofa.rpc.client.ProviderHelper;
import com.alipay.sofa.rpc.common.RpcConstants;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import com.alipay.sofa.rpc.context.RpcInternalContext;
import com.alipay.sofa.rpc.context.RpcInvokeContext;
import com.alipay.sofa.rpc.context.RpcRunningState;
import com.alipay.sofa.rpc.context.RpcRuntimeContext;
import com.alipay.sofa.rpc.log.LogCodes;
import com.alipay.sofa.rpc.server.ServerFactory;
import com.alipay.sofa.rpc.transport.bolt.BoltClientTransport;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.concurrent.Callable;

@SpringBootTest
class RpcTestApplicationTests {

    private final static Logger LOGGER = LoggerFactory.getLogger(RpcTestApplicationTests.class);

    @BeforeAll
    public static void adBeforeClass() {
        RpcRunningState.setUnitTestMode(true);
    }

    @AfterAll
    public static void adAfterClass() {
        RpcRuntimeContext.destroy();
        RpcInternalContext.removeContext();
        RpcInvokeContext.removeContext();
    }

    @Test
    public void testReconnect() throws Exception {
        String serverHost = System.getenv("SERVER_HOST");
        if (StringUtils.isEmpty(serverHost)) {
            serverHost = "127.0.0.1";
        }
        LOGGER.info("Server host is {}", serverHost);

        ServerConfig serverConfig1 = new ServerConfig()
                .setStopTimeout(0)
                .setHost("0.0.0.0")
                .setPort(22221)
                .setProtocol(RpcConstants.PROTOCOL_TYPE_BOLT)
                .setQueues(100).setCoreThreads(5).setMaxThreads(5);
        ProviderConfig<HelloService> providerConfig = new ProviderConfig<HelloService>()
                .setInterfaceId(HelloService.class.getName())
                .setRef(new HelloServiceImpl())
                .setServer(serverConfig1)
                .setRepeatedExportLimit(-1)
                .setRegister(false);
        providerConfig.export();

        final ConsumerConfig<HelloService> consumerConfig = new ConsumerConfig<HelloService>()
                .setInterfaceId(HelloService.class.getName())
                .setDirectUrl("bolt://" + serverHost + ":22221")
                .setConnectionHolder("all")
                .setRegister(false)
                .setLazy(true)
                .setReconnectPeriod(2000)
                .setTimeout(3000);

        LOGGER.info("call first time");

        HelloService helloService = consumerConfig.refer();
        Assert.notNull(helloService.sayHello("xxx"), "returns should not be null\")");

        LOGGER.info("stop server");

        // Mock server down, and RPC will throw exception(no available provider)
        providerConfig.unExport();
        ServerFactory.destroyAll();

        BoltClientTransport clientTransport = (BoltClientTransport) consumerConfig.getConsumerBootstrap().getCluster()
                .getConnectionHolder()
                .getAvailableClientTransport(ProviderHelper.toProviderInfo("bolt://" + serverHost + ":22221"));

        clientTransport.disconnect();

        LOGGER.info("call second time");

        TestUtils.delayGet(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return consumerConfig.getConsumerBootstrap().getCluster().getConnectionHolder().isAvailableEmpty();
            }
        }, true, 100, 30);

        try {
            helloService.sayHello("xxx");
            Assert.isTrue(false, "should not reach here");
        } catch (Exception e) {
            Assert.isTrue(e.getMessage().contains(LogCodes.ERROR_TARGET_URL_INVALID), "error message should contain code " + LogCodes.ERROR_TARGET_URL_INVALID + ", but got :" + e.getMessage());
        }

        LOGGER.info("restart server");

        // Mock server restart
        serverConfig1 = new ServerConfig()
                .setStopTimeout(0)
                .setHost("0.0.0.0")
                .setPort(22221)
                .setProtocol(RpcConstants.PROTOCOL_TYPE_BOLT)
                .setQueues(100).setCoreThreads(5).setMaxThreads(5);
        providerConfig.setServer(Arrays.asList(serverConfig1)).export();

        LOGGER.info("call third time");

        // The consumer will reconnect to provider automatically
        TestUtils.delayGet(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return consumerConfig.getConsumerBootstrap().getCluster().getConnectionHolder().isAvailableEmpty();
            }
        }, false, 100, 30);
        // RPC return success
        Assert.notNull(helloService.sayHello("xxx"), "returns should not be null");

        LOGGER.info("finish");
    }

}
