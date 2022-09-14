# Dapr HTTP Client

```sh
# spit environment variables to out/envs-without-dapr.txt
bb spit-envs

# we've setup some components for testing at ./doc/http-client-components
# the localfile-secret-store expects the following file to exist
echo '{"secret0": "helloworld", "secret1": "whatever"}' > /tmp/secrets.json

# start a dapr sidecar, notice that `bb diff-envs-and-wait` shows what's added
# to the environment variables (it contains the some port you need to interact
# with the sidecar).
dapr run --app-id app0 --components-path ./doc/http-client-components \
     --dapr-http-port 3500 \
     --app-port 9393 \
     bb diff-envs-and-wait

# since we've given a --app-port when starting the sidecar, the sidecar will
# wait for us to start a server on the port.
#
# we've got a sample app at org.lotuc.dapr.http-sample-app

# and now you can test Dapr APIs at org.lotuc.dapr.internal.http-client-v1
```
