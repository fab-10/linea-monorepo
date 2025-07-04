/*
 * Copyright Consensys Software Inc.
 *
 * This file is dual-licensed under either the MIT license or Apache License 2.0.
 * See the LICENSE-MIT and LICENSE-APACHE files in the repository root for details.
 *
 * SPDX-License-Identifier: MIT OR Apache-2.0
 */

package org.hyperledger.besu.tests.acceptance.dsl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.math.BigInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Blockchain;
import org.hyperledger.besu.tests.acceptance.dsl.condition.admin.AdminConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.bft.BftConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.clique.CliqueConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.eth.EthConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.login.LoginConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.net.NetConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.perm.PermissioningConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.process.ExitedWithCode;
import org.hyperledger.besu.tests.acceptance.dsl.condition.txpool.TxPoolConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.web3.Web3Conditions;
import org.hyperledger.besu.tests.acceptance.dsl.contract.ContractVerifier;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeFactory;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.permissioning.PermissionedNodeBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.account.AccountTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.admin.AdminTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.bft.BftTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.clique.CliqueTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.contract.ContractTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.miner.MinerTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.net.NetTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.PermissioningTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.txpool.TxPoolTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.web3.Web3Transactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/** Base class for acceptance tests. */
@ExtendWith(AcceptanceTestBaseTestWatcher.class)
@Tag("AcceptanceTest")
@Slf4j
public abstract class AcceptanceTestBase {
  protected final Accounts accounts;
  protected final AccountTransactions accountTransactions;
  protected final AdminConditions admin;
  protected final AdminTransactions adminTransactions;
  protected final Blockchain blockchain;
  protected final CliqueConditions clique;
  protected final CliqueTransactions cliqueTransactions;
  protected final Cluster cluster;
  protected final ContractVerifier contractVerifier;
  protected final ContractTransactions contractTransactions;
  protected final EthConditions eth;
  protected final EthTransactions ethTransactions;
  protected final BftTransactions bftTransactions;
  protected final BftConditions bft;
  protected final LoginConditions login;
  protected final NetConditions net;
  protected final BesuNodeFactory besu;
  protected final PermissioningConditions perm;
  protected final PermissionedNodeBuilder permissionedNodeBuilder;
  protected final PermissioningTransactions permissioningTransactions;
  protected final MinerTransactions minerTransactions;
  protected final Web3Conditions web3;
  protected final TxPoolConditions txPoolConditions;
  protected final TxPoolTransactions txPoolTransactions;
  protected final ExitedWithCode exitedSuccessfully;

  private final ExecutorService outputProcessorExecutor = Executors.newCachedThreadPool();

  protected AcceptanceTestBase() {
    ethTransactions = new EthTransactions();
    accounts = new Accounts(ethTransactions);
    adminTransactions = new AdminTransactions();
    cliqueTransactions = new CliqueTransactions();
    bftTransactions = new BftTransactions();
    accountTransactions = new AccountTransactions(accounts);
    permissioningTransactions = new PermissioningTransactions();
    contractTransactions = new ContractTransactions();
    minerTransactions = new MinerTransactions();

    blockchain = new Blockchain(ethTransactions);
    clique = new CliqueConditions(ethTransactions, cliqueTransactions);
    eth = new EthConditions(ethTransactions);
    bft = new BftConditions(bftTransactions);
    login = new LoginConditions();
    net = new NetConditions(new NetTransactions());
    cluster = new Cluster(net);
    perm = new PermissioningConditions(permissioningTransactions);
    admin = new AdminConditions(adminTransactions);
    web3 = new Web3Conditions(new Web3Transactions());
    besu = new BesuNodeFactory();
    txPoolTransactions = new TxPoolTransactions();
    txPoolConditions = new TxPoolConditions(txPoolTransactions);
    contractVerifier = new ContractVerifier(accounts.getPrimaryBenefactor());
    permissionedNodeBuilder = new PermissionedNodeBuilder();
    exitedSuccessfully = new ExitedWithCode(0);
  }

  @AfterEach
  public void tearDownAcceptanceTestBase() {
    reportMemory();
    cluster.close();
  }

  /** Report memory usage after test execution. */
  public void reportMemory() {
    String os = System.getProperty("os.name");
    String[] command = null;
    if (os.contains("Linux")) {
      command = new String[] {"/usr/bin/top", "-n", "1", "-o", "%MEM", "-b", "-c", "-w", "180"};
    }
    if (os.contains("Mac")) {
      command = new String[] {"/usr/bin/top", "-l", "1", "-o", "mem", "-n", "20"};
    }
    if (command != null) {
      log.info("Memory usage at end of test:");
      final ProcessBuilder processBuilder =
          new ProcessBuilder(command).redirectErrorStream(true).redirectInput(Redirect.INHERIT);
      try {
        final Process memInfoProcess = processBuilder.start();
        outputProcessorExecutor.execute(() -> printOutput(memInfoProcess));
        memInfoProcess.waitFor();
        log.debug("Memory info process exited with code {}", memInfoProcess.exitValue());
      } catch (final Exception e) {
        log.warn("Error running memory information process", e);
      }
    } else {
      log.info("Don't know how to report memory for OS {}", os);
    }
  }

  private void printOutput(final Process process) {
    try (final BufferedReader in =
        new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {
      String line = in.readLine();
      while (line != null) {
        log.info(line);
        line = in.readLine();
      }
    } catch (final IOException e) {
      log.warn("Failed to read output from memory information process: ", e);
    }
  }

  protected void waitForBlockHeight(final Node node, final long blockchainHeight) {
    WaitUtils.waitFor(
        120,
        () ->
            assertThat(node.execute(ethTransactions.blockNumber()))
                .isGreaterThanOrEqualTo(BigInteger.valueOf(blockchainHeight)));
  }
}
