### Workflow Onset

At the beginning of each workflow, a `Transaction` has to be parsed and validated.
As these steps are the same for all workflows, this functionality has been extracted and made available independently.
All related classes can be found in the package `com.hedera.node.app.workflows.onset`.
Details about the required pre-checks can be found [here](transaction-prechecks.md).

### Ingest Workflow

The package `com.hedera.node.app.workflows.ingest` contains the ingest workflow. A rough overview can be seen in the diagram below.

![Diagram of ingest workflow](images/Ingest%20Workflow.png)

When a new message arrives at the HAPI-endpoint, the byte-buffer that contains the transaction is sent to the ingest workflow.
The gRPC-server is responsible for Thread-Management.
The ingest-workflow is single-threaded, but multiple calls can run in parallel.

The ingest workflow consists of the following steps:

1. **Parse transaction.** The transaction arrives as a byte-array. The required parts are parsed and the structure and syntax are validated.
2. **Check throttles.** Throttling must be observed.
3. **Check semantics.** The semantics of the transaction are validated. This check is specific to each type of transaction.
4. **Get payer's account.** The account data of the payer is read from the latest immutable state.
5. **Check payer's signature.** The signature of the payer is checked. (Please note: other signatures are not checked here, but in later stages)
6. **Check account balance.** The account of the payer is checked to ensure it is able to pay the fee.
7. **Submit to platform.** The transaction is submitted to the platform for further processing.
8. **TransactionResponse.** Return `TransactionResponse`  with result-code.

If all checks have been successful, the transaction has been submitted to the platform and the precheck-code of the returned `TransactionResponse` is `OK`.
Otherwise the transaction is rejected with an appropriate response code.
In case of insufficient funds, the returned `TransactionResponse` also contains an estimation of the required fee.

### Pre-Handle Workflow

The `prehandle` package contains the workflow for pre-handling transactions. A rough overview can be seen in the diagram below.

![Diagram of pre-handle transaction workflow](images/Pre-Handle%20Transaction%20Workflow.png)

An `Event` at a time is sent to the `prehandle` with a reference to the latest immutable state. It iterates through each transaction and initiates the pre-handle workflow in a separate thread. The workflow consists of the following steps:

1. **Parse Transaction.** The transaction arrives as a byte-array. The required parts are parsed and the common information is validated.
2. **Call PreTransactionHandler.** Depending on the type of transaction, a specific `PreTransactionHandler` is called. It validates the transaction-specific parts and pre-loads data into the cache. It also creates a `TransactionMetadata` and sets the required keys.
3. **Prepare Signature-Data.** The data for all signatures is loaded into memory. A signature consists of three parts:
   1. Some bytes that are signed; in our case, either the `bodyBytes` for an Ed25519 signature or the Keccak256 hash of the `bodyBytes` for an ECDSA(secp256k1) signature.
   2. An Ed25519 or secp256k1 public key that is supposed to have signed these bytes (these public keys come from e.g. the Hedera key of some `0.0.X` account).
   3. The signature itself---which comes from the `SignatureMap`, based on existence of a unique `SignaturePair` entry whose `pubKeyPrefix` matches the public key in (ii.).
4. **Verify Signatures.** The information prepared in the previous step is sent to the platform to validate the signatures.
5. **Transaction Metadata.** The `TransactionMetadata` generated by the `PreTransactionHandler` is attached to the `SwirldsTransaction`.

If all checks have been successful, the status of the created `TransactionMetadata` will be `OK`. Otherwise, the status is set to the response code providing the failure reason. If the workflow terminates early (either because the parsing step (1.) fails or an unexpected `Exception` occurs) an `ErrorTransactionMetadata` is attached to the `SwirldsTransaction` that contains the causing `Exception`.
