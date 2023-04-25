/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.utilops.inventory;

import static com.hedera.services.bdd.spec.utilops.inventory.NewSpecKey.exportWithPass;
import static com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromMnemonic.createAndLinkFromMnemonic;
import static com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromMnemonic.createAndLinkSimpleKey;
import static com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromPem.incorporatePem;
import static com.hedera.services.yahcli.config.ConfigManager.isValid;
import static com.hedera.services.yahcli.config.ConfigUtils.*;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.swirlds.common.utility.CommonUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class SpecKeyFromFile extends UtilOp {
    private static final Logger log = LogManager.getLogger(SpecKeyFromFile.class);

    private final String name;
    private final String loc;
    private Optional<String> linkedId = Optional.empty();
    private Optional<String> immediateExportLoc = Optional.empty();
    private Optional<String> immediateExportPass = Optional.empty();

    public SpecKeyFromFile exportingTo(String loc, String pass) {
        immediateExportLoc = Optional.of(loc);
        immediateExportPass = Optional.of(pass);
        return this;
    }

    public SpecKeyFromFile yahcliLogged() {
        verboseLoggingOn = true;
        yahcliLogger = true;
        return this;
    }

    public SpecKeyFromFile(String name, String loc) {
        this.loc = loc;
        this.name = name;
    }

    public SpecKeyFromFile linkedTo(String id) {
        linkedId = Optional.of(id);
        return this;
    }

    @Override
    @SuppressWarnings({"java:S5960", "java:S3776"})
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        final var flexLoc = loc.substring(0, loc.lastIndexOf('.'));
        final var keyFile = keyFileAt(flexLoc);
        if (!keyFile.isPresent()) {
            throw new IllegalArgumentException("No key can be sourced from '" + loc + "'");
        }
        final var f = keyFile.orElseThrow();
        Optional<String> finalPassphrase = Optional.empty();
        if (f.getName().endsWith(".pem")) {
            var optPassFile = passFileFor(f);
            if (optPassFile.isPresent()) {
                final var pf = optPassFile.get();
                try {
                    finalPassphrase = Optional.of(Files.readString(pf.toPath()).trim());
                } catch (IOException e) {
                    log.warn(
                            "Password file {} inaccessible for PEM {}",
                            pf.getAbsolutePath(),
                            name,
                            e);
                }
            }
            if (!isValid(f, finalPassphrase)) {
                var prompt = "Please enter the passphrase for key file " + f.getName();
                finalPassphrase = promptForPassphrase(loc, prompt, 3);
            }
            if (finalPassphrase.isEmpty() || !isValid(f, finalPassphrase)) {
                Assertions.fail(
                        String.format("No valid passphrase could be obtained for PEM %s", loc));
            }
            incorporatePem(
                    spec,
                    SigControl.ON,
                    keyFile.get().getAbsolutePath(),
                    finalPassphrase.orElseThrow(),
                    name,
                    linkedId,
                    Optional.empty());
        } else if (f.getName().endsWith(".words")) {
            final var mnemonic = KeyFactory.mnemonicFromFile(f.getAbsolutePath());
            createAndLinkFromMnemonic(spec, mnemonic, name, linkedId, null);
        } else {
            var hexed = Files.readString(f.toPath()).trim();
            final var privateKey = CommonUtils.unhex(hexed);
            createAndLinkSimpleKey(spec, privateKey, name, linkedId, null);
        }
        if (immediateExportLoc.isPresent() && immediateExportPass.isPresent()) {
            final var exportLoc = immediateExportLoc.get();
            final var exportPass = finalPassphrase.orElse(immediateExportPass.get());
            exportWithPass(spec, name, exportLoc, exportPass);
            if (verboseLoggingOn && yahcliLogger) {
                COMMON_MESSAGES.info("Exported key from " + flexLoc + " to " + exportLoc);
            }
        }
        return false;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        var helper = super.toStringHelper();
        helper.add("name", name);
        helper.add("loc", loc);
        linkedId.ifPresent(s -> helper.add("linkedId", s));
        return helper;
    }
}
