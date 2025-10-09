This directory contains tests of the `io.spine.testing.client.grpc.TestClient`, which belongs to
the `testutil-client` module.

In order to test the gRPC client, we need a backend with the gRPC server backed by a Bounded 
Context with entities handling commands and queries. This in turn requires dependency on the
`server` module, which — obviously — is not available in the `testutil-client` to which the
`TestClient` belongs.

That's why these tests belong to this module, rather to `testutil-client`.
  
