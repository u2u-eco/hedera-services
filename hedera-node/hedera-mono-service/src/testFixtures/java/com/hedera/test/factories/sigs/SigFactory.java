/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.test.factories.sigs;

import static com.hedera.node.app.hapi.utils.CommonUtils.extractTransactionBodyBytes;
import static com.hedera.node.app.hapi.utils.SignatureGenerator.signBytes;
import static com.hedera.node.app.service.mono.sigs.utils.MiscCryptoUtils.keccak256DigestOf;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.SignatureGenerator;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.keys.KeyTreeLeaf;
import com.hedera.test.factories.keys.KeyTreeListNode;
import com.hedera.test.factories.keys.KeyTreeNode;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.crypto.SignatureType;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class SigFactory {
    private final SigMapGenerator sigMapGen;
    public static final byte[] NONSENSE_RSA_SIG = "MOME".getBytes();

    public SigFactory() {
        this(SigMapGenerator.withUniquePrefixes());
    }

    public SigFactory(final SigMapGenerator sigMapGen) {
        this.sigMapGen = sigMapGen;
    }

    public static byte[] signUnchecked(final byte[] data, final PrivateKey pk) {
        try {
            return signBytes(data, pk);
        } catch (final SignatureException | NoSuchAlgorithmException | InvalidKeyException fatal) {
            throw new IllegalStateException(fatal);
        }
    }

    public Transaction signWithSigMap(
            final Transaction.Builder txn, final List<KeyTree> signers, final KeyFactory factory)
            throws Throwable {
        final SimpleSigning signing =
                new SimpleSigning(extractTransactionBodyBytes(txn), signers, factory);
        final List<Map.Entry<byte[], byte[]>> sigs = signing.completed();
        txn.setSigMap(sigMapGen.generate(sigs, signing.sigTypes()));
        return txn.build();
    }

    public static class SimpleSigning {
        private final byte[] data;
        private final KeyFactory factory;
        private final List<KeyTree> signers;
        private final Set<String> used = new HashSet<>();
        private final List<SignatureType> sigTypes = new ArrayList<>();
        private final List<Map.Entry<byte[], byte[]>> keySigs = new ArrayList<>();

        public SimpleSigning(
                final byte[] data, final List<KeyTree> signers, final KeyFactory factory) {
            this.data = data;
            this.signers = signers;
            this.factory = factory;
        }

        public Supplier<SignatureType> sigTypes() {
            return new Supplier<>() {
                private int i = 0;

                @Override
                public SignatureType get() {
                    return sigTypes.get(i++);
                }
            };
        }

        public List<Map.Entry<byte[], byte[]>> completed() throws Throwable {
            for (final KeyTree signer : signers) {
                signRecursively(signer.getRoot());
            }
            return keySigs;
        }

        private void signRecursively(final KeyTreeNode node) throws Throwable {
            if (node instanceof KeyTreeLeaf) {
                if (((KeyTreeLeaf) node).isUsedToSign()) {
                    signIfNecessary(node.asKey(factory));
                }
            } else if (node instanceof KeyTreeListNode) {
                for (final KeyTreeNode child : ((KeyTreeListNode) node).getChildren()) {
                    signRecursively(child);
                }
            }
        }

        private void signIfNecessary(final Key key) throws Throwable {
            final String pubKeyHex = KeyFactory.asPubKeyHex(key);
            if (!used.contains(pubKeyHex)) {
                signFor(pubKeyHex, key);
                used.add(pubKeyHex);
            }
        }

        private void signFor(final String pubKeyHex, final Key key) throws Throwable {
            final SignatureType sigType = sigTypeOf(key);
            if (sigType == SignatureType.ED25519) {
                final PrivateKey signer = factory.lookupPrivateKey(pubKeyHex);
                final byte[] sig = SignatureGenerator.signBytes(data, signer);
                keySigs.add(new AbstractMap.SimpleEntry<>(key.getEd25519().toByteArray(), sig));
            } else if (sigType == SignatureType.ECDSA_SECP256K1) {
                final PrivateKey signer = factory.lookupPrivateKey(pubKeyHex);
                final byte[] sig = SignatureGenerator.signBytes(keccak256DigestOf(data), signer);
                keySigs.add(
                        new AbstractMap.SimpleEntry<>(key.getECDSASecp256K1().toByteArray(), sig));
            } else if (sigType == SignatureType.RSA) {
                keySigs.add(
                        new AbstractMap.SimpleEntry<>(
                                key.getRSA3072().toByteArray(), NONSENSE_RSA_SIG));
            }
            sigTypes.add(sigType);
        }

        private SignatureType sigTypeOf(final Key key) {
            if (key.getEd25519() != ByteString.EMPTY) {
                return SignatureType.ED25519;
            } else if (key.getECDSASecp256K1() != ByteString.EMPTY) {
                return SignatureType.ECDSA_SECP256K1;
            } else if (key.getRSA3072() != ByteString.EMPTY) {
                return SignatureType.RSA;
            }
            throw new AssertionError("Simple gRPC key has no public key!");
        }
    }
}
