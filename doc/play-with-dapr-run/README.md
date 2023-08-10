# Play with dapr run

You can start your application with `dapr run` for a standalone setup of Dapr.

What it essentially does is
1. Start a daprd process (your application sidecar)
2. Start your application with some extra environment variables (telling your
   application how to interact with the sidecar)

Here we write a simple script that does two things
- `bb spit-envs`: spit environment variables out/envs-without-dapr.txt
- `bb diff-envs-and-await`: get environment variables, print the added
  environment variables and wait for user interrupption

So you can play with the `dapr run` like:

```sh
# spit environment variables without dapr run
bb spit-envs

# Run the following
dapr run --app-id app42 bb diff-envs-and-wait

# now you can find the extra environments within the log
== APP == DAPR_METRICS_PORT=51753
== APP == DAPR_GRPC_PORT=51752
== APP == APP_ID=app42
== APP == DAPR_HTTP_PORT=51751
== APP == _=/usr/local/bin/dapr

# and now you can interact with the sidecar using dapr APIs (the http or grpc).
```
