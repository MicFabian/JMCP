package com.example.javamcp.grpc;

import com.example.javamcp.grpc.generated.McpServiceGrpc;
import com.example.javamcp.grpc.generated.McpManifestReply;
import com.example.javamcp.grpc.generated.McpManifestRequest;
import com.example.javamcp.grpc.generated.SearchReply;
import com.example.javamcp.grpc.generated.SearchRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

public final class McpGrpcClientExample {

    private McpGrpcClientExample() {
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9090;
        String command = "search";
        String query = "constructor injection";
        if (args.length > 2) {
            if ("search".equalsIgnoreCase(args[2]) || "manifest".equalsIgnoreCase(args[2])) {
                command = args[2];
                if (args.length > 3) {
                    query = args[3];
                }
            } else {
                query = args[2];
            }
        }

        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        try {
            McpServiceGrpc.McpServiceBlockingStub stub = McpServiceGrpc.newBlockingStub(channel);
            if ("manifest".equalsIgnoreCase(command)) {
                printManifest(stub.getMcpManifest(McpManifestRequest.newBuilder().build()));
            } else {
                SearchReply reply = stub.search(SearchRequest.newBuilder()
                        .setQuery(query)
                        .setLimit(5)
                        .setMode("HYBRID")
                        .setDiagnostics(true)
                        .build());

                System.out.println("Query: " + reply.getQuery());
                System.out.println("Count: " + reply.getCount());
                reply.getResultsList().forEach(result ->
                        System.out.println("- " + result.getTitle() + " (" + result.getSource() + ")"));
            }
        } finally {
            channel.shutdown();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void printManifest(McpManifestReply reply) {
        System.out.println("Server: " + reply.getServerName());
        System.out.println("Version: " + reply.getVersion());
        System.out.println("Tools: " + reply.getToolsCount());
        reply.getToolsList().forEach(tool ->
                System.out.println("- " + tool.getName() + ": " + tool.getDescription()));
    }
}
