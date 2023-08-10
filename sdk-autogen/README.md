# Dapr sdk-autogen

The dapr client proto files are downloaded from
[https://github.com/dapr/dapr/tree/release-1.11/dapr/proto](https://github.com/dapr/dapr/tree/release-1.11/dapr/proto).

1. [Download Protocol Buffers][1] and put into your `PATH`.
2. [Download protoc-gen-grpc-java][2] plugin and put it into
   `protoc-plugins/protoc-gen-grpc-java`.
3. Generate code with:
   ```sh
   clojure -T:build prep-lib
   ```

And for any `tools.build` project which use this as dependency, [prepare][3]
with command:

```sh
clojure -X:deps prep
```

[1]: https://github.com/protocolbuffers/protobuf/releases
[2]: https://github.com/grpc/grpc-java/tree/master/compiler
[3]: https://clojure.org/guides/deps_and_cli#prep_libs
