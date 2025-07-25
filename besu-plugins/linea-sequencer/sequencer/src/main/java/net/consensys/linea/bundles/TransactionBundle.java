/*
 * Copyright Consensys Software Inc.
 *
 * This file is dual-licensed under either the MIT license or Apache License 2.0.
 * See the LICENSE-MIT and LICENSE-APACHE files in the repository root for details.
 *
 * SPDX-License-Identifier: MIT OR Apache-2.0
 */

package net.consensys.linea.bundles;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.parameters.UnsignedLongParameter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

/** TransactionBundle class representing a collection of pending transactions with metadata. */
@Accessors(fluent = true)
@Getter
@EqualsAndHashCode
@ToString
public class TransactionBundle {
  private static final AtomicLong BUNDLE_COUNT = new AtomicLong(0L);
  private final long sequence = BUNDLE_COUNT.incrementAndGet();
  private final Hash bundleIdentifier;
  private final List<PendingBundleTx> pendingTransactions;
  private final Long blockNumber;
  private final Optional<Long> minTimestamp;
  private final Optional<Long> maxTimestamp;
  private final Optional<List<Hash>> revertingTxHashes;
  private final Optional<UUID> replacementUUID;

  public TransactionBundle(
      final Hash bundleIdentifier,
      final List<Transaction> transactions,
      final Long blockNumber,
      final Optional<Long> minTimestamp,
      final Optional<Long> maxTimestamp,
      final Optional<List<Hash>> revertingTxHashes,
      final Optional<UUID> replacementUUID) {
    this.bundleIdentifier = bundleIdentifier;
    this.pendingTransactions = transactions.stream().map(PendingBundleTx::new).toList();
    this.blockNumber = blockNumber;
    this.minTimestamp = minTimestamp;
    this.maxTimestamp = maxTimestamp;
    this.revertingTxHashes = revertingTxHashes;
    this.replacementUUID = replacementUUID;
  }

  public BundleParameter toBundleParameter(final boolean compact) {
    return new BundleParameter(
        pendingTransactions.stream()
            .map(
                ptx ->
                    compact ? ptx.toBase64String() : ptx.getTransaction().encoded().toHexString())
            .toList(),
        new UnsignedLongParameter(blockNumber),
        minTimestamp,
        maxTimestamp,
        revertingTxHashes,
        replacementUUID.map(UUID::toString),
        Optional.empty());
  }

  @JsonValue
  public Map<Hash, BundleParameter> serialize() {
    return Map.of(bundleIdentifier, toBundleParameter(true));
  }

  @JsonCreator
  public static TransactionBundle deserialize(
      final SequencedMap<Hash, BundleParameter> serialized) {
    final var entry = serialized.firstEntry();
    final var hash = entry.getKey();
    final var parameters = entry.getValue();

    return new TransactionBundle(
        hash,
        parameters.txs().stream().map(Bytes::fromBase64String).map(Transaction::readFrom).toList(),
        parameters.blockNumber(),
        parameters.minTimestamp(),
        parameters.maxTimestamp(),
        parameters.revertingTxHashes(),
        parameters.replacementUUID().map(UUID::fromString));
  }

  /** A pending transaction contained in a bundle. */
  public class PendingBundleTx
      extends org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.Local {

    public PendingBundleTx(final Transaction transaction) {
      super(transaction);
    }

    public TransactionBundle getBundle() {
      return TransactionBundle.this;
    }

    public boolean isBundleStart() {
      return getBundle().pendingTransactions().getFirst().equals(this);
    }

    @Override
    public String toTraceLog() {
      return "Bundle tx: " + super.toTraceLog();
    }

    String toBase64String() {
      final var rlpOutput = new BytesValueRLPOutput();
      getTransaction().writeTo(rlpOutput);
      return rlpOutput.encoded().toBase64String();
    }
  }
}
