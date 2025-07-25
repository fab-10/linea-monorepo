/*
 * Copyright Consensys Software Inc.
 *
 * This file is dual-licensed under either the MIT license or Apache License 2.0.
 * See the LICENSE-MIT and LICENSE-APACHE files in the repository root for details.
 *
 * SPDX-License-Identifier: MIT OR Apache-2.0
 */
package net.consensys.linea.sequencer.txselection.selectors;

import static java.lang.Boolean.TRUE;

import java.time.Instant;
import net.consensys.linea.bundles.TransactionBundle;
import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;

public class BundleConstraintTransactionSelector implements PluginTransactionSelector {

  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext txContext) {

    // short circuit if we are not a PendingBundleTx
    if (!(txContext.getPendingTransaction()
        instanceof TransactionBundle.PendingBundleTx pendingBundleTx)) {
      return TransactionSelectionResult.SELECTED;
    }

    final var bundle = pendingBundleTx.getBundle();

    final var satisfiesCriteria =
        bundle.minTimestamp().map(minTime -> minTime < Instant.now().getEpochSecond()).orElse(TRUE)
            && bundle
                .maxTimestamp()
                .map(maxTime -> maxTime > Instant.now().getEpochSecond())
                .orElse(TRUE);

    if (!satisfiesCriteria) {
      return TransactionSelectionResult.invalid("Failed Bundled Transaction Criteria");
    }
    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext txContext,
      final TransactionProcessingResult transactionProcessingResult) {

    // short circuit if we are not a PendingBundleTx
    if (!(txContext.getPendingTransaction()
        instanceof TransactionBundle.PendingBundleTx pendingBundleTx)) {
      return TransactionSelectionResult.SELECTED;
    }

    if (transactionProcessingResult.isFailed()) {
      final var revertableList = pendingBundleTx.getBundle().revertingTxHashes();

      // if a bundle tx failed, but was not in a revertable list, we unselect and fail the bundle
      if (revertableList.isEmpty()
          || !revertableList
              .get()
              .contains(txContext.getPendingTransaction().getTransaction().getHash())) {
        return TransactionSelectionResult.invalid("Failed non revertable transaction in bundle");
      }
    }
    return TransactionSelectionResult.SELECTED;
  }
}
